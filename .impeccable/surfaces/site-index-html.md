---
version: 1
slug: "site-index-html"
primary_target: "site/index.html"
related_targets: ["site/trip/index.html"]
---

# Surface brief: Navi website (landing + shared trip)

Mode: Persuade (landing), Operate (shared-trip page). Static, GitHub Pages; data from https://navi-api.taymaerz.de.

Audience: commuters, step-free travellers, FOSS/privacy people, occasional long-distance travellers in Germany.
Action: get the APK (GitHub Releases / Obtainium; F-Droid soon). Proof: a real live departure board in the first viewport.

## Direction contract

THESIS: The site is the passenger display above a train door: dark glass, amber text, one line with its stops, the next one ringed in blue. It refuses both the app-landing template and the literal bus-stop costume the previous version wore.

OWN-WORLD: Blue-graphite ground (#0c0e12), display glass panels (#161a1f) that stay dark in light mode, amber (#ebbf89) for what matters now, blue ring (#b0c8ec) for "next", line colours only on rides. Atkinson Hyperlegible Next throughout, tabular numbers. Route strips (dots on a 5px track) are the only ornament.

STORY: The visitor reads one plain promise, sees a real live board for a real stop, switches stops along a route strip, then reads tay's short note and the feature list drawn as a ride. Downloads the APK.

FIRST VIEWPORT: Promise headline, APK + Obtainium buttons, the display with live departures and the stop strip.

FORM: candidate 6 of 7 (in-train "Nächster Halt" passenger display). seed:d0550f69f3313fee. Raise from the split-flap challenger: columns never move, rows are the only thing that changes.

FINISH: reviewer and documenter not run yet in this pass.
