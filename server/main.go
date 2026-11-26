// Navi server: one small API for the Navi app.
// Combines DB OpenBoards (live boards), FaSta (lift status), OpenStation (station data)
// and MOTIS (routing + all local transport), keeps the DB keys secret and caches aggressively.
package main

import (
	"bufio"
	"compress/gzip"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"math"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"regexp"
	"runtime/debug"
	"sort"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"
)

const version = "0.1.0"

var httpClient = &http.Client{Timeout: 20 * time.Second}

type App struct {
	db        *DBAPI
	motis     *Motis
	stations  *StationIndex
	cache     *Cache
	shares    *Shares
	shareBase string
	ai        *Assistant // nil when no OpenRouter key is set
	delays    *Delays
	photonURL string // named places (shops, restaurants…); empty = off
}

type httpError struct {
	status int
	msg    string
}

func (e httpError) Error() string { return e.msg }

func badRequest(format string, a ...any) error { return httpError{400, fmt.Sprintf(format, a...)} }

func param(q url.Values, name string) (string, error) {
	v := strings.TrimSpace(q.Get(name))
	if v == "" {
		return "", badRequest("missing parameter: %s", name)
	}
	if len(v) > 200 {
		return "", badRequest("invalid %s", name)
	}
	return v, nil
}

func timeParam(q url.Values) (time.Time, error) {
	v := q.Get("time")
	if v == "" {
		return time.Now(), nil
	}
	t, err := time.Parse(time.RFC3339, v)
	if err != nil {
		// Older app builds send Java's ISO form, which leaves out ":00" seconds.
		t, err = time.Parse("2006-01-02T15:04Z07:00", v)
	}
	if err != nil {
		return t, badRequest("invalid time, expected RFC 3339")
	}
	return t, nil
}

var reLatLon = regexp.MustCompile(`^-?\d{1,3}(\.\d+)?,-?\d{1,3}(\.\d+)?$`)

func placeParam(q url.Values, name string) (string, error) {
	v, err := param(q, name)
	if err != nil {
		return "", err
	}
	if !reLatLon.MatchString(v) && strings.ContainsAny(v, "\n\r\t ") {
		return "", badRequest("invalid %s", name)
	}
	return v, nil
}

func flag(q url.Values, name string) bool { return q.Get(name) == "1" || q.Get(name) == "true" }

// ---------- handlers ----------

func (a *App) search(ctx context.Context, q url.Values) (any, error) {
	text, err := param(q, "q")
	if err != nil {
		return nil, err
	}
	if len([]rune(text)) < 2 {
		return []Location{}, nil
	}
	typ := ""
	if q.Get("type") == "stop" {
		typ = "STOP"
	}
	// an optional, coarse position ranks nearby places first ("Rewe" means the one in your town)
	var lat, lon *float64
	if la, err := strconv.ParseFloat(q.Get("lat"), 64); err == nil && math.Abs(la) <= 90 {
		if lo, err := strconv.ParseFloat(q.Get("lon"), 64); err == nil && math.Abs(lo) <= 180 {
			lat, lon = &la, &lo
		}
	}
	near := ""
	if lat != nil {
		near = fmt.Sprintf("|%.1f,%.1f", *lat, *lon)
	}
	return cached(a.cache, "search|"+strings.ToLower(text)+"|"+typ+near, time.Hour, func() ([]Location, error) {
		var raw motisGeocode
		var places []Location
		var placesErr error
		var wg sync.WaitGroup
		if typ == "" {
			wg.Add(1)
			go func() {
				defer wg.Done()
				places, placesErr = a.photon(ctx, text, lat, lon)
				if placesErr != nil {
					log.Printf("photon: %v", placesErr)
				}
			}()
		}
		err := a.motis.get(ctx, "/api/v1/geocode", url.Values{"text": {text}, "language": {"de"}, "type": {typ}}, &raw)
		wg.Wait()
		if err != nil && len(places) == 0 {
			return nil, err
		}
		out := mergeSearch(text, nearGermany(NormalizeGeocode(raw)), places)
		if len(out) > 15 {
			out = out[:15]
		}
		return out, nil
	})
}

// nearGermany drops hits far outside Germany (Transitous is worldwide, so "Goettingen" finds San Francisco).
// The box includes the border regions people commute into.
func nearGermany(in []Location) []Location {
	out := in[:0]
	for _, l := range in {
		if l.Lat >= 45.5 && l.Lat <= 56 && l.Lon >= 4.5 && l.Lon <= 17 {
			out = append(out, l)
		}
	}
	return out
}

func (a *App) nearby(ctx context.Context, q url.Values) (any, error) {
	lat, err1 := strconv.ParseFloat(q.Get("lat"), 64)
	lon, err2 := strconv.ParseFloat(q.Get("lon"), 64)
	if err1 != nil || err2 != nil || math.Abs(lat) > 90 || math.Abs(lon) > 180 {
		return nil, badRequest("invalid coordinates")
	}
	r := 600.0
	if v, err := strconv.ParseFloat(q.Get("radius"), 64); err == nil {
		r = math.Min(math.Max(v, 50), 3000)
	}
	cosLat := math.Cos(lat * math.Pi / 180)
	dLat, dLon := r/111320, r/(111320*cosLat)
	// rounded key: neighbours share cache entries
	key := fmt.Sprintf("nearby|%.3f|%.3f|%.0f", lat, lon, r)
	return cached(a.cache, key, 10*time.Minute, func() ([]Location, error) {
		var stops []motisPlace
		err := a.motis.get(ctx, "/api/v1/map/stops", url.Values{
			"min": {fmt.Sprintf("%f,%f", lat-dLat, lon-dLon)},
			"max": {fmt.Sprintf("%f,%f", lat+dLat, lon+dLon)},
		}, &stops)
		if err != nil {
			return nil, err
		}
		known := func(id string) bool { return a.stations.Get(StationKey(id)) != nil }
		// collapse platforms/duplicate feeds of the same stop (same name): prefer ids we have station data for, then the closest
		best := map[string]Location{}
		for _, s := range stops {
			d := int(math.Hypot((s.Lat-lat)*111320, (s.Lon-lon)*111320*cosLat))
			if float64(d) > r {
				continue
			}
			l := Location{ID: s.StopID, Type: "STOP", Name: s.Name, Lat: s.Lat, Lon: s.Lon, Modes: s.Modes, Distance: &d}
			if l.Modes == nil {
				l.Modes = []string{}
			}
			prev, ok := best[s.Name]
			if !ok || (known(l.ID) && !known(prev.ID)) || (known(l.ID) == known(prev.ID) && d < *prev.Distance) {
				best[s.Name] = l
			}
		}
		out := make([]Location, 0, len(best))
		for _, l := range best {
			out = append(out, l)
		}
		sort.Slice(out, func(i, j int) bool { return *out[i].Distance < *out[j].Distance })
		if len(out) > 40 {
			out = out[:40]
		}
		return out, nil
	})
}

type Board struct {
	Stop struct {
		ID             string `json:"id"`
		Name           string `json:"name,omitempty"`
		HasStationInfo bool   `json:"hasStationInfo"`
	} `json:"stop"`
	Sources struct {
		DB    bool `json:"db"`
		Motis bool `json:"motis"`
	} `json:"sources"`
	Rows []Row `json:"rows"`
}

func (a *App) board(ctx context.Context, q url.Values) (any, error) {
	stopID, err := placeParam(q, "stop")
	if err != nil {
		return nil, err
	}
	at, err := timeParam(q)
	if err != nil {
		return nil, err
	}
	arrivals := flag(q, "arrivals")
	const window = 90 * time.Minute
	minute := at.Truncate(time.Minute)
	station := a.stations.Get(StationKey(stopID))

	var wg sync.WaitGroup
	var motisRows, dbRows []Row
	var motisErr, dbErr error
	wg.Add(1)
	go func() {
		defer wg.Done()
		motisRows, motisErr = cached(a.cache, fmt.Sprint("st|", stopID, arrivals, minute.Unix()), 45*time.Second, func() ([]Row, error) {
			var raw motisStopTimes
			err := a.motis.get(ctx, "/api/v5/stoptimes", url.Values{
				"stopId": {stopID}, "time": {minute.UTC().Format(time.RFC3339)}, "arriveBy": {strconv.FormatBool(arrivals)},
				"n": {"80"}, "window": {strconv.Itoa(int(window.Seconds()))},
			}, &raw)
			return NormalizeStopTimes(&raw, arrivals), err
		})
	}()
	if station != nil && a.db.Enabled() {
		wg.Add(1)
		go func() {
			defer wg.Done()
			// one board per station per minute keeps us far below the daily quota
			dbRows, dbErr = cached(a.cache, fmt.Sprint("ob|", station.DHID, arrivals, minute.Unix()), time.Minute, func() ([]Row, error) {
				raw, err := a.db.Board(ctx, station.DHID, arrivals, minute, minute.Add(window))
				if err != nil {
					return nil, err
				}
				return NormalizeDBBoard(raw, arrivals), nil
			})
			if dbErr != nil {
				log.Printf("openboards %s: %v", station.DHID, dbErr)
			}
		}()
	}
	wg.Wait()
	if motisErr != nil {
		log.Printf("stoptimes %s: %v", stopID, motisErr)
	}

	var b Board
	b.Stop.ID = stopID
	b.Sources.DB, b.Sources.Motis = dbErr == nil && dbRows != nil, motisErr == nil
	if station != nil {
		b.Stop.Name, b.Stop.HasStationInfo = station.Name, true
	}
	if !b.Sources.DB && !b.Sources.Motis {
		return nil, httpError{502, "no departure data available right now"}
	}
	if b.Sources.DB {
		// DB is authoritative for trains (platform changes, operator notes); MOTIS adds buses, trams, …
		var rail, other []Row
		for _, r := range motisRows {
			if railModes[r.Mode] {
				rail = append(rail, r)
			} else {
				other = append(other, r)
			}
		}
		rows := append([]Row{}, dbRows...) // copy: cached slices are shared
		LinkTrips(rows, rail)
		b.Rows = append(rows, other...)
	} else {
		b.Rows = append([]Row{}, motisRows...)
	}
	for i := range b.Rows {
		r := &b.Rows[i]
		if !arrivals {
			a.delays.Observe(r.ID, r.Line, r.Operator, stopID, r.ScheduledTime, r.Delay, r.Cancelled)
		}
		r.Usual = a.delays.Predict(r.Line, r.Operator, stopID, r.ScheduledTime)
	}
	sort.SliceStable(b.Rows, func(i, j int) bool {
		ti, _ := time.Parse(time.RFC3339, b.Rows[i].Time)
		tj, _ := time.Parse(time.RFC3339, b.Rows[j].Time)
		return ti.Before(tj)
	})
	return b, nil
}

func (a *App) plan(ctx context.Context, q url.Values) (any, error) {
	from, err := placeParam(q, "from")
	if err != nil {
		return nil, err
	}
	to, err := placeParam(q, "to")
	if err != nil {
		return nil, err
	}
	at, err := timeParam(q)
	if err != nil {
		return nil, err
	}
	cursor := q.Get("cursor")
	arriveBy, bike, wheelchair := flag(q, "arriveBy"), flag(q, "bike"), flag(q, "wheelchair")
	modes, err := transitModes(q.Get("modes"))
	if err != nil {
		return nil, err
	}
	// optional: one stop to pass through, extra minutes per change, walking pace
	via := q.Get("via")
	if via != "" && (reLatLon.MatchString(via) || strings.ContainsAny(via, "\n\r\t ")) {
		return nil, badRequest("via must be a stop id")
	}
	minTransfer := 0
	if v := q.Get("minTransfer"); v != "" {
		n, err := strconv.Atoi(v)
		if err != nil || n < 0 || n > 30 {
			return nil, badRequest("minTransfer must be 0-30 minutes")
		}
		minTransfer = n
	}
	walkSpeed := map[string]string{"slow": "0.9", "fast": "1.6"}[q.Get("walk")]
	key := fmt.Sprint("plan|", from, "|", to, "|", at.Truncate(time.Minute).Unix(), arriveBy, bike, wheelchair, "|", modes, "|", cursor,
		"|", via, "|", minTransfer, "|", walkSpeed)
	return cached(a.cache, key, time.Minute, func() (Plan, error) {
		params := url.Values{
			"fromPlace": {from}, "toPlace": {to}, "time": {at.UTC().Format(time.RFC3339)},
			"arriveBy": {strconv.FormatBool(arriveBy)}, "pageCursor": {cursor},
			"detailedTransfers": {"true"}, "joinInterlinedLegs": {"true"},
		}
		if bike {
			params.Set("requireBikeTransport", "true")
		}
		if wheelchair {
			params.Set("pedestrianProfile", "WHEELCHAIR")
		}
		if modes != "" {
			params.Set("transitModes", modes)
		}
		if via != "" {
			params.Set("via", via)
		}
		if minTransfer > 0 {
			params.Set("minTransferTime", strconv.Itoa(minTransfer))
		}
		if walkSpeed != "" && !wheelchair {
			params.Set("pedestrianSpeed", walkSpeed)
		}
		var raw motisPlan
		if err := a.motis.get(ctx, "/api/v5/plan", params, &raw); err != nil {
			var me motisError
			if errors.As(err, &me) && me.status == 400 {
				return Plan{}, badRequest("could not plan this route")
			}
			return Plan{}, err
		}
		p := NormalizePlan(&raw)
		p.Itineraries = a.FixSameLine(ctx, p.Itineraries)
		for i := range p.Itineraries {
			a.delays.AnnotateLegs(p.Itineraries[i].Legs)
		}
		return p, nil
	})
}

func (a *App) trip(ctx context.Context, q url.Values) (any, error) {
	id, err := param(q, "id")
	if err != nil {
		return nil, err
	}
	return a.tripLeg(ctx, id)
}

type StationResponse struct {
	Station
	LiveStatus    bool      `json:"liveStatus"`
	DataUpdatedAt time.Time `json:"dataUpdatedAt"`
}

func (a *App) station(ctx context.Context, q url.Values) (any, error) {
	id, err := param(q, "stop")
	if err != nil {
		return nil, err
	}
	st := a.stations.Get(StationKey(id))
	if st == nil {
		st = a.stations.Get(id)
	}
	if st == nil {
		return nil, httpError{404, "no station information for this stop"}
	}
	resp := StationResponse{Station: *st}
	_, resp.DataUpdatedAt = a.stations.Stats()
	var live *FastaStation
	if st.Stada != "" && a.db.Enabled() {
		live, err = cached(a.cache, "fasta|"+st.Stada, 2*time.Minute, func() (*FastaStation, error) { return a.db.Facilities(ctx, st.Stada) })
		if err != nil {
			log.Printf("fasta %s: %v", st.Stada, err)
			live = nil
		}
	}
	resp.LiveStatus = live != nil
	type fac = struct {
		eq   Equipment
		used bool
	}
	status := map[string]*fac{}
	if live != nil {
		for _, f := range live.Facilities {
			typ := "lift"
			if f.Type == "ESCALATOR" {
				typ = "escalator"
			}
			status[strconv.Itoa(f.EquipmentNumber)] = &fac{eq: Equipment{
				Type: typ, Name: f.Description, EquipmentNumber: strconv.Itoa(f.EquipmentNumber),
				Lat: f.GeocoordY, Lon: f.GeocoordX, Status: f.State, StatusText: f.StateExplanation,
			}}
		}
	}
	resp.Equipment = make([]Equipment, 0, len(st.Equipment)+len(status))
	for _, e := range st.Equipment {
		e.Status = "UNKNOWN"
		if f := status[e.EquipmentNumber]; e.EquipmentNumber != "" && f != nil {
			f.used = true
			e.Status, e.StatusText = f.eq.Status, f.eq.StatusText
			if e.Lat == nil {
				e.Lat, e.Lon = f.eq.Lat, f.eq.Lon
			}
		}
		resp.Equipment = append(resp.Equipment, e)
	}
	for _, f := range status { // FaSta knows a few lifts/escalators NeTEx doesn't list
		if !f.used {
			resp.Equipment = append(resp.Equipment, f.eq)
		}
	}
	return resp, nil
}

func (a *App) health(context.Context, url.Values) (any, error) {
	n, at := a.stations.Stats()
	return map[string]any{
		"ok": true, "version": version, "stations": n, "stationsUpdatedAt": at,
		"dbQuotaRemaining": a.db.Remaining(), "cacheEntries": a.cache.Len(),
	}, nil
}

// ---------- plumbing ----------

type rateLimiter struct {
	mu      sync.Mutex
	perMin  float64
	burst   float64 // bucket size; defaults to perMin
	buckets map[string]*bucket
}

type bucket struct {
	tokens float64
	at     time.Time
}

func (rl *rateLimiter) allow(ip string) bool {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	now := time.Now()
	if len(rl.buckets) > 100_000 {
		rl.buckets = map[string]*bucket{}
	}
	capacity := math.Max(rl.perMin, rl.burst)
	b := rl.buckets[ip]
	if b == nil {
		b = &bucket{capacity, now}
		rl.buckets[ip] = b
	}
	b.tokens = math.Min(capacity, b.tokens+now.Sub(b.at).Minutes()*rl.perMin)
	b.at = now
	if b.tokens < 1 {
		return false
	}
	b.tokens--
	return true
}

func writeJSON(w http.ResponseWriter, r *http.Request, status int, body any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	if strings.Contains(r.Header.Get("Accept-Encoding"), "gzip") {
		w.Header().Set("Content-Encoding", "gzip")
		w.WriteHeader(status)
		gz := gzip.NewWriter(w)
		defer gz.Close()
		json.NewEncoder(gz).Encode(body)
		return
	}
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(body)
}

func loadDotEnv(path string) {
	f, err := os.Open(path)
	if err != nil {
		return
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		k, v, ok := strings.Cut(line, "=")
		if !ok || strings.HasPrefix(line, "#") {
			continue
		}
		if _, set := os.LookupEnv(strings.TrimSpace(k)); !set { // real env wins
			os.Setenv(strings.TrimSpace(k), strings.Trim(strings.TrimSpace(v), `"'`))
		}
	}
}

// envOr is like env, but an explicitly empty value stays empty (to switch something off).
func envOr(k, def string) string {
	if v, ok := os.LookupEnv(k); ok {
		return v
	}
	return def
}

func env(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}

func main() {
	loadDotEnv(".env")
	// soft memory cap for small containers (the daily station refresh briefly holds old + new data)
	if mb, _ := strconv.Atoi(os.Getenv("MEMORY_LIMIT_MB")); mb > 0 {
		debug.SetMemoryLimit(int64(mb) << 20)
	}
	dataDir := env("DATA_DIR", "./data")
	if err := os.MkdirAll(dataDir, 0o755); err != nil {
		log.Fatal(err)
	}
	reserve, _ := strconv.Atoi(env("DB_QUOTA_RESERVE", "150"))
	perMin, _ := strconv.ParseFloat(env("RATE_PER_MIN", "120"), 64)
	trustProxy := env("TRUST_PROXY", "0") == "1"

	app := &App{
		db: &DBAPI{ClientID: os.Getenv("DB_CLIENT_ID"), APIKey: os.Getenv("DB_API_KEY"), Reserve: reserve,
			blockedUntil: map[string]time.Time{}, remaining: map[string]int{}},
		motis:    &Motis{Base: strings.TrimRight(env("MOTIS_URL", "https://api.transitous.org"), "/"), UserAgent: env("USER_AGENT", "Navi-server/"+version+" (FOSS transit app)")},
		stations: NewStationIndex(dataDir),
		cache:    NewCache(20_000),
		shares:   NewShares(dataDir),
		delays:   NewDelays(dataDir),
		// Photon (OpenStreetMap) for named places; set PHOTON_URL= (empty) to turn it off
		photonURL: strings.TrimRight(envOr("PHOTON_URL", "https://photon.komoot.io"), "/"),
		// shared trips open on the website's trip page
		shareBase: env("SHARE_URL", "https://taynotfound.github.io/navi/trip/?id="),
	}
	if key := os.Getenv("OPENROUTER_API_KEY"); key != "" {
		limit, _ := strconv.Atoi(env("ASSISTANT_DAILY_LIMIT", "300"))
		var models []string
		for _, m := range strings.Split(env("OPENROUTER_MODELS", "google/gemma-4-31b-it:free,nvidia/nemotron-3.5-super-vl:free,openrouter/free"), ",") {
			// free models only, enforced here so a config typo can never cost money
			if m = strings.TrimSpace(m); strings.HasSuffix(m, ":free") || m == "openrouter/free" {
				models = append(models, m)
			}
		}
		if len(models) > 0 {
			app.ai = &Assistant{Key: key, Referer: env("SITE_URL", "https://taynotfound.github.io/navi/"), Models: models, DailyLimit: limit}
		}
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	app.delays.Start(ctx)
	app.stations.Start(ctx) // non-blocking: boards work without it; station info appears once loaded

	rl := &rateLimiter{perMin: perMin, buckets: map[string]*bucket{}}
	shareRL := &rateLimiter{perMin: 0.5, burst: 10, buckets: map[string]*bucket{}} // ~30 new share links per hour per IP
	aiRL := &rateLimiter{perMin: 4, burst: 6, buckets: map[string]*bucket{}}
	get := func(h func(context.Context, url.Values) (any, error)) func(context.Context, *http.Request) (any, error) {
		return func(ctx context.Context, r *http.Request) (any, error) { return h(ctx, r.URL.Query()) }
	}
	mux := http.NewServeMux()
	for pattern, h := range map[string]func(context.Context, *http.Request) (any, error){
		"GET /v1/search": get(app.search), "GET /v1/nearby": get(app.nearby), "GET /v1/board": get(app.board),
		"GET /v1/plan": get(app.plan), "GET /v1/trip": get(app.trip), "GET /v1/station": get(app.station),
		"GET /v1/punctuality": get(app.punctuality), "GET /v1/photos": get(app.photos),
		"GET /healthz": get(app.health), "POST /v1/live": app.liveHandler, "GET /v1/share": app.shareGet,
		"POST /v1/share": app.shareCreate, "POST /v1/assistant": app.assistantHandler,
	} {
		mux.HandleFunc(pattern, func(w http.ResponseWriter, r *http.Request) {
			if strings.HasPrefix(pattern, "GET ") {
				w.Header().Set("Access-Control-Allow-Origin", "*") // public, read-only, no credentials
			}
			ip, _, _ := net.SplitHostPort(r.RemoteAddr)
			if fwd := r.Header.Get("X-Forwarded-For"); trustProxy && fwd != "" {
				ip = strings.TrimSpace(strings.Split(fwd, ",")[0])
			}
			if !rl.allow(ip) || (pattern == "POST /v1/share" && !shareRL.allow(ip)) || (pattern == "POST /v1/assistant" && !aiRL.allow(ip)) {
				writeJSON(w, r, 429, map[string]string{"error": "too many requests"})
				return
			}
			// detach from the client: a cancelled request must not poison the shared cache entry
			reqCtx, cancel := context.WithTimeout(context.WithoutCancel(r.Context()), 25*time.Second)
			defer cancel()
			out, err := h(reqCtx, r)
			if err != nil {
				var he httpError
				if errors.As(err, &he) {
					writeJSON(w, r, he.status, map[string]string{"error": he.msg})
					return
				}
				log.Printf("%s: %v", pattern, err)
				writeJSON(w, r, 502, map[string]string{"error": "upstream unavailable, please try again"})
				return
			}
			writeJSON(w, r, 200, out)
		})
	}
	site := env("SITE_URL", "https://taynotfound.github.io/navi/")
	mux.HandleFunc("GET /{$}", func(w http.ResponseWriter, r *http.Request) { http.Redirect(w, r, site, http.StatusFound) })
	// old share links (/j/{id}) keep working
	mux.HandleFunc("GET /j/{id}", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, app.shareBase+url.QueryEscape(r.PathValue("id")), http.StatusFound)
	})
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, r, 404, map[string]string{"error": "not found"})
	})

	srv := &http.Server{Addr: ":" + env("PORT", "8080"), Handler: mux, ReadHeaderTimeout: 10 * time.Second}
	go func() {
		<-ctx.Done()
		shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		srv.Shutdown(shutdown)
	}()
	log.Printf("navi-server %s on %s (DB API %v, MOTIS %s, assistant %v)", version, srv.Addr, app.db.Enabled(), app.motis.Base, app.ai != nil)
	if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal(err)
	}
}

// modeGroups are the app's type filter ("bus,tram") in MOTIS transit modes.
var modeGroups = map[string][]string{
	"long":     {"HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL"},
	"regional": {"REGIONAL_FAST_RAIL", "REGIONAL_RAIL"},
	"suburban": {"SUBURBAN"},
	"subway":   {"SUBWAY", "METRO"},
	"tram":     {"TRAM", "CABLE_CAR", "FUNICULAR"},
	"bus":      {"BUS", "COACH"},
	"ferry":    {"FERRY"},
}

// transitModes turns the filter into MOTIS' list; empty means everything.
func transitModes(s string) (string, error) {
	if s == "" {
		return "", nil
	}
	seen := map[string]bool{}
	var out []string
	for _, g := range strings.Split(strings.ToLower(s), ",") {
		ms, ok := modeGroups[strings.TrimSpace(g)]
		if !ok {
			return "", badRequest("invalid modes")
		}
		for _, m := range ms {
			if !seen[m] {
				seen[m] = true
				out = append(out, m)
			}
		}
	}
	return strings.Join(out, ","), nil
}
