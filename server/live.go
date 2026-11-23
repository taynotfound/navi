package main

// Live trips and sharing: an itinerary is refreshed by re-fetching each vehicle's run (MOTIS trip),
// so delays, platform changes and cancellations show up after planning. Shared itineraries are
// stored as small JSON files and rendered by the public web page at /j/{id}.

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"time"
)

const (
	shareTTL       = 7 * 24 * time.Hour
	maxShareBytes  = 256 << 10
	maxShareFiles  = 50_000
	shareIDLetters = "abcdefghijkmnpqrstuvwxyz23456789" // no 0/o/1/l: readable when typed
)

var reShareID = regexp.MustCompile(`^[a-z2-9]{10}$`)

type Shares struct{ dir string }

func NewShares(dataDir string) *Shares {
	s := &Shares{dir: filepath.Join(dataDir, "shares")}
	os.MkdirAll(s.dir, 0o755)
	go func() {
		for ; ; time.Sleep(time.Hour) {
			s.cleanup()
		}
	}()
	return s
}

func (s *Shares) cleanup() {
	entries, _ := os.ReadDir(s.dir)
	for _, e := range entries {
		if fi, err := e.Info(); err == nil && time.Since(fi.ModTime()) > shareTTL {
			os.Remove(filepath.Join(s.dir, e.Name()))
		}
	}
}

func newShareID() string {
	b := make([]byte, 10)
	rand.Read(b)
	for i := range b {
		b[i] = shareIDLetters[int(b[i])%len(shareIDLetters)]
	}
	return string(b)
}

func (s *Shares) Save(it Itinerary) (string, error) {
	if entries, _ := os.ReadDir(s.dir); len(entries) >= maxShareFiles {
		return "", httpError{503, "sharing is temporarily unavailable"}
	}
	data, err := json.Marshal(it)
	if err != nil {
		return "", err
	}
	id := newShareID()
	return id, os.WriteFile(filepath.Join(s.dir, id+".json"), data, 0o644)
}

func (s *Shares) Load(id string) (Itinerary, error) {
	var it Itinerary
	if !reShareID.MatchString(id) { // also the path-traversal guard
		return it, httpError{404, "shared trip not found"}
	}
	path := filepath.Join(s.dir, id+".json")
	fi, err := os.Stat(path)
	if err != nil || time.Since(fi.ModTime()) > shareTTL {
		return it, httpError{404, "shared trip not found or expired"}
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return it, err
	}
	return it, json.Unmarshal(data, &it)
}

// readItinerary decodes and sanity-checks an itinerary posted by a client.
func readItinerary(r *http.Request) (Itinerary, error) {
	var it Itinerary
	body, err := io.ReadAll(http.MaxBytesReader(nil, r.Body, maxShareBytes))
	if err != nil {
		return it, httpError{413, "itinerary too large"}
	}
	if err := json.Unmarshal(body, &it); err != nil {
		return it, badRequest("invalid itinerary")
	}
	if len(it.Legs) == 0 || len(it.Legs) > 40 {
		return it, badRequest("invalid itinerary")
	}
	for _, l := range it.Legs {
		if len(l.Stops) > 500 || len(l.TripID) > 300 {
			return it, badRequest("invalid itinerary")
		}
	}
	return it, nil
}

// tripLeg fetches one vehicle run (cached, shared with GET /v1/trip).
func (a *App) tripLeg(ctx context.Context, id string) (Leg, error) {
	return cached(a.cache, "trip|"+id, 30*time.Second, func() (Leg, error) {
		var raw motisItinerary
		if err := a.motis.get(ctx, "/api/v5/trip", map[string][]string{"tripId": {id}}, &raw); err != nil {
			return Leg{}, err
		}
		for _, l := range raw.Legs {
			if l.TripID != "" {
				return l.leg(), nil
			}
		}
		return Leg{}, httpError{404, "trip not found"}
	})
}

// samePlace matches a stop of the planned leg with a stop of the live run.
func samePlace(a, b Place) bool {
	if a.StopID != "" && a.StopID == b.StopID {
		return true
	}
	if a.StopID != "" && b.StopID != "" || a.Name == "" || a.Name != b.Name {
		return false
	}
	return (a.ScheduledDeparture != "" && a.ScheduledDeparture == b.ScheduledDeparture) ||
		(a.ScheduledArrival != "" && a.ScheduledArrival == b.ScheduledArrival)
}

// RefreshLeg copies live times, platforms and cancellations from the vehicle's current run into a planned leg.
func RefreshLeg(planned, live Leg) Leg {
	run := append(append([]Place{live.From}, live.Stops...), live.To)
	update := func(p *Place) {
		for _, q := range run {
			if samePlace(*p, q) {
				if q.Departure != "" {
					p.Departure = q.Departure
				}
				if q.Arrival != "" {
					p.Arrival = q.Arrival
				}
				if q.Platform != "" {
					p.Platform = q.Platform
				}
				p.Cancelled = q.Cancelled
				return
			}
		}
	}
	update(&planned.From)
	update(&planned.To)
	for i := range planned.Stops {
		update(&planned.Stops[i])
	}
	planned.Realtime = live.Realtime
	planned.Cancelled = live.Cancelled
	if len(live.Alerts) > 0 {
		planned.Alerts = live.Alerts
	}
	return planned
}

// LiveItinerary refreshes every transit leg in parallel; a leg whose run can't be fetched keeps its planned data.
func (a *App) LiveItinerary(ctx context.Context, it Itinerary) Itinerary {
	type result struct {
		i   int
		leg Leg
	}
	ch := make(chan result)
	n := 0
	for i, l := range it.Legs {
		if l.TripID == "" {
			continue
		}
		n++
		go func(i int, l Leg) {
			live, err := a.tripLeg(ctx, l.TripID)
			if err != nil {
				log.Printf("live leg %s: %v", l.TripID, err)
				ch <- result{i, l}
				return
			}
			leg := RefreshLeg(l, live)
			leg.Notes = a.legNotes(ctx, leg)
			ch <- result{i, leg}
		}(i, l)
	}
	legs := append([]Leg{}, it.Legs...)
	for ; n > 0; n-- {
		r := <-ch
		legs[r.i] = r.leg
	}
	it.Legs = legs
	// shortcut: start/end only follow the first/last leg when it is transit; leading/trailing walks keep planned times.
	if f := legs[0]; f.TripID != "" && f.From.Departure != "" {
		it.Start = f.From.Departure
	}
	if l := legs[len(legs)-1]; l.TripID != "" && l.To.Arrival != "" {
		it.End = l.To.Arrival
	}
	return it
}

type SharedTrip struct {
	ID string `json:"id"`
	Itinerary
}

func (a *App) liveHandler(ctx context.Context, r *http.Request) (any, error) {
	it, err := readItinerary(r)
	if err != nil {
		return nil, err
	}
	return a.LiveItinerary(ctx, it), nil
}

func (a *App) shareCreate(ctx context.Context, r *http.Request) (any, error) {
	it, err := readItinerary(r)
	if err != nil {
		return nil, err
	}
	id, err := a.shares.Save(it)
	if err != nil {
		return nil, err
	}
	return map[string]string{"id": id, "url": a.shareURL(id)}, nil
}

func (a *App) shareGet(ctx context.Context, r *http.Request) (any, error) {
	id := r.URL.Query().Get("id")
	it, err := a.shares.Load(id)
	if err != nil {
		return nil, err
	}
	return SharedTrip{ID: id, Itinerary: a.LiveItinerary(ctx, it)}, nil
}

// shareURL is where a shared trip opens: the website's trip page (SHARE_URL + id).
func (a *App) shareURL(id string) string { return a.shareBase + url.QueryEscape(id) }

// legNotes finds this ride on its departure board and returns DB's messages for it
// ("Reparatur an einem Signal"). Boards are cached, so this costs no extra upstream calls
// for people watching the same stop.
func (a *App) legNotes(ctx context.Context, l Leg) []string {
	dep, err := time.Parse(time.RFC3339, l.From.ScheduledDeparture)
	if l.From.StopID == "" || err != nil || time.Until(dep) > 80*time.Minute || time.Since(dep) > 3*time.Hour {
		return l.Notes
	}
	stop := l.From.StopID
	if a.stations.Get(StationKey(stop)) == nil {
		if st := a.stations.Nearest(l.From.Lat, l.From.Lon, 400); st != nil {
			stop = "de-DELFI_" + st.DHID
		}
	}
	res, err := a.board(ctx, url.Values{"stop": {stop}, "time": {dep.Add(-2 * time.Minute).Format(time.RFC3339)}})
	if err != nil {
		return l.Notes
	}
	b, ok := res.(Board)
	if !ok {
		return l.Notes
	}
	for _, r := range b.Rows {
		if (r.TripID != "" && r.TripID == l.TripID) || (r.Line == l.Line && r.ScheduledTime == l.From.ScheduledDeparture) {
			if len(r.Notes) > 0 {
				return r.Notes
			}
			return l.Notes
		}
	}
	return l.Notes
}
