package main

import "math"

// Step is one walking instruction: "turn left onto Goßlerstraße, 120 m".
type Step struct {
	Kind     string `json:"kind"` // START | STRAIGHT | LEFT | RIGHT | SLIGHT_LEFT | SLIGHT_RIGHT | STAIRS | ELEVATOR | ESCALATOR
	Street   string `json:"street,omitempty"`
	Distance int    `json:"distance"`
}

type motisStep struct {
	RelativeDirection string  `json:"relativeDirection"`
	Distance          float64 `json:"distance"`
	StreetName        string  `json:"streetName"`
	Polyline          struct {
		Points    string `json:"points"`
		Precision int    `json:"precision"`
	} `json:"polyline"`
}

// decodePolyline decodes Google's encoded polyline format at the given precision.
func decodePolyline(s string, precision int) [][2]float64 {
	f := math.Pow(10, float64(precision))
	var out [][2]float64
	lat, lon, i := 0, 0, 0
	next := func() (int, bool) {
		res, shift := 0, 0
		for i < len(s) {
			b := int(s[i]) - 63
			i++
			res |= (b & 0x1f) << shift
			shift += 5
			if b < 0x20 {
				if res&1 != 0 {
					return ^(res >> 1), true
				}
				return res >> 1, true
			}
		}
		return 0, false
	}
	for i < len(s) {
		dlat, ok1 := next()
		dlon, ok2 := next()
		if !ok1 || !ok2 {
			break
		}
		lat += dlat
		lon += dlon
		out = append(out, [2]float64{float64(lat) / f, float64(lon) / f})
	}
	return out
}

// bearing in degrees clockwise from north.
func bearing(a, b [2]float64) float64 {
	dx := (b[1] - a[1]) * math.Cos(a[0]*math.Pi/180)
	dy := b[0] - a[0]
	return math.Atan2(dx, dy) * 180 / math.Pi
}

func turnAngle(from, to float64) float64 {
	d := math.Mod(to-from+540, 360) - 180
	return d // positive = right
}

// BuildSteps turns MOTIS's per-way segments into a short list of instructions. Transitous only sends
// CONTINUE, so turns are worked out from the geometry: a new instruction starts where the street
// changes or the path bends by more than 50°.
func BuildSteps(raw []motisStep) []Step {
	var out []Step
	var lastBearing float64
	haveBearing := false
	for _, r := range raw {
		pts := decodePolyline(r.Polyline.Points, r.Polyline.Precision)
		var first, last float64
		hasGeo := len(pts) >= 2
		if hasGeo {
			first = bearing(pts[0], pts[1])
			last = bearing(pts[len(pts)-2], pts[len(pts)-1])
		}
		kind := ""
		switch r.RelativeDirection {
		case "STAIRS", "ELEVATOR", "ESCALATOR":
			kind = r.RelativeDirection
		case "LEFT", "HARD_LEFT", "UTURN_LEFT":
			kind = "LEFT"
		case "RIGHT", "HARD_RIGHT", "UTURN_RIGHT":
			kind = "RIGHT"
		case "SLIGHTLY_LEFT":
			kind = "SLIGHT_LEFT"
		case "SLIGHTLY_RIGHT":
			kind = "SLIGHT_RIGHT"
		}
		if kind == "" && hasGeo && haveBearing {
			switch a := turnAngle(lastBearing, first); {
			case a <= -100 || a >= 100:
				if a < 0 {
					kind = "LEFT"
				} else {
					kind = "RIGHT"
				}
			case a <= -50:
				kind = "LEFT"
			case a >= 50:
				kind = "RIGHT"
			}
		}
		n := len(out)
		special := kind == "STAIRS" || kind == "ELEVATOR" || kind == "ESCALATOR"
		sameStreet := n > 0 && (r.StreetName == "" || r.StreetName == out[n-1].Street)
		switch {
		case n == 0:
			out = append(out, Step{Kind: "START", Street: r.StreetName, Distance: int(r.Distance + .5)})
		case !special && kind == "" && sameStreet && !isSpecial(out[n-1].Kind):
			out[n-1].Distance += int(r.Distance + .5)
			if out[n-1].Street == "" {
				out[n-1].Street = r.StreetName
			}
		default:
			if kind == "" {
				kind = "STRAIGHT"
			}
			out = append(out, Step{Kind: kind, Street: r.StreetName, Distance: int(r.Distance + .5)})
		}
		if hasGeo {
			lastBearing, haveBearing = last, true
		}
	}
	// fold tiny unnamed wiggles into the step before them
	clean := out[:0]
	for _, s := range out {
		if len(clean) > 0 && s.Distance < 25 && s.Street == "" && !isSpecial(s.Kind) && !isSpecial(clean[len(clean)-1].Kind) {
			clean[len(clean)-1].Distance += s.Distance
			continue
		}
		clean = append(clean, s)
	}
	if len(clean) > 30 {
		clean = clean[:30]
	}
	return clean
}

func isSpecial(k string) bool { return k == "STAIRS" || k == "ELEVATOR" || k == "ESCALATOR" }
