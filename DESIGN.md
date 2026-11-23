# Design

<!-- Describes the built website world ("Die Haltestelle"). The app follows Material 3 Expressive with the same palette. -->

## World

The website is a German bus/tram stop you walk up to: a **pole** runs down the left edge and everything hangs on it; the **round stop disc** at the top carries the Navi mark; the **stop-name plate** says where you are; the **glass timetable case** shows what comes next, with live data. Further sections are more signs mounted on the same pole with small metal brackets. On the shared-trip page the pole *is* the route: it takes each line's colour while you ride it, passed stops dim, the next stop is lit in coral.

Refuses: app-landing template (headline + phone mockup + three equal feature cards), gradient text, emoji icons, eyebrow labels.

## Colour

M3 tonal palette generated with Material Color Utilities (SchemeTonalSpot, spec 2025) from the logo seeds: teal route `#1FA39A`, coral pin `#F0603F`. **Dark is the default** (the stop at night); light is opt-in via the theme button and remembered.

| Token | Dark | Light | Role |
|---|---|---|---|
| `--ground` | `#0a0f0e` | `#f6faf8` | page |
| `--surface` / `--surface-hi` | `#131b1a` / `#1d2826` | `#fff` / `#e1eae8` | panels, chips |
| `--ink` / `--ink-2` | `#dce8e5` / `#a2adab` | `#1c2625` / `#4d5957` | text |
| `--plate` / `--plate-ink` / `--plate-edge` | `#265b56` / `#b8eee7` / `#9cd1ca` | `#a8f0e8` / `#00403b` / `#1d6a64` | enamel sign plates |
| `--teal` / `--teal-ink` | `#9cd1ca` / `#0e4844` | `#1d6a64` / `#e1fffb` | primary action |
| `--live` | `#f9b7a7` | `#8c4e40` | "live", next stop, focus ring |
| `--live-plate` / `--live-plate-ink` | `#76463a` / `#ffdbd3` | `#fdad9b` / `#622d20` | "what to do now", platform change, support |
| `--ok` `--late` `--bad` | `#8bd8a0` `#f2c27a` `#ffa49a` | `#1b7a3a` `#8a5b00` `#b3261e` | punctuality (always with text) |

Line colours (route chips, pole on trip page) match the app's mode colours: long distance `#b0283a`, regional `#5b4ba8`, S-Bahn `#2e7d32`, U-Bahn `#1565c0`, tram `#c2410c`, bus `#7b1fa2`, ferry `#00838f`; an operator's own route colour wins when the API provides one.

## Type

**DINish** (OFL, a DIN 1451 model, self-hosted variable font `site/fonts/dinish.woff2`, weight 100–900, width 75–125%). One family for everything, like real road and station signs.

- Stop name: 800, width 80–82%, `clamp(84px, 17vw, 184px)`, line-height .84.
- Section signs: 800, width 85%, `clamp(36px, 5.4vw, 60px)`.
- Times and clock: 800, width 110%, tabular numerals.
- Body: 400–500, 18px / 1.5.

## Shape & material

- Plates: 28px radius, 3px inset edge in `--plate-edge`.
- Case: brushed-metal frame (`--metal` gradient) 10px, glass inside with a single diagonal sheen.
- Pole and brackets: `--metal`, flat with a soft offset shadow.
- Buttons: pills that morph to 18px radius on hover with a spring (`cubic-bezier(.34,1.4,.64,1)`), scale .97 when pressed – the M3 Expressive shape-morph, done in CSS.
- Connected stop chooser: outer pill ends, inner 6px, the selected one becomes a full pill.

## Motion

One authored moment: departure rows **flip in** (clip-path reveal, staggered 45 ms) when the board loads or the stop changes. Everything else is state transitions. `prefers-reduced-motion` turns all of it off.

## Browser surfaces

Selection in plate colours, caret in teal, scrollbar in `--line` on `--ground`, focus ring 3px `--live` with 3px offset, tabular numerals everywhere.

## Files

`site/style.css` (shared), `site/index.html` (landing), `site/trip/index.html` (shared trip). Data: `https://navi-api.taymaerz.de` (`/v1/board`, `/v1/station`, `/v1/share`).
