package main

import (
	"encoding/json"
	"math"
	"reflect"
	"strings"
	"testing"
)

func TestStationKey(t *testing.T) {
	for in, want := range map[string]string{
		"de-DELFI_de:08222:2417:5:5": "de:08222:2417",
		"de-VBN_000008000128":        "8000128",
		"de-VBN_000000702592":        "",
		"":                           "",
	} {
		if got := StationKey(in); got != want {
			t.Errorf("StationKey(%q) = %q, want %q", in, got, want)
		}
	}
}

const stopPlaceXML = `<StopPlace id="dhid:de:03152:33817:EdB"><keyList><KeyValue><Key>EVA</Key><Value>8000128</Value></KeyValue></keyList>
<privateCodes><PrivateCode type="https://daten.bahnhof.de/namespace/stada/">2218</PrivateCode></privateCodes>
<Name lang="de">Göttingen<Text lang="de">Göttingen</Text></Name><Street lang="de">Bahnhofsplatz 1</Street><Town lang="de">Göttingen</Town><PostCode>37073</PostCode>
<MobilityImpairedAccess>partial</MobilityImpairedAccess><StepFreeAccess>true</StepFreeAccess>
<ParentSiteRef ref="dhid:de:03152:33817" versionRef="any"/>
<EquipmentPlace id="x"><Centroid><Location><Longitude>9.92</Longitude><Latitude>51.53</Latitude></Location></Centroid><placeEquipments><LiftEquipmentRef ref="diid:lift1"/></placeEquipments></EquipmentPlace>
<LiftEquipment version="1" id="diid:lift1"><privateCodes><PrivateCode type="https://daten.bahnhof.de/namespace/eq/">10779738</PrivateCode></privateCodes><Name lang="de">zu Gleis 4/5</Name><WheelchairPassable>true</WheelchairPassable></LiftEquipment>
<SanitaryEquipment version="0" id="diid:wc"><Name lang="de">Toilette</Name><SanitaryFacilityList>toilet</SanitaryFacilityList></SanitaryEquipment>
<quays><Quay version="1" id="dhid:p4"><Name lang="de">Bahnsteig Gleis 4/5</Name><QuayType>railIslandPlatform</QuayType><PlatformHeight>0.76</PlatformHeight><Length>400.00</Length></Quay>
<Quay version="1" id="dhid:t4"><Name lang="de">4</Name><QuayType>railPlatform</QuayType><ParentQuayRef ref="dhid:p4"/></Quay></quays></StopPlace>`

func TestParseStopPlace(t *testing.T) {
	st := ParseStopPlace(stopPlaceXML)
	if st.DHID != "de:03152:33817" || st.Stada != "2218" || st.Name != "Göttingen" || !reflect.DeepEqual(st.EVAs, []string{"8000128"}) {
		t.Fatalf("ids: %+v", st)
	}
	if st.Address != "Bahnhofsplatz 1, 37073 Göttingen" || st.Accessibility.Level != "partial" {
		t.Errorf("address/access: %q %+v", st.Address, st.Accessibility)
	}
	if len(st.Platforms) != 1 || *st.Platforms[0].HeightCm != 76 || !reflect.DeepEqual(st.Platforms[0].Tracks, []string{"4"}) {
		t.Errorf("platforms: %+v", st.Platforms)
	}
	lift := st.Equipment[0]
	if lift.Type != "lift" || lift.EquipmentNumber != "10779738" || !lift.Wheelchair || lift.Lat == nil || *lift.Lat != 51.53 {
		t.Errorf("lift: %+v", lift)
	}
	if st.Equipment[1].Type != "toilet" {
		t.Errorf("toilet: %+v", st.Equipment[1])
	}
}

func TestParseNetexStreaming(t *testing.T) {
	doc := "<PublicationDelivery>" + strings.Repeat(stopPlaceXML, 3) + "</PublicationDelivery>"
	n := 0
	if err := ParseNetex(strings.NewReader(doc), func(*Station) { n++ }); err != nil || n != 3 {
		t.Fatalf("got %d stations, err %v", n, err)
	}
}

func TestDBBoardAndTripLinking(t *testing.T) {
	var raw DBBoard
	json.Unmarshal([]byte(`{
	 "stopPlaces":[{"evaNumber":"1","localizedNames":[{"language":"deu","name":"Kassel"}]},{"evaNumber":"2","localizedNames":[{"language":"deu","name":"Fulda"}]}],
	 "messages":{"attributes":[{"messageID":1,"descriptions":[{"language":"deu","text":"Fahrrad"}]}],"notes":[{"messageID":2,"descriptions":[{"language":"deu","text":"Bauarbeiten"}]}]},
	 "departures":[{"journeyId":"j","time":"2026-11-23T08:05:00+01:00","timeSchedule":"2026-11-23T08:00:00+01:00","timeType":"PREVIEW",
	   "platform":{"name":"9"},"platformSchedule":{"name":"10"},"messages":[1,2],
	   "transport":{"type":"HIGH_SPEED_TRAIN","journeyDescription":"ICE 575","category":"ICE","journeyNumber":575,
	     "direction":{"stopPlaces":[{"evaNumber":"1"}]},"via":[{"displayPriority":1,"evaNumber":"2"}]}},
	  {"journeyId":"k","time":"2026-11-23T09:00:00+01:00","timeSchedule":"2026-11-23T09:00:00+01:00","timeType":"SCHEDULE","canceled":true,"transport":{"type":"REGIONAL_TRAIN","line":{"name":"RE2"}}}]}`), &raw)
	rows := NormalizeDBBoard(&raw, false)
	r := rows[0]
	if r.Headsign != "Kassel" || !reflect.DeepEqual(r.Via, []string{"Fulda"}) || r.Delay == nil || *r.Delay != 5 || r.Mode != "HIGHSPEED_RAIL" {
		t.Errorf("row: %+v", r)
	}
	if !reflect.DeepEqual(r.Notes, []string{"Bauarbeiten"}) || !reflect.DeepEqual(r.Attributes, []string{"Fahrrad"}) || r.Cancelled {
		t.Errorf("notes: %+v", r)
	}
	if !rows[1].Cancelled || rows[1].Delay != nil || rows[1].Line != "RE2" {
		t.Errorf("cancelled row: %+v", rows[1])
	}

	var st motisStopTimes
	json.Unmarshal([]byte(`{"stopTimes":[{"tripId":"t1","displayName":"ICE 575","tripShortName":"575","mode":"HIGHSPEED_RAIL",
	  "place":{"departure":"2026-11-23T07:00:00Z","scheduledDeparture":"2026-11-23T07:00:00Z"}}]}`), &st)
	LinkTrips(rows, NormalizeStopTimes(&st, false))
	if rows[0].TripID != "t1" {
		t.Errorf("trip not linked: %+v", rows[0])
	}
}

func TestCacheServesStaleOnError(t *testing.T) {
	c := NewCache(10)
	v, _ := cached(c, "k", 0, func() (int, error) { return 1, nil })
	v, err := cached(c, "k", 0, func() (int, error) { return 0, errQuota })
	if v != 1 || err != nil {
		t.Fatalf("want stale 1, got %d %v", v, err)
	}
}

func TestRefreshLegTakesLiveTimes(t *testing.T) {
	planned := Leg{TripID: "t", From: Place{StopID: "a", ScheduledDeparture: "08:00", Departure: "08:00", Platform: "1"},
		To: Place{StopID: "c", ScheduledArrival: "09:00", Arrival: "09:00"}, Stops: []Place{{StopID: "b"}}}
	live := Leg{Realtime: true, From: Place{StopID: "a", Departure: "08:05", Platform: "2"},
		Stops: []Place{{StopID: "b", Arrival: "08:30", Cancelled: true}}, To: Place{StopID: "c", Arrival: "09:07"}}
	got := RefreshLeg(planned, live)
	if got.From.Departure != "08:05" || got.From.Platform != "2" || got.From.ScheduledDeparture != "08:00" ||
		got.To.Arrival != "09:07" || !got.Stops[0].Cancelled || !got.Realtime {
		t.Fatalf("not refreshed: %+v", got)
	}
}

func TestSharesRoundTripAndIDGuard(t *testing.T) {
	s := &Shares{dir: t.TempDir()}
	id, err := s.Save(Itinerary{Start: "x", Legs: []Leg{{Mode: "BUS"}}})
	if err != nil || !reShareID.MatchString(id) {
		t.Fatalf("save: %q %v", id, err)
	}
	it, err := s.Load(id)
	if err != nil || it.Start != "x" {
		t.Fatalf("load: %+v %v", it, err)
	}
	if _, err := s.Load("../../etc/passwd"); err == nil {
		t.Fatal("path traversal not rejected")
	}
}

func TestAssistantDailyBudget(t *testing.T) {
	as := &Assistant{DailyLimit: 2}
	if !as.take() || !as.take() || as.take() {
		t.Fatal("daily limit not enforced")
	}
}

func TestNearGermany(t *testing.T) {
	got := nearGermany([]Location{{Name: "SF", Lat: 37.7, Lon: -122.4}, {Name: "Göttingen", Lat: 51.53, Lon: 9.93}})
	if len(got) != 1 || got[0].Name != "Göttingen" {
		t.Fatalf("got %+v", got)
	}
}

func TestBuildSteps(t *testing.T) {
	// north along a footpath, then a right turn (east) onto Goßlerstraße, then stairs
	enc := func(pts ...[2]float64) string { return encodePolyline(pts, 6) }
	raw := []motisStep{
		{RelativeDirection: "CONTINUE", Distance: 50},
		{RelativeDirection: "CONTINUE", Distance: 40},
		{RelativeDirection: "CONTINUE", Distance: 80, StreetName: "Goßlerstraße"},
		{RelativeDirection: "STAIRS", Distance: 5},
	}
	raw[0].Polyline.Points, raw[0].Polyline.Precision = enc([2]float64{51.5, 9.9}, [2]float64{51.5005, 9.9}), 6
	raw[1].Polyline.Points, raw[1].Polyline.Precision = enc([2]float64{51.5005, 9.9}, [2]float64{51.5009, 9.9}), 6
	raw[2].Polyline.Points, raw[2].Polyline.Precision = enc([2]float64{51.5009, 9.9}, [2]float64{51.5009, 9.901}), 6
	got := BuildSteps(raw)
	want := []Step{{"START", "", 90}, {"RIGHT", "Goßlerstraße", 80}, {"STAIRS", "", 5}}
	if len(got) != len(want) {
		t.Fatalf("got %+v", got)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("step %d: got %+v want %+v", i, got[i], want[i])
		}
	}
}

func encodePolyline(pts [][2]float64, precision int) string {
	f := math.Pow(10, float64(precision))
	var b []byte
	put := func(v int) {
		u := v << 1
		if v < 0 {
			u = ^u
		}
		for u >= 0x20 {
			b = append(b, byte((0x20|(u&0x1f))+63))
			u >>= 5
		}
		b = append(b, byte(u+63))
	}
	pl, po := 0, 0
	for _, p := range pts {
		la, lo := int(math.Round(p[0]*f)), int(math.Round(p[1]*f))
		put(la - pl)
		put(lo - po)
		pl, po = la, lo
	}
	return string(b)
}
