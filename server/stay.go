package main

// Stay on board: sometimes the router sends you to the end of a line, makes you wait (often an hour)
// and then puts you on the same line again to reach a stop further on. In reality the bus usually just
// turns at the end stop and carries on a few minutes later, or the timetable splits one run into two trips
// and the transfer rules there forbid "changing" into the same vehicle. FixSameLine finds these legs and
// replaces the long wait with the earliest run of the same line that actually gets you there.

import (
	"context"
	"fmt"
	"log"
	"math"
	"net/url"
	"strconv"
	"strings"
	"time"
)

const (
	sameLineMinWait  = 6 * time.Minute  // shorter waits are fine as planned
	sameLineLookback = 0                // a run that leaves before you arrive is no use
	sameLineWindow   = 75 * time.Minute // how far after the arrival we look for an earlier run
)

func normLine(s string) string {
	return strings.ToUpper(strings.NewReplacer(" ", "", "-", "", "_", "").Replace(s))
}

// sameLine reports whether two legs are the same line of the same operator.
func sameLine(a, b Leg) bool {
	if a.Line == "" || normLine(a.Line) != normLine(b.Line) || a.Mode != b.Mode {
		return false
	}
	return a.Operator == "" || b.Operator == "" || a.Operator == b.Operator
}

// distM is the rough distance between two coordinates in metres.
func distM(lat1, lon1, lat2, lon2 float64) float64 {
	dLat := (lat2 - lat1) * 111320
	dLon := (lon2 - lon1) * 111320 * math.Cos(lat1*math.Pi/180)
	return math.Hypot(dLat, dLon)
}

// sameStop matches stops across trips: same id, or same name close by (platforms of one stop differ in id).
func sameStop(a, b Place) bool {
	if a.StopID != "" && a.StopID == b.StopID {
		return true
	}
	return a.Name != "" && strings.EqualFold(a.Name, b.Name) && distM(a.Lat, a.Lon, b.Lat, b.Lon) < 400
}

func depTime(p Place) time.Time {
	for _, s := range []string{p.ScheduledDeparture, p.Departure, p.ScheduledArrival, p.Arrival} {
		if t, err := time.Parse(time.RFC3339, s); err == nil {
			return t
		}
	}
	return time.Time{}
}

func arrTime(p Place) time.Time {
	for _, s := range []string{p.ScheduledArrival, p.Arrival, p.ScheduledDeparture, p.Departure} {
		if t, err := time.Parse(time.RFC3339, s); err == nil {
			return t
		}
	}
	return time.Time{}
}

// FixSameLine rewrites itineraries in place. At most a few upstream calls per plan; everything is cached.
func (a *App) FixSameLine(ctx context.Context, its []Itinerary) []Itinerary {
	out := make([]Itinerary, 0, len(its))
	seen := map[string]bool{}
	budget := 3 // itineraries we may look up per request
	for _, it := range its {
		if fixed, ok := a.fixItinerary(ctx, it, &budget); ok {
			it = fixed
		}
		// after fixing, two itineraries can be the same ride
		k := itineraryKey(it)
		if seen[k] {
			continue
		}
		seen[k] = true
		out = append(out, it)
	}
	return out
}

func itineraryKey(it Itinerary) string {
	var b strings.Builder
	for _, l := range it.Legs {
		if l.TripID != "" {
			b.WriteString(l.TripID + "@" + l.From.ScheduledDeparture + ">" + l.To.Name + "|")
		}
	}
	if b.Len() == 0 {
		return it.Start + it.End
	}
	return b.String()
}

func (a *App) fixItinerary(ctx context.Context, it Itinerary, budget *int) (Itinerary, bool) {
	changed := false
	for {
		i, j := findSameLinePair(it.Legs)
		if i < 0 {
			break
		}
		first, second := it.Legs[i], it.Legs[j]
		var repl *Leg
		passes := false
		// 1. the stop you want was already on the first ride: just stay on until there
		for k, s := range first.Stops {
			if sameStop(s, second.To) {
				l := first
				l.To = s
				l.Stops = append([]Place{}, first.Stops[:k]...)
				l.Geometry = trimGeometry(first.Geometry, l.From, l.To)
				l.Duration = int(arrTime(l.To).Sub(depTime(l.From)).Seconds())
				l.StayOnBoard = "passes"
				repl, passes = &l, true
				break
			}
		}
		// 2. an earlier run of the same line from the end stop
		if repl == nil && *budget > 0 {
			*budget--
			repl = a.earlierRun(ctx, first, second)
		}
		if repl == nil {
			break // can't improve this one
		}
		changed = true
		legs := append([]Leg{}, it.Legs[:i]...)
		if passes {
			legs = append(legs, *repl) // one ride replaces both, the walk in between goes too
		} else {
			legs = append(legs, first, *repl)
		}
		legs = append(legs, it.Legs[j+1:]...)
		// rides after this one keep their times; walks after the last ride move with the new arrival
		if tail := len(it.Legs) - 1 - j; lastTransit(legs) == len(legs)-1-tail {
			shift := arrTime(repl.To).Sub(arrTime(second.To))
			for k := len(legs) - tail; k < len(legs); k++ {
				legs[k] = shiftLeg(legs[k], shift)
			}
		}
		it.Legs = legs
	}
	if !changed {
		return it, false
	}
	it.Transfers = -1
	for _, l := range it.Legs {
		if l.TripID != "" {
			it.Transfers++
		}
	}
	if it.Transfers < 0 {
		it.Transfers = 0
	}
	last := it.Legs[len(it.Legs)-1]
	if t := arrTime(last.To); !t.IsZero() {
		it.End = t.Format(time.RFC3339)
	}
	if s, err := time.Parse(time.RFC3339, it.Start); err == nil {
		if e, err := time.Parse(time.RFC3339, it.End); err == nil {
			it.Duration = int(e.Sub(s).Seconds())
		}
	}
	return it, true
}

func lastTransit(legs []Leg) int {
	for k := len(legs) - 1; k >= 0; k-- {
		if legs[k].TripID != "" {
			return k
		}
	}
	return -1
}

func shiftLeg(l Leg, d time.Duration) Leg {
	sh := func(s string) string {
		if t, err := time.Parse(time.RFC3339, s); err == nil {
			return t.Add(d).Format(time.RFC3339)
		}
		return s
	}
	for _, p := range []*Place{&l.From, &l.To} {
		p.Arrival, p.ScheduledArrival = sh(p.Arrival), sh(p.ScheduledArrival)
		p.Departure, p.ScheduledDeparture = sh(p.Departure), sh(p.ScheduledDeparture)
	}
	return l
}

// findSameLinePair returns the indexes of two consecutive rides on the same line with a long wait in between
// at the same stop, where the first ride ends at its last stop (or passes your stop).
func findSameLinePair(legs []Leg) (int, int) {
	prev := -1
	for k, l := range legs {
		if l.TripID == "" {
			if !l.isShortWalk() {
				prev = -1
			}
			continue
		}
		if prev >= 0 && sameLine(legs[prev], l) && sameStop(legs[prev].To, l.From) && l.StayOnBoard == "" {
			wait := depTime(l.From).Sub(arrTime(legs[prev].To))
			if wait >= sameLineMinWait {
				return prev, k
			}
		}
		prev = k
	}
	return -1, -1
}

func (l Leg) isShortWalk() bool {
	return l.Mode == "WALK" && (l.Distance == nil || *l.Distance < 400)
}

// earlierRun looks for a run of the same line that leaves the stop soon after you arrive and reaches your stop.
func (a *App) earlierRun(ctx context.Context, first, second Leg) *Leg {
	at := arrTime(first.To)
	limit := depTime(second.From)
	if at.IsZero() || limit.IsZero() || first.To.StopID == "" {
		return nil
	}
	from := at.Add(-sameLineLookback).Truncate(time.Minute)
	rows, err := cached(a.cache, fmt.Sprint("st|", first.To.StopID, false, from.Unix()), 45*time.Second, func() ([]Row, error) {
		var raw motisStopTimes
		err := a.motis.get(ctx, "/api/v5/stoptimes", url.Values{
			"stopId": {first.To.StopID}, "time": {from.UTC().Format(time.RFC3339)}, "arriveBy": {"false"},
			"n": {"40"}, "window": {strconv.Itoa(int(sameLineWindow.Seconds()))},
		}, &raw)
		return NormalizeStopTimes(&raw, false), err
	})
	if err != nil {
		log.Printf("same line %s: %v", first.To.StopID, err)
		return nil
	}
	tries := 0
	for _, r := range rows {
		t, err := time.Parse(time.RFC3339, r.ScheduledTime)
		if err != nil || !t.Before(limit) || r.Cancelled || r.TripID == "" || r.TripID == second.TripID {
			continue
		}
		if normLine(r.Line) != normLine(second.Line) {
			continue
		}
		if tries++; tries > 4 {
			break
		}
		run, err := a.tripLeg(ctx, r.TripID)
		if err != nil {
			continue
		}
		if l := cutRun(run, first.To, second.To, t); l != nil {
			l.StayOnBoard = "same-line"
			if r.TripID == first.TripID {
				l.StayOnBoard = "same-vehicle"
			}
			return l
		}
	}
	return nil
}

// cutRun takes the part of a vehicle's run from [from] (departing at [dep]) to [to].
func cutRun(run Leg, from, to Place, dep time.Time) *Leg {
	all := append(append([]Place{run.From}, run.Stops...), run.To)
	start := -1
	for k, p := range all {
		if start < 0 && sameStop(p, from) && math.Abs(depTime(p).Sub(dep).Minutes()) < 1.5 {
			start = k
			continue
		}
		if start >= 0 && sameStop(p, to) {
			l := run
			l.From, l.To = all[start], p
			l.Stops = append([]Place{}, all[start+1:k]...)
			l.Geometry = trimGeometry(run.Geometry, l.From, l.To)
			a, d := arrTime(l.To), depTime(l.From)
			l.Duration = int(a.Sub(d).Seconds())
			return &l
		}
	}
	return nil
}

// trimGeometry cuts a run's polyline down to the part between two stops.
func trimGeometry(g *struct {
	Points    string `json:"points"`
	Precision int    `json:"precision"`
}, from, to Place) *struct {
	Points    string `json:"points"`
	Precision int    `json:"precision"`
} {
	if g == nil {
		return nil
	}
	pts := decodePolyline(g.Points, g.Precision)
	if len(pts) < 2 {
		return g
	}
	nearest := func(p Place, after int) int {
		best, bestD := after, math.MaxFloat64
		for k := after; k < len(pts); k++ {
			if d := distM(p.Lat, p.Lon, pts[k][0], pts[k][1]); d < bestD {
				best, bestD = k, d
			}
		}
		return best
	}
	s := nearest(from, 0)
	e := nearest(to, s)
	if e <= s {
		return g
	}
	return &struct {
		Points    string `json:"points"`
		Precision int    `json:"precision"`
	}{encodePolyline(pts[s:e+1], g.Precision), g.Precision}
}

func encodePolyline(pts [][2]float64, precision int) string {
	factor := math.Pow10(precision)
	var b strings.Builder
	enc := func(v int64) {
		v <<= 1
		if v < 0 {
			v = ^v
		}
		for v >= 0x20 {
			b.WriteByte(byte((0x20 | (v & 0x1f)) + 63))
			v >>= 5
		}
		b.WriteByte(byte(v + 63))
	}
	var pLat, pLon int64
	for _, p := range pts {
		lat, lon := int64(math.Round(p[0]*factor)), int64(math.Round(p[1]*factor))
		enc(lat - pLat)
		enc(lon - pLon)
		pLat, pLon = lat, lon
	}
	return b.String()
}
