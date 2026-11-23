# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

The Android app (Jetpack Compose, Material 3 Expressive) is the product; the website (static, GitHub Pages) presents it and renders shared live trips in the browser.

## Stack

- App: Kotlin, Jetpack Compose, Material 3 1.5 (Expressive), MapLibre with OpenFreeMap tiles. No Google Play Services.
- Website: plain static HTML/CSS/JS on GitHub Pages, no framework, no build step, no third-party requests except the Navi API.
- API: Go server at `https://navi-api.taymaerz.de`.

## Users

Everyone who rides public transport in Germany:

- **Everyday commuters** checking the next bus or train in their region (e.g. VSN, VSB, city networks).
- **Step-free travellers** – wheelchair users, people with prams or heavy luggage – who need working lifts and stair-free routes.
- **Privacy- and FOSS-minded people** who avoid the official apps because of tracking, ads and accounts.
- **Occasional long-distance travellers** planning a trip across Germany.

The app is meant to be idiot-proof: someone in a hurry on a platform must understand it at a glance.

## Product Purpose

Plan journeys and read live departures for every train, bus and tram in Germany, follow a trip live with a plain "what to do now" line, check lifts before arriving, and share a trip as a link others can follow in any browser.

## Positioning

All of these together, which no single competitor offers:

- Free and open source (AGPL-3.0), no ads, no account, no tracking.
- Live trip sharing: one link, the recipient follows the journey live without the app.
- Accessibility built in: live lift/escalator status (DB FaSta), step-free routing, station plans.
- Simplest to use: one clear instruction instead of dense timetables.
- Covers every network (DB long-distance and regional plus all local transport via DELFI/MOTIS).

## Operating Context

Used on the move: on platforms, at bus stops, while changing trains, often one-handed, in sunlight or at night, sometimes with poor connectivity. Shared-trip pages are opened by friends/family on any phone or desktop.

## Capabilities and Constraints

- Journey planning (stops, addresses, current location), earlier/later, step-free and bike options.
- Live departure/arrival boards with delays, platform changes, cancellations, operator notices.
- Live trips: saved/open itineraries refresh every 30 s; status card says what to do next.
- Saved trips and favourite stations; first-launch setup; English and German; dark by default, light and system themes.
- Station info: lifts/escalators with live status, platforms, toilets, entrances, on a map.
- "Frag Navi" assistant: free OpenRouter models only, answers only about public transport in Germany and the Navi app (enforced server-side), grounded in Navi's live data tools.
- Share links expire after 7 days.
- Distribution: APK on GitHub Releases, installable via Obtainium. F-Droid is planned, not available yet.
- Not affiliated with Deutsche Bahn; must not use DB branding.

## Brand Commitments

- Name: **Navi**.
- Logo: train above a teal-to-orange infinity route ending in a location pin, on deep navy (`server/web/logo.png`, `android/app/src/main/res/drawable-nodpi/logo.png`).
- Colours: M3 tonal palette generated from the logo seeds (teal route `#1FA39A`, coral pin `#F0603F`), dark-first.
- Material 3 Expressive is the design direction (owner's requirement).
- Bilingual: German and English.
- Support link: https://ko-fi.com/taygotkofi
- One-person project by taynotfound; human-directed, AI-assisted (see AI-DECLARATION.md).

## Evidence on Hand

- Real, working product: live API, app builds, shared-trip pages.
- Logo assets as above.
- No user counts, ratings, testimonials, press or store listings exist. Do not invent any.

## Product Principles

1. Glanceable first: the next action must be readable in one second.
2. Honest data: show live vs. scheduled clearly, never hide a cancellation.
3. Private by default: nothing leaves the device unless needed for the request.
4. Nobody left out: step-free and screen-reader use are first-class.
5. Free forever: no ads, no paywalls; support is voluntary.

## Accessibility & Inclusion

WCAG 2.2 AA as the floor. Large touch targets, full TalkBack/screen-reader labels, sufficient contrast in sunlight and at night, reduced-motion respected, no information by colour alone (delay text, not just red).
