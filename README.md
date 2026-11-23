<p align="center"><img src="site/logo.png" width="120" alt=""></p>

# Navi

Bus and train app for Germany. Live departures, journeys, lifts at stations, and you can share a trip as a link. No ads, no login, no tracking.

[Website](https://taynotfound.github.io/navi/) · [Download the APK](https://github.com/taynotfound/navi/releases/latest) · [Buy me a coffee](https://ko-fi.com/taygotkofi)

I built this because the other apps annoyed me. It covers every train, tram and bus that's in the German open timetable data, so local networks like VSN or VSB work too.

## What's in here

```
android/   the app (Kotlin, Compose, Material 3 Expressive, MapLibre)
server/    the API the app talks to (Go, no dependencies)
site/      the website, served by GitHub Pages
```

The app only talks to the server. The server holds the DB API keys, caches everything so we stay under the DB quotas, and pulls the rest from these places:

- DB OpenBoards for live departures
- DB FaSta for lift and escalator status
- DB OpenStation for station data (downloaded once a day)
- Transitous / MOTIS for routing and all the local buses and trams
- OpenRouter (free models only) for "Frag Navi", the little assistant. It only talks about public transport and the app.

## Running the server

You need Go and a [DB API Marketplace](https://developers.deutschebahn.com) account with OpenBoards and FaSta.

```sh
cd server
cp .env.example .env   # put your DB keys in there
go run .
```

Or `docker compose up -d`. Everything you can set is in `server/.env.example`. The live one runs at https://navi-api.taymaerz.de.

Endpoints, all JSON: `/v1/search`, `/v1/nearby`, `/v1/board`, `/v1/plan`, `/v1/trip`, `/v1/station`, `POST /v1/live`, `POST /v1/share`, `/v1/share?id=`, `POST /v1/assistant`.

## Building the app

JDK 21 and the Android SDK with platform 37.1.

```sh
cd android
./gradlew assembleDebug -PnaviApi=http://10.0.2.2:8080
```

`naviApi` is the server the app uses (10.0.2.2 is your computer from inside the emulator).

Every push to main builds a signed APK. Tag `vX.Y.Z` and it becomes a GitHub release. The same workflow puts the website on Pages and the server on my Pelican panel.

## Website

Plain HTML and CSS in `site/`, no build step. Try it locally with `python3 -m http.server -d site 8091`. Some notes on the look are in [DESIGN.md](DESIGN.md).

## Thanks

Map data © OpenStreetMap contributors, tiles from OpenFreeMap. Timetables from DELFI via Transitous. Station and live data from Deutsche Bahn. Navi is not a DB product.

## License

AGPL-3.0, see [LICENSE](LICENSE). How AI was used is in [AI-DECLARATION.md](AI-DECLARATION.md).
