package main

// Places: the MOTIS geocoder knows stops and addresses well, but shops, restaurants and other named
// places ("McDonalds Siekweg") hardly. Photon (OpenStreetMap, by komoot) finds those, so search asks both
// and ranks the hits by how well they match what was typed.

import (
	"context"
	"fmt"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"unicode"

	"encoding/json"
)

type photonResult struct {
	Features []struct {
		Geometry struct {
			Coordinates []float64 `json:"coordinates"` // lon, lat
		} `json:"geometry"`
		Properties struct {
			OsmKey      string `json:"osm_key"`
			OsmValue    string `json:"osm_value"`
			Type        string `json:"type"`
			Name        string `json:"name"`
			Street      string `json:"street"`
			HouseNumber string `json:"housenumber"`
			City        string `json:"city"`
			Locality    string `json:"locality"`
			District    string `json:"district"`
			CountryCode string `json:"countrycode"`
		} `json:"properties"`
	} `json:"features"`
}

// photon searches named places. Empty base disables it.
func (a *App) photon(ctx context.Context, text string, lat, lon *float64) ([]Location, error) {
	if a.photonURL == "" {
		return nil, nil
	}
	q := url.Values{"q": {text}, "lang": {"de"}, "limit": {"8"}, "bbox": {"4.5,45.5,17,56"}}
	if lat != nil && lon != nil {
		q.Set("lat", fmt.Sprintf("%.3f", *lat)) // rounded: a hint, not a position
		q.Set("lon", fmt.Sprintf("%.3f", *lon))
	}
	req, _ := http.NewRequestWithContext(ctx, "GET", a.photonURL+"/api/?"+q.Encode(), nil)
	req.Header.Set("User-Agent", a.motis.UserAgent)
	res, err := httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()
	if res.StatusCode != 200 {
		return nil, fmt.Errorf("photon: HTTP %d", res.StatusCode)
	}
	var raw photonResult
	if err := json.NewDecoder(res.Body).Decode(&raw); err != nil {
		return nil, err
	}
	out := []Location{}
	for _, f := range raw.Features {
		p := f.Properties
		if len(f.Geometry.Coordinates) < 2 || p.Name == "" && p.Street == "" {
			continue
		}
		// stops come from MOTIS with their real ids; Photon's copies would only be coordinates
		if p.OsmKey == "highway" && p.OsmValue == "bus_stop" || p.OsmKey == "railway" || p.OsmKey == "public_transport" {
			continue
		}
		l := Location{Lat: f.Geometry.Coordinates[1], Lon: f.Geometry.Coordinates[0], Modes: []string{}}
		l.ID = fmt.Sprintf("%g,%g", l.Lat, l.Lon)
		street := strings.TrimSpace(p.Street + " " + p.HouseNumber)
		city := p.City
		if city == "" {
			city = p.Locality
		}
		switch {
		case p.Name != "" && p.Type == "house" || p.Name != "" && p.OsmKey != "place" && p.OsmKey != "highway" && p.OsmKey != "boundary":
			l.Type, l.Name = "PLACE", p.Name
			l.Area = strings.Trim(strings.Join([]string{street, city}, ", "), ", ")
		case p.Name != "":
			l.Type, l.Name, l.Area = "ADDRESS", p.Name, city
		default:
			l.Type, l.Name, l.Area = "ADDRESS", street, city
		}
		out = append(out, l)
	}
	return out, nil
}

// fold lowercases and drops accents, apostrophes and punctuation: "McDonald's" ~ "mcdonalds", "Göttingen" ~ "gottingen".
func fold(s string) string {
	var b strings.Builder
	for _, r := range strings.ToLower(s) {
		switch r {
		case 'ä':
			r = 'a'
		case 'ö':
			r = 'o'
		case 'ü':
			r = 'u'
		case 'ß':
			b.WriteString("ss")
			continue
		case 'é', 'è', 'ê':
			r = 'e'
		}
		if unicode.IsLetter(r) || unicode.IsDigit(r) || r == ' ' {
			b.WriteRune(r)
		} else if r == '-' || r == ',' || r == '/' || r == '.' {
			b.WriteRune(' ')
		}
	}
	return b.String()
}

// matchScore is the share of typed words found in a hit's name and area (prefix match for the last word).
func matchScore(query string, l Location) float64 {
	words := strings.Fields(fold(query))
	if len(words) == 0 {
		return 0
	}
	hay := strings.Fields(fold(l.Name + " " + l.Area))
	n := 0.0
	for i, w := range words {
		for _, h := range hay {
			if h == w || (i == len(words)-1 || len(w) >= 4) && strings.HasPrefix(h, w) {
				n++
				break
			}
		}
	}
	return n / float64(len(words))
}

// mergeSearch puts both lists together, best matches first, stops before places on a tie, no duplicates.
func mergeSearch(query string, motis, photon []Location) []Location {
	type hit struct {
		l     Location
		score float64
		order int
	}
	var hits []hit
	add := func(l Location, order int) {
		for _, h := range hits {
			// the same thing twice; a town also comes back as a stop, an address and a place
			near := 80.0
			if l.Type != "STOP" {
				near = 3000
			}
			if fold(h.l.Name) == fold(l.Name) && distM(h.l.Lat, h.l.Lon, l.Lat, l.Lon) < near {
				return
			}
		}
		score := matchScore(query, l)
		if l.Type == "STOP" {
			score += .15 // it's a transit app: on a similar match, the stop wins
		}
		// "Weender Tor" beats "Zahnarztpraxis am Weender Tor": fewer extra words in the name
		extra := len(strings.Fields(fold(l.Name))) - len(strings.Fields(fold(query)))
		score -= .03 * float64(max(extra, 0))
		hits = append(hits, hit{l, score, order})
	}
	for i, l := range motis {
		add(l, i)
	}
	for i, l := range photon {
		add(l, i+100) // behind MOTIS hits on a tie
	}
	sort.SliceStable(hits, func(i, j int) bool {
		a, b := hits[i], hits[j]
		if a.score != b.score {
			return a.score > b.score
		}
		return a.order < b.order
	})
	out := make([]Location, 0, len(hits))
	for _, h := range hits {
		out = append(out, h.l)
	}
	return out
}
