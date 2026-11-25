package main

// Spotter photos: pictures of the line's vehicles from Wikimedia Commons, with author and licence.
// Commons titles usually name operator, line and vehicle class ("Linie VSN 120, 1, Hann. Münden…",
// "DB 425 053 S-Bahn Hannover…"), so a few searches ranked by how many of those words match work well.
// It is a similar vehicle, not necessarily the one you'll ride.

import (
	"context"
	"encoding/json"
	"fmt"
	"html"
	"net/http"
	"net/url"
	"regexp"
	"sort"
	"strings"
	"time"
)

type Photo struct {
	Thumb   string `json:"thumb"` // ~640 px wide
	Page    string `json:"page"`  // the file page on Commons (credit + licence)
	Title   string `json:"title"`
	Author  string `json:"author"`
	License string `json:"license"`
}

type commonsResult struct {
	Query struct {
		Pages map[string]struct {
			Index     int    `json:"index"`
			Title     string `json:"title"`
			ImageInfo []struct {
				ThumbURL       string `json:"thumburl"`
				DescriptionURL string `json:"descriptionurl"`
				Mime           string `json:"mime"`
				ExtMetadata    map[string]struct {
					Value any `json:"value"`
				} `json:"extmetadata"`
			} `json:"imageinfo"`
		} `json:"pages"`
	} `json:"query"`
}

var reTags = regexp.MustCompile(`<[^>]+>`)

func plainText(v any) string {
	s, _ := v.(string)
	return strings.TrimSpace(html.UnescapeString(reTags.ReplaceAllString(s, "")))
}

func (a *App) commonsSearch(ctx context.Context, query string) ([]Photo, error) {
	q := url.Values{
		"action": {"query"}, "format": {"json"}, "generator": {"search"}, "gsrnamespace": {"6"},
		"gsrlimit": {"12"}, "gsrsearch": {query + " filetype:bitmap"},
		"prop": {"imageinfo"}, "iiprop": {"url|mime|extmetadata"}, "iiurlwidth": {"640"},
		"iiextmetadatafilter": {"Artist|LicenseShortName"},
	}
	req, _ := http.NewRequestWithContext(ctx, "GET", "https://commons.wikimedia.org/w/api.php?"+q.Encode(), nil)
	req.Header.Set("User-Agent", a.motis.UserAgent)
	res, err := httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()
	if res.StatusCode != 200 {
		return nil, fmt.Errorf("commons: HTTP %d", res.StatusCode)
	}
	var raw commonsResult
	if err := json.NewDecoder(res.Body).Decode(&raw); err != nil {
		return nil, err
	}
	type ranked struct {
		p     Photo
		index int
	}
	var list []ranked
	for _, p := range raw.Query.Pages {
		if len(p.ImageInfo) == 0 || p.ImageInfo[0].ThumbURL == "" {
			continue
		}
		ii := p.ImageInfo[0]
		if ii.Mime != "image/jpeg" && ii.Mime != "image/png" && ii.Mime != "image/webp" {
			continue
		}
		title := strings.TrimSuffix(strings.TrimPrefix(p.Title, "File:"), ".jpg")
		list = append(list, ranked{Photo{
			Thumb: ii.ThumbURL, Page: ii.DescriptionURL, Title: title,
			Author: plainText(ii.ExtMetadata["Artist"].Value), License: plainText(ii.ExtMetadata["LicenseShortName"].Value),
		}, p.Index})
	}
	sort.Slice(list, func(i, j int) bool { return list[i].index < list[j].index })
	out := make([]Photo, len(list))
	for i, r := range list {
		out[i] = r.p
	}
	return out, nil
}

var modeWords = map[string]string{
	"BUS": "Bus", "COACH": "Bus", "TRAM": "Straßenbahn", "SUBWAY": "U-Bahn", "SUBURBAN": "S-Bahn", "FERRY": "Fähre",
}

// photos finds up to 6 pictures for a line.
func (a *App) photos(ctx context.Context, q url.Values) (any, error) {
	line, err := param(q, "line")
	if err != nil {
		return nil, err
	}
	operator, mode, city := strings.TrimSpace(q.Get("operator")), q.Get("mode"), strings.TrimSpace(q.Get("city"))
	if len(operator) > 100 || len(city) > 100 {
		return nil, badRequest("invalid parameters")
	}
	key := strings.ToLower(fmt.Sprint("photos|", line, "|", operator, "|", mode, "|", city))
	return cached(a.cache, key, 24*time.Hour, func() ([]Photo, error) {
		lineWord := strings.TrimSpace(strings.TrimPrefix(strings.TrimPrefix(line, "Bus "), "STR "))
		short := shortOperator(operator)
		// most specific first; stop once enough good matches came up
		queries := []string{}
		if short != "" {
			queries = append(queries, fmt.Sprintf("%s %s %s", short, lineWord, modeWords[mode]))
		}
		if city != "" {
			queries = append(queries, fmt.Sprintf("Linie %s %s %s", lineWord, city, modeWords[mode]))
		}
		if railModes[mode] {
			queries = append(queries, strings.TrimSpace(fmt.Sprintf("%s %s", line, short)))
		}
		if short != "" {
			queries = append(queries, strings.TrimSpace(short+" "+modeWords[mode]))
		}
		seen := map[string]bool{}
		type scored struct {
			p     Photo
			score int
		}
		var all []scored
		for _, query := range queries {
			found, err := a.commonsSearch(ctx, query)
			if err != nil {
				if len(all) == 0 {
					return nil, err
				}
				break
			}
			for _, p := range found {
				if seen[p.Page] {
					continue
				}
				seen[p.Page] = true
				all = append(all, scored{p, photoScore(p.Title, lineWord, short, city, modeWords[mode])})
			}
			good := 0
			for _, s := range all {
				if s.score >= 3 {
					good++
				}
			}
			if good >= 4 {
				break
			}
		}
		sort.SliceStable(all, func(i, j int) bool { return all[i].score > all[j].score })
		out := []Photo{}
		for _, s := range all {
			if s.score < 2 || len(out) == 6 {
				break
			}
			out = append(out, s.p)
		}
		return out, nil
	})
}

// shortOperator turns "Göttinger Verkehrsbetriebe GmbH" into something people put in photo titles.
func shortOperator(op string) string {
	for _, suffix := range []string{" GmbH", " AG", " mbH", " KG", " & Co.", " e.V."} {
		op = strings.ReplaceAll(op, suffix, "")
	}
	switch strings.ToLower(op) {
	case "db regio", "db fernverkehr", "db regio ag", "deutsche bahn":
		return "DB"
	}
	return strings.TrimSpace(op)
}

func photoScore(title, line, operator, city, mode string) int {
	t := " " + fold(title) + " "
	score := 0
	if line != "" && (strings.Contains(t, " "+fold(line)+" ") || strings.Contains(t, " linie "+fold(line)+" ")) {
		score += 2
	}
	for _, w := range strings.Fields(fold(operator)) {
		if len(w) >= 3 && strings.Contains(t, w) {
			score++
			break
		}
	}
	if city != "" && strings.Contains(t, fold(city)) {
		score++
	}
	if mode != "" && strings.Contains(t, fold(mode)) {
		score++
	}
	return score
}
