package main

import (
	"context"
	"testing"
	"time"
)

func ts(hm string) string { return "2026-11-23T" + hm + ":00+01:00" }

func stop(id, name, arr, dep string) Place {
	return Place{Name: name, StopID: id, Lat: 51.5, Lon: 9.9, ScheduledArrival: arr, ScheduledDeparture: dep, Arrival: arr, Departure: dep}
}

func TestFindSameLinePair(t *testing.T) {
	a := Leg{Mode: "BUS", Line: "11", Operator: "GöVB", TripID: "t1", From: stop("s1", "Start", "", ts("10:00")), To: stop("end", "Endhalt", ts("10:20"), "")}
	walk := Leg{Mode: "WALK"}
	b := Leg{Mode: "BUS", Line: "11", Operator: "GöVB", TripID: "t2", From: stop("end", "Endhalt", "", ts("11:20")), To: stop("s9", "Ziel", ts("11:30"), "")}
	if i, j := findSameLinePair([]Leg{a, walk, b}); i != 0 || j != 2 {
		t.Fatalf("got %d %d", i, j)
	}
	b.From = stop("end", "Endhalt", "", ts("10:23")) // short wait is fine
	if i, _ := findSameLinePair([]Leg{a, b}); i != -1 {
		t.Fatal("short wait should not count")
	}
	b.From = stop("end", "Endhalt", "", ts("11:20"))
	b.Line = "12"
	if i, _ := findSameLinePair([]Leg{a, b}); i != -1 {
		t.Fatal("other line should not count")
	}
}

func TestFixPassesDestination(t *testing.T) {
	// the first ride already stops at "Ziel": stay on, no hour at the end stop
	a := Leg{Mode: "BUS", Line: "11", TripID: "t1", From: stop("s1", "Start", "", ts("10:00")), To: stop("end", "Endhalt", ts("10:20"), ""),
		Stops: []Place{stop("s5", "Ziel", ts("10:10"), ts("10:10")), stop("s6", "Weiter", ts("10:15"), ts("10:15"))}}
	b := Leg{Mode: "BUS", Line: "11", TripID: "t2", From: stop("end", "Endhalt", "", ts("11:20")), To: stop("s5", "Ziel", ts("11:30"), "")}
	it := Itinerary{Start: ts("10:00"), End: ts("11:30"), Transfers: 1, Legs: []Leg{a, b}}
	budget := 0
	got, ok := (&App{}).fixItinerary(context.Background(), it, &budget)
	if !ok || len(got.Legs) != 1 || got.Legs[0].To.Name != "Ziel" || got.Legs[0].StayOnBoard != "passes" {
		t.Fatalf("not fixed: %+v", got)
	}
	if got.End != ts("10:10") || got.Transfers != 0 || got.Duration != 600 || len(got.Legs[0].Stops) != 0 {
		t.Fatalf("times: %s %d %d", got.End, got.Transfers, got.Duration)
	}
}

func TestCutRun(t *testing.T) {
	run := Leg{Mode: "BUS", Line: "11", TripID: "t3", From: stop("end", "Endhalt", "", ts("10:22")),
		Stops: []Place{stop("x", "Mitte", ts("10:25"), ts("10:25"))}, To: stop("s9", "Ziel", ts("10:31"), "")}
	l := cutRun(run, stop("end2", "Endhalt", ts("10:20"), ""), stop("s9", "Ziel", "", ""), mustTime(ts("10:22")))
	if l == nil || l.To.Name != "Ziel" || len(l.Stops) != 1 || l.Duration != 540 {
		t.Fatalf("cut: %+v", l)
	}
}

func mustTime(s string) time.Time { t, _ := time.Parse(time.RFC3339, s); return t }

func TestPolylineRoundTrip(t *testing.T) {
	pts := [][2]float64{{51.5, 9.9}, {51.501, 9.902}, {51.503, 9.905}}
	got := decodePolyline(encodePolyline(pts, 6), 6)
	if len(got) != 3 || got[2] != pts[2] {
		t.Fatalf("%v", got)
	}
}

func TestDelayStats(t *testing.T) {
	d := &Delays{hists: map[string]*hist{}, pending: map[string]*pendingObs{}}
	base := time.Now().Add(-2 * time.Hour)
	for i := 0; i < 20; i++ {
		delay := 1
		if i%5 == 0 {
			delay = 8
		}
		sched := base.Add(time.Duration(i) * time.Minute).Format(time.RFC3339)
		d.Observe("trip", "Bus 11", "GöVB", "de-DELFI_de:03159:1:2:3", sched, &delay, false)
		d.Observe("trip", "Bus 11", "GöVB", "de-DELFI_de:03159:1:2:3", sched, &delay, false) // seen twice, counted once
	}
	d.commit(time.Now())
	s := d.Predict("BUS 11", "GöVB", "de-DELFI_de:03159:1:9:9", base.Format(time.RFC3339)) // other platform, same station
	if s == nil || s.Samples != 20 || s.Median != 1 || s.Likely != 1 || s.OnTime != 80 || s.Scope != "stop" {
		t.Fatalf("%+v", s)
	}
	if p := d.Predict("11", "", "x", ""); p != nil {
		t.Fatal("line without operator must not match")
	}
	r := d.Report("Bus 11", "GöVB", "")
	if r.Overall == nil || len(r.Histogram) != 6 || r.Histogram[0] != 80 || r.Histogram[2] != 20 {
		t.Fatalf("%+v", r)
	}
}

func TestMergeSearch(t *testing.T) {
	motis := []Location{{Name: "Siekweg", Type: "STOP", Area: "Göttingen", Lat: 51.52, Lon: 9.88}}
	photon := []Location{{Name: "McDonald's & McCafé", Type: "PLACE", Area: "Siekweg 30, Göttingen", Lat: 51.5276, Lon: 9.8899}}
	got := mergeSearch("McDonalds Siekweg", motis, photon)
	if len(got) != 2 || got[0].Type != "PLACE" {
		t.Fatalf("%+v", got)
	}
	if fold("Göttingen McDonald's") != "gottingen mcdonalds" {
		t.Fatal(fold("Göttingen McDonald's"))
	}
}

func TestPhotoScore(t *testing.T) {
	if s := photoScore("Linie VSN 120, 1, Hann. Münden, Landkreis Göttingen", "120", "VSN", "", "Bus"); s < 3 {
		t.Fatalf("score %d", s)
	}
	if s := photoScore("Strassenbahn bremen", "120", "VSN", "", "Bus"); s != 0 {
		t.Fatalf("score %d", s)
	}
}
