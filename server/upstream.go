package main

// Upstream clients and normalisers. The app only ever sees the shapes defined here, so
// the upstreams (DB, MOTIS/Transitous) can be swapped without an app update.

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

var railModes = map[string]bool{
	"HIGHSPEED_RAIL": true, "LONG_DISTANCE": true, "NIGHT_RAIL": true, "REGIONAL_FAST_RAIL": true,
	"REGIONAL_RAIL": true, "RAIL": true, "SUBURBAN": true,
}

var dbModes = map[string]string{
	"HIGH_SPEED_TRAIN": "HIGHSPEED_RAIL", "INTERCITY_TRAIN": "LONG_DISTANCE", "INTER_REGIONAL_TRAIN": "REGIONAL_FAST_RAIL",
	"REGIONAL_TRAIN": "REGIONAL_RAIL", "CITY_TRAIN": "SUBURBAN", "SUBWAY": "SUBWAY", "TRAM": "TRAM", "BUS": "BUS",
	"FERRY": "FERRY", "FLIGHT": "AIRPLANE", "CAR_TRAIN": "LONG_DISTANCE", "SHUTTLE": "BUS",
}

var (
	reDelfi = regexp.MustCompile(`^de-DELFI_(de:\d+:\d+)`)
	reEvaID = regexp.MustCompile(`_0*(80\d{5})$`)
)

// StationKey maps a MOTIS stop id to a key of the station index:
// "de-DELFI_de:08222:2417:5:5" → "de:08222:2417" (DHID), "de-VBN_000008000128" → "8000128" (EVA).
func StationKey(stopID string) string {
	if m := reDelfi.FindStringSubmatch(stopID); m != nil {
		return m[1]
	}
	return first(reEvaID, stopID)
}

// ---------- API shapes ----------

type Row struct {
	Source            string   `json:"source"` // db | motis
	ID                string   `json:"id"`
	TripID            string   `json:"tripId,omitempty"`
	Time              string   `json:"time"`
	ScheduledTime     string   `json:"scheduledTime"`
	Delay             *int     `json:"delay"` // minutes, null = no realtime data
	Realtime          bool     `json:"realtime"`
	Cancelled         bool     `json:"cancelled"`
	Platform          string   `json:"platform,omitempty"`
	ScheduledPlatform string   `json:"scheduledPlatform,omitempty"`
	Line              string   `json:"line"`
	Number            string   `json:"number,omitempty"`
	Mode              string   `json:"mode"`
	Color             string   `json:"color,omitempty"`
	TextColor         string   `json:"textColor,omitempty"`
	Headsign          string   `json:"headsign,omitempty"`
	Via               []string `json:"via"`
	Operator          string   `json:"operator,omitempty"`
	Notes             []string `json:"notes"`
	Attributes        []string `json:"attributes"`
}

type Place struct {
	Name               string  `json:"name"`
	StopID             string  `json:"stopId,omitempty"`
	Lat                float64 `json:"lat"`
	Lon                float64 `json:"lon"`
	Arrival            string  `json:"arrival,omitempty"`
	ScheduledArrival   string  `json:"scheduledArrival,omitempty"`
	Departure          string  `json:"departure,omitempty"`
	ScheduledDeparture string  `json:"scheduledDeparture,omitempty"`
	Platform           string  `json:"platform,omitempty"`
	ScheduledPlatform  string  `json:"scheduledPlatform,omitempty"`
	Cancelled          bool    `json:"cancelled"`
}

type Leg struct {
	Mode      string   `json:"mode"`
	From      Place    `json:"from"`
	To        Place    `json:"to"`
	Duration  int      `json:"duration"`
	Distance  *float64 `json:"distance"`
	Realtime  bool     `json:"realtime"`
	Cancelled bool     `json:"cancelled"`
	Line      string   `json:"line,omitempty"`
	Number    string   `json:"number,omitempty"`
	Headsign  string   `json:"headsign,omitempty"`
	Color     string   `json:"color,omitempty"`
	TextColor string   `json:"textColor,omitempty"`
	Operator  string   `json:"operator,omitempty"`
	TripID    string   `json:"tripId,omitempty"`
	Stops     []Place  `json:"stops"`
	Geometry  *struct {
		Points    string `json:"points"`
		Precision int    `json:"precision"`
	} `json:"geometry"`
	Alerts []Alert `json:"alerts"`
}

type Alert struct {
	Header string `json:"header,omitempty"`
	Text   string `json:"text,omitempty"`
}

type Itinerary struct {
	Start     string `json:"start"`
	End       string `json:"end"`
	Duration  int    `json:"duration"`
	Transfers int    `json:"transfers"`
	Legs      []Leg  `json:"legs"`
}

type Plan struct {
	Itineraries []Itinerary `json:"itineraries"`
	Direct      []Itinerary `json:"direct"` // walk/bike only, when close enough
	Earlier     string      `json:"earlier,omitempty"`
	Later       string      `json:"later,omitempty"`
}

type Location struct {
	ID       string   `json:"id"`   // stop id, or "lat,lon" for addresses/places
	Type     string   `json:"type"` // STOP | ADDRESS | PLACE
	Name     string   `json:"name"`
	Area     string   `json:"area,omitempty"`
	Lat      float64  `json:"lat"`
	Lon      float64  `json:"lon"`
	Modes    []string `json:"modes"`
	Distance *int     `json:"distance,omitempty"`
}

// ---------- DB API Marketplace ----------

var errQuota = errors.New("quota exhausted")

type DBAPI struct {
	ClientID, APIKey string
	Reserve          int
	mu               sync.Mutex
	blockedUntil     map[string]time.Time
	remaining        map[string]int
}

var reRemaining = regexp.MustCompile(`limitPerDay,(\d+)`)

func (d *DBAPI) Enabled() bool { return d.ClientID != "" && d.APIKey != "" }

func (d *DBAPI) Remaining() map[string]int {
	d.mu.Lock()
	defer d.mu.Unlock()
	out := map[string]int{}
	for k, v := range d.remaining {
		out[k] = v
	}
	return out
}

func (d *DBAPI) call(ctx context.Context, product, path string, body any, out any) error {
	if !d.Enabled() {
		return errors.New("DB API not configured")
	}
	d.mu.Lock()
	blocked := time.Now().Before(d.blockedUntil[product])
	d.mu.Unlock()
	if blocked {
		return errQuota
	}
	method, var_ := "GET", []byte(nil)
	if body != nil {
		method = "POST"
		var_, _ = json.Marshal(body)
	}
	req, _ := http.NewRequestWithContext(ctx, method, "https://apis.deutschebahn.com/db-api-marketplace/apis/"+product+path, bytes.NewReader(var_))
	req.Header.Set("DB-Client-ID", d.ClientID)
	req.Header.Set("DB-Api-Key", d.APIKey)
	req.Header.Set("Accept", "application/json")
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	res, err := httpClient.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	d.mu.Lock()
	if m := reRemaining.FindStringSubmatch(res.Header.Get("x-ratelimit-remaining")); m != nil {
		left, _ := strconv.Atoi(m[1])
		d.remaining[product] = left
		if left < d.Reserve { // keep a reserve, back off for an hour, then probe again
			d.blockedUntil[product] = time.Now().Add(time.Hour)
		}
	}
	if res.StatusCode == 429 {
		d.blockedUntil[product] = time.Now().Add(time.Hour)
	}
	d.mu.Unlock()
	if res.StatusCode != 200 {
		return fmt.Errorf("%s%s: HTTP %d", product, path, res.StatusCode)
	}
	return json.NewDecoder(res.Body).Decode(out)
}

type dbLocalized struct {
	Language string `json:"language"`
	Name     string `json:"name"`
	Text     string `json:"text"`
}

func pickLang(list []dbLocalized) string {
	for _, l := range list {
		if l.Language == "deu" {
			return l.Text + l.Name
		}
	}
	if len(list) > 0 {
		return list[0].Text + list[0].Name
	}
	return ""
}

type dbEva struct {
	EvaNumber string `json:"evaNumber"`
}

type dbEntry struct {
	JourneyID        string                 `json:"journeyId"`
	Time             string                 `json:"time"`
	TimeSchedule     string                 `json:"timeSchedule"`
	TimeType         string                 `json:"timeType"`
	Platform         *struct{ Name string } `json:"platform"`
	PlatformSchedule *struct{ Name string } `json:"platformSchedule"`
	Messages         []int                  `json:"messages"`
	Transport        struct {
		Type               string                 `json:"type"`
		JourneyDescription string                 `json:"journeyDescription"`
		Category           string                 `json:"category"`
		JourneyNumber      int                    `json:"journeyNumber"`
		Line               *struct{ Name string } `json:"line"`
		Direction          *struct {
			StopPlaces []dbEva `json:"stopPlaces"`
		} `json:"direction"`
		Via []struct {
			DisplayPriority int    `json:"displayPriority"`
			EvaNumber       string `json:"evaNumber"`
		} `json:"via"`
	} `json:"transport"`
	JourneyInfo struct {
		Origin      *dbEva `json:"origin"`
		Destination *dbEva `json:"destination"`
	} `json:"journeyInfo"`
}

type DBBoard struct {
	StopPlaces []struct {
		EvaNumber      string        `json:"evaNumber"`
		LocalizedNames []dbLocalized `json:"localizedNames"`
	} `json:"stopPlaces"`
	Messages   map[string]json.RawMessage `json:"messages"`
	Departures []json.RawMessage          `json:"departures"`
	Arrivals   []json.RawMessage          `json:"arrivals"`
}

func (d *DBAPI) Board(ctx context.Context, dhid string, arrivals bool, from, to time.Time) (*DBBoard, error) {
	path := "/departures"
	if arrivals {
		path = "/arrivals"
	}
	var out DBBoard
	err := d.call(ctx, "open-boards/v1", path, map[string]any{
		"stopPlaceOrStation":         map[string]any{"dhids": []string{dhid}},
		"timeWindow":                 map[string]string{"timeStart": from.Format(time.RFC3339), "timeEnd": to.Format(time.RFC3339)},
		"maxViaStops":                4,
		"maxEntriesPerTransportType": 50, // API maximum
		"languages":                  []string{"deu", "eng"},
	}, &out)
	return &out, err
}

type FastaStation struct {
	Facilities []struct {
		EquipmentNumber  int      `json:"equipmentnumber"`
		Type             string   `json:"type"`
		Description      string   `json:"description"`
		GeocoordX        *float64 `json:"geocoordX"`
		GeocoordY        *float64 `json:"geocoordY"`
		State            string   `json:"state"`
		StateExplanation string   `json:"stateExplanation"`
	} `json:"facilities"`
}

func (d *DBAPI) Facilities(ctx context.Context, stada string) (*FastaStation, error) {
	var out FastaStation
	err := d.call(ctx, "fasta/v2", "/stations/"+url.PathEscape(stada), nil, &out)
	return &out, err
}

func minutesBetween(a, b string) *int {
	ta, err1 := time.Parse(time.RFC3339, a)
	tb, err2 := time.Parse(time.RFC3339, b)
	if err1 != nil || err2 != nil {
		return nil
	}
	m := int(ta.Sub(tb).Round(time.Minute).Minutes())
	return &m
}

// NormalizeDBBoard turns an OpenBoards response into board rows.
func NormalizeDBBoard(raw *DBBoard, arrivals bool) []Row {
	names := map[string]string{}
	for _, s := range raw.StopPlaces {
		names[s.EvaNumber] = pickLang(s.LocalizedNames)
	}
	type msg struct{ kind, text string }
	msgs := map[int]msg{}
	for kind, rawList := range raw.Messages {
		var list []struct {
			MessageID    int           `json:"messageID"`
			Descriptions []dbLocalized `json:"descriptions"`
		}
		if json.Unmarshal(rawList, &list) != nil {
			continue
		}
		for _, m := range list {
			msgs[m.MessageID] = msg{kind, pickLang(m.Descriptions)}
		}
	}
	entries := raw.Departures
	if arrivals {
		entries = raw.Arrivals
	}
	rows := make([]Row, 0, len(entries))
	for _, rawEntry := range entries {
		var d dbEntry
		if json.Unmarshal(rawEntry, &d) != nil {
			continue
		}
		t := d.Transport
		r := Row{
			Source: "db", ID: d.JourneyID, Time: d.Time, ScheduledTime: d.TimeSchedule,
			Realtime: d.TimeType != "SCHEDULE", Number: t.JourneyDescription, Mode: dbModes[t.Type],
			Via: []string{}, Notes: []string{}, Attributes: []string{},
			Cancelled: hasCancelFlag(rawEntry),
		}
		if r.Mode == "" {
			r.Mode = "RAIL"
		}
		if r.Realtime {
			r.Delay = minutesBetween(d.Time, d.TimeSchedule)
		}
		if d.Platform != nil {
			r.Platform = d.Platform.Name
		}
		if d.PlatformSchedule != nil {
			r.ScheduledPlatform = d.PlatformSchedule.Name
		}
		switch {
		case t.Line != nil && t.Line.Name != "":
			r.Line = t.Line.Name
		case t.JourneyDescription != "":
			r.Line = t.JourneyDescription
		default:
			r.Line = strings.TrimSpace(fmt.Sprintf("%s %d", t.Category, t.JourneyNumber))
		}
		var end *dbEva
		if arrivals {
			end = d.JourneyInfo.Origin
		} else if t.Direction != nil && len(t.Direction.StopPlaces) > 0 {
			end = &t.Direction.StopPlaces[0]
		} else {
			end = d.JourneyInfo.Destination
		}
		if end != nil {
			r.Headsign = names[end.EvaNumber]
		}
		sort.SliceStable(t.Via, func(i, j int) bool { return t.Via[i].DisplayPriority < t.Via[j].DisplayPriority })
		for _, v := range t.Via {
			if n := names[v.EvaNumber]; n != "" {
				r.Via = append(r.Via, n)
			}
		}
		for _, id := range d.Messages {
			m, ok := msgs[id]
			switch {
			case !ok || m.text == "":
			case m.kind == "attributes":
				r.Attributes = append(r.Attributes, m.text)
			default:
				r.Notes = append(r.Notes, m.text)
			}
		}
		rows = append(rows, r)
	}
	return rows
}

// shortcut: the cancellation field name isn't in the public docs; any top-level *cancel* key set to true counts. Verify against the OpenAPI spec.
func hasCancelFlag(raw json.RawMessage) bool {
	var m map[string]any
	if json.Unmarshal(raw, &m) != nil {
		return false
	}
	for k, v := range m {
		if strings.Contains(strings.ToLower(k), "cancel") && v == true {
			return true
		}
	}
	return false
}

// LinkTrips attaches MOTIS trip ids to DB rows so a tap on a DB train can still open its full route.
func LinkTrips(dbRows, motisRows []Row) {
	norm := func(s string) string { return strings.ToUpper(strings.ReplaceAll(s, " ", "")) }
	minute := func(s string) int64 {
		t, _ := time.Parse(time.RFC3339, s)
		return t.Unix() / 60
	}
	byLine, byNumber := map[string]Row{}, map[string]Row{}
	for _, r := range motisRows {
		byLine[fmt.Sprint(minute(r.ScheduledTime), "|", norm(r.Line))] = r
		if n, err := strconv.Atoi(strings.TrimLeft(r.Number, "0")); err == nil {
			byNumber[fmt.Sprint(minute(r.ScheduledTime), "|", n)] = r
		}
	}
	reNum := regexp.MustCompile(`\d+`)
	for i := range dbRows {
		r := &dbRows[i]
		m, ok := byLine[fmt.Sprint(minute(r.ScheduledTime), "|", norm(r.Line))]
		if !ok {
			if n, err := strconv.Atoi(reNum.FindString(r.Number)); err == nil {
				m, ok = byNumber[fmt.Sprint(minute(r.ScheduledTime), "|", n)]
			}
		}
		if ok {
			r.TripID = m.TripID
			r.Color, r.TextColor, r.Operator = m.Color, m.TextColor, m.Operator
		}
	}
}

// ---------- MOTIS (Transitous by default) ----------

type Motis struct {
	Base, UserAgent string
}

type motisError struct{ status int }

func (e motisError) Error() string { return fmt.Sprintf("motis: HTTP %d", e.status) }

func (m *Motis) get(ctx context.Context, path string, params url.Values, out any) error {
	for k, v := range params {
		if len(v) == 0 || v[0] == "" {
			params.Del(k)
		}
	}
	req, _ := http.NewRequestWithContext(ctx, "GET", m.Base+path+"?"+params.Encode(), nil)
	req.Header.Set("User-Agent", m.UserAgent)
	req.Header.Set("Accept", "application/json")
	res, err := httpClient.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode != 200 {
		return motisError{res.StatusCode}
	}
	return json.NewDecoder(res.Body).Decode(out)
}

type motisPlace struct {
	Name               string   `json:"name"`
	StopID             string   `json:"stopId"`
	Lat                float64  `json:"lat"`
	Lon                float64  `json:"lon"`
	Arrival            string   `json:"arrival"`
	ScheduledArrival   string   `json:"scheduledArrival"`
	Departure          string   `json:"departure"`
	ScheduledDeparture string   `json:"scheduledDeparture"`
	Track              string   `json:"track"`
	ScheduledTrack     string   `json:"scheduledTrack"`
	Cancelled          bool     `json:"cancelled"`
	Modes              []string `json:"modes"`
}

func (p motisPlace) place() Place {
	sp := p.ScheduledTrack
	if sp == "" {
		sp = p.Track
	}
	return Place{p.Name, p.StopID, p.Lat, p.Lon, p.Arrival, p.ScheduledArrival, p.Departure, p.ScheduledDeparture, p.Track, sp, p.Cancelled}
}

type motisRoute struct {
	Mode           string `json:"mode"`
	RealTime       bool   `json:"realTime"`
	Headsign       string `json:"headsign"`
	AgencyName     string `json:"agencyName"`
	RouteColor     string `json:"routeColor"`
	RouteTextColor string `json:"routeTextColor"`
	TripID         string `json:"tripId"`
	RouteShortName string `json:"routeShortName"`
	TripShortName  string `json:"tripShortName"`
	DisplayName    string `json:"displayName"`
	Cancelled      bool   `json:"cancelled"`
}

func (r motisRoute) line() string {
	for _, s := range []string{r.DisplayName, r.RouteShortName, r.TripShortName} {
		if s != "" {
			return s
		}
	}
	return ""
}

type motisStopTimes struct {
	StopTimes []struct {
		motisRoute
		Place         motisPlace  `json:"place"`
		TripFrom      *motisPlace `json:"tripFrom"`
		TripTo        *motisPlace `json:"tripTo"`
		TripCancelled bool        `json:"tripCancelled"`
	} `json:"stopTimes"`
}

func NormalizeStopTimes(raw *motisStopTimes, arrivals bool) []Row {
	rows := make([]Row, 0, len(raw.StopTimes))
	for _, s := range raw.StopTimes {
		p := s.Place
		t, sched := p.Departure, p.ScheduledDeparture
		if arrivals {
			t, sched = p.Arrival, p.ScheduledArrival
		}
		r := Row{
			Source: "motis", ID: s.TripID, TripID: s.TripID, Time: t, ScheduledTime: sched,
			Realtime: s.RealTime, Cancelled: s.Cancelled || s.TripCancelled || p.Cancelled,
			Platform: p.Track, ScheduledPlatform: p.place().ScheduledPlatform,
			Line: s.line(), Number: s.TripShortName, Mode: s.Mode, Color: s.RouteColor, TextColor: s.RouteTextColor,
			Headsign: s.Headsign, Operator: s.AgencyName, Via: []string{}, Notes: []string{}, Attributes: []string{},
		}
		if s.RealTime {
			r.Delay = minutesBetween(t, sched)
		}
		switch {
		case arrivals && s.TripFrom != nil:
			r.Headsign = s.TripFrom.Name
		case r.Headsign == "" && s.TripTo != nil:
			r.Headsign = s.TripTo.Name
		}
		rows = append(rows, r)
	}
	return rows
}

type motisLeg struct {
	motisRoute
	From              motisPlace   `json:"from"`
	To                motisPlace   `json:"to"`
	Duration          int          `json:"duration"`
	Distance          *float64     `json:"distance"`
	IntermediateStops []motisPlace `json:"intermediateStops"`
	LegGeometry       *struct {
		Points    string `json:"points"`
		Precision int    `json:"precision"`
	} `json:"legGeometry"`
	Alerts []struct {
		HeaderText      string `json:"headerText"`
		DescriptionText string `json:"descriptionText"`
	} `json:"alerts"`
}

func (l motisLeg) leg() Leg {
	out := Leg{
		Mode: l.Mode, From: l.From.place(), To: l.To.place(), Duration: l.Duration, Distance: l.Distance,
		Realtime: l.RealTime, Cancelled: l.Cancelled, Line: l.line(), Number: l.TripShortName, Headsign: l.Headsign,
		Color: l.RouteColor, TextColor: l.RouteTextColor, Operator: l.AgencyName, TripID: l.TripID,
		Stops: []Place{}, Alerts: []Alert{},
	}
	if l.Mode == "WALK" || l.Mode == "BIKE" {
		out.Line = ""
	}
	for _, s := range l.IntermediateStops {
		out.Stops = append(out.Stops, s.place())
	}
	if g := l.LegGeometry; g != nil {
		if g.Precision == 0 {
			g.Precision = 6
		}
		out.Geometry = &struct {
			Points    string `json:"points"`
			Precision int    `json:"precision"`
		}{g.Points, g.Precision}
	}
	for _, a := range l.Alerts {
		out.Alerts = append(out.Alerts, Alert{a.HeaderText, a.DescriptionText})
	}
	return out
}

type motisItinerary struct {
	StartTime string     `json:"startTime"`
	EndTime   string     `json:"endTime"`
	Duration  int        `json:"duration"`
	Transfers int        `json:"transfers"`
	Legs      []motisLeg `json:"legs"`
}

func (it motisItinerary) itinerary() Itinerary {
	out := Itinerary{Start: it.StartTime, End: it.EndTime, Duration: it.Duration, Transfers: it.Transfers, Legs: []Leg{}}
	for _, l := range it.Legs {
		out.Legs = append(out.Legs, l.leg())
	}
	return out
}

type motisPlan struct {
	Itineraries        []motisItinerary `json:"itineraries"`
	Direct             []motisItinerary `json:"direct"`
	PreviousPageCursor string           `json:"previousPageCursor"`
	NextPageCursor     string           `json:"nextPageCursor"`
}

func NormalizePlan(raw *motisPlan) Plan {
	p := Plan{Itineraries: []Itinerary{}, Direct: []Itinerary{}, Earlier: raw.PreviousPageCursor, Later: raw.NextPageCursor}
	for _, it := range raw.Itineraries {
		p.Itineraries = append(p.Itineraries, it.itinerary())
	}
	for _, it := range raw.Direct {
		p.Direct = append(p.Direct, it.itinerary())
	}
	return p
}

type motisGeocode []struct {
	Type        string   `json:"type"`
	ID          string   `json:"id"`
	Name        string   `json:"name"`
	Lat         float64  `json:"lat"`
	Lon         float64  `json:"lon"`
	Street      string   `json:"street"`
	HouseNumber string   `json:"houseNumber"`
	Modes       []string `json:"modes"`
	Areas       []struct {
		Name    string `json:"name"`
		Default bool   `json:"default"`
	} `json:"areas"`
}

func NormalizeGeocode(raw motisGeocode) []Location {
	out := make([]Location, 0, len(raw))
	for _, g := range raw {
		l := Location{ID: g.ID, Type: g.Type, Name: g.Name, Lat: g.Lat, Lon: g.Lon, Modes: g.Modes}
		if l.Modes == nil {
			l.Modes = []string{}
		}
		if g.Type != "STOP" {
			l.ID = fmt.Sprintf("%g,%g", g.Lat, g.Lon)
		}
		if street := strings.TrimSpace(g.Street + " " + g.HouseNumber); g.Type == "ADDRESS" && street != "" {
			l.Name = street
		}
		for _, a := range g.Areas {
			if a.Default {
				l.Area = a.Name
			}
		}
		if l.Area == "" && len(g.Areas) > 0 {
			l.Area = g.Areas[len(g.Areas)-1].Name
		}
		if l.Area == l.Name {
			l.Area = ""
		}
		out = append(out, l)
	}
	return out
}
