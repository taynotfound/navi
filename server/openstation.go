package main

// OpenStation (DB InfraGO, CC0): the full NeTEx dump from Mobilithek, parsed into a compact index.
// The Marketplace endpoint returns only 100 stations per call; the Mobilithek dump has all ~5.7k and needs no key.

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"log"
	"math"
	"net/http"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

const netexURL = "https://bahnhof.de/daten/netex"

type Station struct {
	DHID          string        `json:"dhid"`
	EVAs          []string      `json:"evas"`
	Stada         string        `json:"stada,omitempty"`
	Name          string        `json:"name"`
	Address       string        `json:"address,omitempty"`
	URL           string        `json:"url,omitempty"`
	Accessibility Accessibility `json:"accessibility"`
	Entrances     []string      `json:"entrances"`
	Platforms     []Platform    `json:"platforms"`
	Equipment     []Equipment   `json:"equipment"`
}

type Accessibility struct {
	Level           string `json:"level"` // true | partial | false | unknown
	StepFree        string `json:"stepFree"`
	Wheelchair      string `json:"wheelchair"`
	TactileGuidance string `json:"tactileGuidance"`
}

type Platform struct {
	Name     string   `json:"name"`
	HeightCm *int     `json:"heightCm"`
	LengthM  *float64 `json:"lengthM"`
	StepFree string   `json:"stepFree"`
	Tracks   []string `json:"tracks"`
}

type Equipment struct {
	Type            string   `json:"type"` // lift | escalator | travelator | ramp | toilet
	Name            string   `json:"name"`
	EquipmentNumber string   `json:"equipmentNumber,omitempty"` // == FaSta equipmentnumber
	Wheelchair      bool     `json:"wheelchair"`
	Lat             *float64 `json:"lat"`
	Lon             *float64 `json:"lon"`
	Status          string   `json:"status,omitempty"` // ACTIVE | INACTIVE | UNKNOWN (filled from FaSta)
	StatusText      string   `json:"statusText,omitempty"`
}

var (
	reStopID      = regexp.MustCompile(`<StopPlace [^>]*id="dhid:([^"]+)"`)
	reParent      = regexp.MustCompile(`<ParentSiteRef ref="dhid:([^"]+)"`)
	reEVA         = regexp.MustCompile(`<Key>EVA</Key>\s*<Value>(\d+)</Value>`)
	reStada       = regexp.MustCompile(`namespace/stada/">(\d+)<`)
	reEqNo        = regexp.MustCompile(`namespace/eq/">(\d+)<`)
	reEquipPlace  = regexp.MustCompile(`(?s)<EquipmentPlace [^>]*>(.*?)</EquipmentPlace>`)
	reEquipRef    = regexp.MustCompile(`EquipmentRef ref="([^"]+)"`)
	reQuay        = regexp.MustCompile(`(?s)<Quay [^>]*id="([^"]+)"[^>]*>(.*?)</Quay>`)
	reParentQuay  = regexp.MustCompile(`<ParentQuayRef ref="([^"]+)"`)
	reEntrance    = regexp.MustCompile(`(?s)<Entrance [^>]*>(.*?)</Entrance>`)
	tagRes        = map[string]*regexp.Regexp{}
	tagMu         sync.Mutex
	equipmentKind = map[string]string{
		"LiftEquipment": "lift", "EscalatorEquipment": "escalator", "TravelatorEquipment": "travelator",
		"RampEquipment": "ramp", "SanitaryEquipment": "toilet",
	}
	equipmentRes = func() map[string]*regexp.Regexp {
		m := map[string]*regexp.Regexp{}
		for t := range equipmentKind { // RE2 has no backreferences, so one regex per element type
			m[t] = regexp.MustCompile(`(?s)<` + t + ` [^>]*id="([^"]+)"[^>]*>(.*?)</` + t + `>`)
		}
		return m
	}()
)

// tag returns the text of the first <name …>text element.
func tag(name, s string) string {
	tagMu.Lock()
	re := tagRes[name]
	if re == nil {
		re = regexp.MustCompile(`<` + name + `(?:\s[^>]*)?>([^<]*)`)
		tagRes[name] = re
	}
	tagMu.Unlock()
	if m := re.FindStringSubmatch(s); m != nil {
		return strings.TrimSpace(m[1])
	}
	return ""
}

func first(re *regexp.Regexp, s string) string {
	if m := re.FindStringSubmatch(s); m != nil {
		return m[1]
	}
	return ""
}

func orUnknown(s string) string {
	if s == "" {
		return "unknown"
	}
	return s
}

func floatPtr(s string) *float64 {
	f, err := strconv.ParseFloat(s, 64)
	if err != nil {
		return nil
	}
	return &f
}

// ParseStopPlace turns one <StopPlace>…</StopPlace> chunk into a Station.
func ParseStopPlace(xml string) *Station {
	ownID := first(reStopID, xml)
	if ownID == "" {
		return nil
	}
	dhid := first(reParent, xml)
	if dhid == "" {
		dhid = strings.TrimSuffix(ownID, ":EdB")
	}
	st := &Station{
		DHID:      dhid,
		EVAs:      []string{},
		Stada:     first(reStada, xml),
		Name:      tag("Name", xml),
		URL:       tag("Url", xml),
		Entrances: []string{},
		Platforms: []Platform{},
		Equipment: []Equipment{},
		Accessibility: Accessibility{
			Level:           orUnknown(tag("MobilityImpairedAccess", xml)),
			StepFree:        orUnknown(tag("StepFreeAccess", xml)),
			Wheelchair:      orUnknown(tag("WheelchairAccess", xml)),
			TactileGuidance: orUnknown(tag("TactileGuidanceAvailable", xml)),
		},
	}
	for _, m := range reEVA.FindAllStringSubmatch(xml, -1) {
		st.EVAs = append(st.EVAs, m[1])
	}
	town := strings.TrimSpace(tag("PostCode", xml) + " " + tag("Town", xml))
	st.Address = strings.Trim(tag("Street", xml)+", "+town, ", ")

	// EquipmentPlace centroids give coordinates to the equipment they reference.
	coords := map[string][2]float64{}
	for _, m := range reEquipPlace.FindAllStringSubmatch(xml, -1) {
		lat, lon := floatPtr(tag("Latitude", m[1])), floatPtr(tag("Longitude", m[1]))
		if lat == nil || lon == nil {
			continue
		}
		for _, r := range reEquipRef.FindAllStringSubmatch(m[1], -1) {
			coords[r[1]] = [2]float64{*lat, *lon}
		}
	}
	for _, t := range []string{"LiftEquipment", "EscalatorEquipment", "TravelatorEquipment", "RampEquipment", "SanitaryEquipment"} {
		for _, m := range equipmentRes[t].FindAllStringSubmatch(xml, -1) {
			body := m[2]
			e := Equipment{Type: equipmentKind[t], Name: tag("Name", body), EquipmentNumber: first(reEqNo, body)}
			if t == "SanitaryEquipment" {
				e.Wheelchair = strings.Contains(body, "wheelchairAccessToilet")
			} else {
				e.Wheelchair = tag("WheelchairPassable", body) == "true"
			}
			if c, ok := coords[m[1]]; ok {
				e.Lat, e.Lon = &c[0], &c[1]
			}
			st.Equipment = append(st.Equipment, e)
		}
	}

	type quay struct{ id, name, typ, parent, stepFree, height, length string }
	var quays []quay
	for _, m := range reQuay.FindAllStringSubmatch(xml, -1) {
		b := m[2]
		quays = append(quays, quay{m[1], tag("Name", b), tag("QuayType", b), first(reParentQuay, b),
			tag("StepFreeAccess", b), tag("PlatformHeight", b), tag("Length", b)})
	}
	for _, q := range quays {
		if q.parent != "" || q.typ == "railPlatform" || q.typ == "railPlatformSector" {
			continue
		}
		p := Platform{Name: q.name, LengthM: floatPtr(q.length), StepFree: orUnknown(q.stepFree), Tracks: []string{}}
		if h := floatPtr(q.height); h != nil {
			cm := int(math.Round(*h * 100))
			p.HeightCm = &cm
		}
		for _, t := range quays {
			if t.parent == q.id && t.typ == "railPlatform" {
				p.Tracks = append(p.Tracks, t.name)
			}
		}
		st.Platforms = append(st.Platforms, p)
	}
	for _, m := range reEntrance.FindAllStringSubmatch(xml, -1) {
		if n := tag("Name", m[1]); n != "" {
			st.Entrances = append(st.Entrances, n)
		}
	}
	return st
}

// ParseNetex stream-splits a NeTEx document into StopPlace chunks without holding 300 MB in memory.
func ParseNetex(r io.Reader, onStation func(*Station)) error {
	const open, close = "<StopPlace ", "</StopPlace>"
	br := bufio.NewReaderSize(r, 1<<20)
	chunk := make([]byte, 1<<20)
	var buf strings.Builder
	pending := ""
	for {
		n, err := br.Read(chunk)
		buf.Reset()
		buf.WriteString(pending)
		buf.Write(chunk[:n])
		s := buf.String()
		for {
			end := strings.Index(s, close)
			if end < 0 {
				break
			}
			if start := strings.LastIndex(s[:end], open); start >= 0 {
				if st := ParseStopPlace(s[start : end+len(close)]); st != nil {
					onStation(st)
				}
			}
			s = s[end+len(close):]
		}
		// only the tail of an unfinished StopPlace can matter
		if i := strings.LastIndex(s, open); i >= 0 {
			pending = s[i:]
		} else {
			pending = ""
		}
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return err
		}
	}
}

type StationIndex struct {
	mu        sync.RWMutex
	byKey     map[string]*Station // DHID and every EVA
	count     int
	updatedAt time.Time
	file      string
}

func NewStationIndex(dataDir string) *StationIndex {
	return &StationIndex{byKey: map[string]*Station{}, file: dataDir + "/openstation.netex.xml"}
}

func (x *StationIndex) Get(key string) *Station {
	x.mu.RLock()
	defer x.mu.RUnlock()
	return x.byKey[key]
}

func (x *StationIndex) Stats() (int, time.Time) {
	x.mu.RLock()
	defer x.mu.RUnlock()
	return x.count, x.updatedAt
}

func (x *StationIndex) load() error {
	f, err := os.Open(x.file)
	if err != nil {
		return err
	}
	defer f.Close()
	byKey, count := map[string]*Station{}, 0
	if err := ParseNetex(f, func(st *Station) {
		count++
		byKey[st.DHID] = st
		for _, e := range st.EVAs {
			byKey[e] = st
		}
	}); err != nil {
		return err
	}
	fi, _ := f.Stat()
	x.mu.Lock()
	x.byKey, x.count, x.updatedAt = byKey, count, fi.ModTime()
	x.mu.Unlock()
	log.Printf("openstation: %d stations loaded", count)
	return nil
}

func (x *StationIndex) refresh(ctx context.Context) error {
	req, _ := http.NewRequestWithContext(ctx, "GET", netexURL, nil)
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode != 200 {
		return fmt.Errorf("openstation download: HTTP %d", res.StatusCode)
	}
	tmp := x.file + ".part"
	f, err := os.Create(tmp)
	if err != nil {
		return err
	}
	if _, err := io.Copy(f, res.Body); err != nil {
		f.Close()
		os.Remove(tmp)
		return err
	}
	f.Close()
	if err := os.Rename(tmp, x.file); err != nil { // atomic: a failed download never replaces good data
		return err
	}
	return x.load()
}

// Start loads from disk if present and refreshes daily in the background.
func (x *StationIndex) Start(ctx context.Context) {
	go func() {
		fi, err := os.Stat(x.file)
		if err == nil {
			if err := x.load(); err != nil {
				log.Printf("openstation load: %v", err)
			}
		}
		if err != nil || time.Since(fi.ModTime()) > 24*time.Hour {
			if err := x.refresh(ctx); err != nil {
				log.Printf("openstation refresh: %v", err)
			}
		}
		for range time.Tick(24 * time.Hour) {
			if err := x.refresh(ctx); err != nil {
				log.Printf("openstation refresh: %v", err)
			}
		}
	}()
}
