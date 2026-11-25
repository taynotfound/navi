package main

import (
	"net/http/httptest"
	"strings"
	"testing"
)

// The app leaves out "precision" (its default); the line must still come back as precision 6, not 0.
func TestReadItineraryDefaultsPrecision(t *testing.T) {
	body := `{"start":"","end":"","duration":0,"transfers":0,"legs":[{"mode":"BUS","from":{"name":"A","lat":51,"lon":9},"to":{"name":"B","lat":51,"lon":9},"geometry":{"points":"abc"}},{"mode":"WALK","from":{"name":"B","lat":51,"lon":9},"to":{"name":"C","lat":51,"lon":9},"geometry":{"points":"abc","precision":5}}]}`
	it, err := readItinerary(httptest.NewRequest("POST", "/v1/live", strings.NewReader(body)))
	if err != nil {
		t.Fatal(err)
	}
	if p := it.Legs[0].Geometry.Precision; p != 6 {
		t.Fatalf("missing precision read as %d, want 6", p)
	}
	if p := it.Legs[1].Geometry.Precision; p != 5 {
		t.Fatalf("explicit precision changed to %d", p)
	}
}
