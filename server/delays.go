package main

// Punctuality: every live departure Navi sees (boards, vehicle runs) is remembered once it has left,
// so we learn how late a line usually is, overall, by time of day and at each stop. The app uses this
// to say "usually 4 min late" and to warn about changes that often don't work, before live data exists.
// Only counts are stored, nothing about users. Old data fades out (counts halve when a bucket gets full).

import (
	"compress/gzip"
	"context"
	"encoding/json"
	"log"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

const (
	delayMin     = -5
	delayMax     = 60
	delayBins    = delayMax - delayMin + 1
	histCap      = 4000    // a bucket halves above this, so recent weeks weigh more
	maxHists     = 300_000 // memory guard
	maxPending   = 200_000
	minSamples   = 8
	onTimeMinute = 3 // "on time" means less than 3 minutes late
)

var berlin = func() *time.Location {
	if l, err := time.LoadLocation("Europe/Berlin"); err == nil {
		return l
	}
	return time.FixedZone("CET", 3600)
}()

// DelayStats is what the app gets: minutes late, typical and bad-but-common, plus how sure we are.
type DelayStats struct {
	Median    int    `json:"median"`    // half the time it's at most this late
	Likely    int    `json:"likely"`    // 4 out of 5 times it's at most this late
	OnTime    int    `json:"onTime"`    // % less than 3 min late
	Cancelled int    `json:"cancelled"` // % cancelled
	Samples   int    `json:"samples"`
	Scope     string `json:"scope"` // stop | line
}

type hist struct {
	C [delayBins]uint32 `json:"c"`
	X uint32            `json:"x"` // cancelled
	N uint32            `json:"n"` // total incl. cancelled
}

func (h *hist) add(delay int, cancelled bool) {
	if cancelled {
		h.X++
	} else {
		d := min(max(delay, delayMin), delayMax)
		h.C[d-delayMin]++
	}
	h.N++
	if h.N > histCap {
		h.N = 0
		for i := range h.C {
			h.C[i] /= 2
			h.N += h.C[i]
		}
		h.X /= 2
		h.N += h.X
	}
}

func (h *hist) stats(scope string) *DelayStats {
	ran := h.N - h.X
	if h.N < minSamples || ran == 0 {
		return nil
	}
	pct := func(p float64) int {
		want := uint32(float64(ran)*p + 0.5)
		var sum uint32
		for i, c := range h.C {
			if sum += c; sum >= want && sum > 0 {
				return i + delayMin
			}
		}
		return delayMax
	}
	var onTime uint32
	for i := 0; i < onTimeMinute-delayMin; i++ {
		onTime += h.C[i]
	}
	return &DelayStats{
		Median: max(pct(.5), 0), Likely: max(pct(.8), 0),
		OnTime: int(100 * onTime / ran), Cancelled: int(100 * h.X / h.N),
		Samples: int(h.N), Scope: scope,
	}
}

type pendingObs struct {
	line, stop string
	sched      time.Time
	delay      int
	cancelled  bool
}

type Delays struct {
	mu      sync.Mutex
	hists   map[string]*hist
	pending map[string]*pendingObs
	path    string
}

func NewDelays(dataDir string) *Delays {
	d := &Delays{hists: map[string]*hist{}, pending: map[string]*pendingObs{}, path: filepath.Join(dataDir, "delays.json.gz")}
	d.load()
	return d
}

func (d *Delays) Start(ctx context.Context) {
	go func() {
		tick := time.NewTicker(time.Minute)
		defer tick.Stop()
		for n := 1; ; n++ {
			select {
			case <-ctx.Done():
				d.commit(time.Now())
				d.save()
				return
			case <-tick.C:
				d.commit(time.Now())
				if n%10 == 0 {
					d.save()
				}
			}
		}
	}()
}

func lineKey(line, operator string) string {
	if line == "" {
		return ""
	}
	return normLine(line) + "|" + strings.ToLower(strings.TrimSpace(operator))
}

func stopKey(stopID string) string {
	if k := StationKey(stopID); k != "" {
		return k
	}
	return stopID
}

// band groups hours into parts of the day; weekends count separately.
func band(t time.Time) int {
	t = t.In(berlin)
	b := 0
	switch h := t.Hour(); {
	case h < 6:
		b = 0
	case h < 9:
		b = 1
	case h < 15:
		b = 2
	case h < 19:
		b = 3
	default:
		b = 4
	}
	if wd := t.Weekday(); wd == time.Saturday || wd == time.Sunday {
		b += 5
	}
	return b
}

var bandNames = []string{"night", "morning", "day", "evening-peak", "evening"}

// Observe remembers the latest known delay of one departure; it's counted once the vehicle has left.
func (d *Delays) Observe(id, line, operator, stopID, scheduled string, delay *int, cancelled bool) {
	lk := lineKey(line, operator)
	if lk == "" || stopID == "" || (delay == nil && !cancelled) {
		return
	}
	sched, err := time.Parse(time.RFC3339, scheduled)
	if err != nil {
		return
	}
	o := &pendingObs{line: lk, stop: stopKey(stopID), sched: sched, cancelled: cancelled}
	if delay != nil {
		o.delay = *delay
	}
	key := id + "|" + o.stop + "|" + scheduled
	d.mu.Lock()
	defer d.mu.Unlock()
	if _, ok := d.pending[key]; !ok && len(d.pending) >= maxPending {
		return
	}
	d.pending[key] = o
}

func (d *Delays) commit(now time.Time) {
	d.mu.Lock()
	defer d.mu.Unlock()
	for k, o := range d.pending {
		left := o.sched.Add(time.Duration(o.delay)*time.Minute + 2*time.Minute)
		if now.Before(left) && now.Sub(o.sched) < 3*time.Hour {
			continue
		}
		delete(d.pending, k)
		keys := []string{"S|" + o.line + "|" + o.stop}
		if !strings.HasSuffix(o.line, "|") { // lines without an operator only count per stop ("11" exists everywhere)
			keys = append(keys, "L|"+o.line, "B|"+o.line+"|"+string(rune('0'+band(o.sched))))
		}
		for _, k := range keys {
			h := d.hists[k]
			if h == nil {
				if len(d.hists) >= maxHists {
					continue
				}
				h = &hist{}
				d.hists[k] = h
			}
			h.add(o.delay, o.cancelled)
		}
	}
}

// Predict: the stop if we know enough about it, else the line at this time of day, else the line.
func (d *Delays) Predict(line, operator, stopID, scheduled string) *DelayStats {
	lk := lineKey(line, operator)
	if lk == "" {
		return nil
	}
	d.mu.Lock()
	defer d.mu.Unlock()
	if h := d.hists["S|"+lk+"|"+stopKey(stopID)]; stopID != "" && h != nil {
		if s := h.stats("stop"); s != nil {
			return s
		}
	}
	if t, err := time.Parse(time.RFC3339, scheduled); err == nil {
		if h := d.hists["B|"+lk+"|"+string(rune('0'+band(t)))]; h != nil {
			if s := h.stats("line"); s != nil {
				return s
			}
		}
	}
	if h := d.hists["L|"+lk]; h != nil {
		return h.stats("line")
	}
	return nil
}

// ObserveRun records every stop of a vehicle's run that it has already left.
func (d *Delays) ObserveRun(l Leg) {
	now := time.Now()
	for _, p := range append(append([]Place{l.From}, l.Stops...), l.To) {
		actual, sched := p.Departure, p.ScheduledDeparture
		if sched == "" {
			actual, sched = p.Arrival, p.ScheduledArrival
		}
		t, err := time.Parse(time.RFC3339, actual)
		if err != nil || t.After(now) || !l.Realtime {
			continue
		}
		d.Observe(l.TripID, l.Line, l.Operator, p.StopID, sched, minutesBetween(actual, sched), p.Cancelled || l.Cancelled)
	}
}

// AnnotateLegs adds punctuality to every ride of the itineraries.
func (d *Delays) AnnotateLegs(legs []Leg) {
	for i := range legs {
		l := &legs[i]
		if l.TripID == "" {
			continue
		}
		l.DepDelay = d.Predict(l.Line, l.Operator, l.From.StopID, l.From.ScheduledDeparture)
		l.ArrDelay = d.Predict(l.Line, l.Operator, l.To.StopID, l.To.ScheduledArrival)
	}
}

// ---------- line report ----------

type BandStats struct {
	Band    string      `json:"band"` // night | morning | day | evening-peak | evening
	Weekend bool        `json:"weekend"`
	Stats   *DelayStats `json:"stats"`
}

type Punctuality struct {
	Line      string      `json:"line"`
	Overall   *DelayStats `json:"overall"`
	AtStop    *DelayStats `json:"atStop,omitempty"`
	Bands     []BandStats `json:"bands"`
	Histogram []int       `json:"histogram"` // % of runs per delay class: early/on time, 3–5, 6–10, 11–20, >20 min, cancelled
}

func (d *Delays) Report(line, operator, stopID string) Punctuality {
	lk := lineKey(line, operator)
	p := Punctuality{Line: line, Bands: []BandStats{}, Histogram: []int{}}
	d.mu.Lock()
	defer d.mu.Unlock()
	if h := d.hists["S|"+lk+"|"+stopKey(stopID)]; stopID != "" && h != nil {
		p.AtStop = h.stats("stop")
	}
	h := d.hists["L|"+lk]
	if h == nil {
		h = d.hists["S|"+lk+"|"+stopKey(stopID)]
	}
	if h != nil {
		p.Overall = h.stats("line")
		if p.Overall != nil {
			classes := []int{3, 6, 11, 21, delayMax + 1}
			lo := delayMin
			for _, hi := range classes {
				var n uint32
				for m := lo; m < hi && m <= delayMax; m++ {
					n += h.C[m-delayMin]
				}
				p.Histogram = append(p.Histogram, int(100*n/h.N))
				lo = hi
			}
			p.Histogram = append(p.Histogram, int(100*h.X/h.N))
		}
	}
	for b := 0; b < 10; b++ {
		if bh := d.hists["B|"+lk+"|"+string(rune('0'+b))]; bh != nil {
			if s := bh.stats("line"); s != nil {
				p.Bands = append(p.Bands, BandStats{bandNames[b%5], b >= 5, s})
			}
		}
	}
	sort.SliceStable(p.Bands, func(i, j int) bool { return !p.Bands[i].Weekend && p.Bands[j].Weekend })
	return p
}

func (a *App) punctuality(ctx context.Context, q url.Values) (any, error) {
	line, err := param(q, "line")
	if err != nil {
		return nil, err
	}
	return a.delays.Report(line, q.Get("operator"), q.Get("stop")), nil
}

// ---------- persistence ----------

func (d *Delays) save() {
	d.mu.Lock()
	data, err := json.Marshal(d.hists)
	d.mu.Unlock()
	if err != nil {
		return
	}
	tmp := d.path + ".tmp"
	f, err := os.Create(tmp)
	if err != nil {
		log.Printf("delays: %v", err)
		return
	}
	gz := gzip.NewWriter(f)
	_, err = gz.Write(data)
	if err2 := gz.Close(); err == nil {
		err = err2
	}
	if err2 := f.Close(); err == nil {
		err = err2
	}
	if err != nil {
		log.Printf("delays: %v", err)
		os.Remove(tmp)
		return
	}
	os.Rename(tmp, d.path)
}

func (d *Delays) load() {
	f, err := os.Open(d.path)
	if err != nil {
		return
	}
	defer f.Close()
	gz, err := gzip.NewReader(f)
	if err != nil {
		return
	}
	var m map[string]*hist
	if err := json.NewDecoder(gz).Decode(&m); err != nil {
		log.Printf("delays: %v", err)
		return
	}
	d.hists = m
	log.Printf("delays: %d buckets loaded", len(m))
}
