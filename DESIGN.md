# Design notes

## The idea

Navi looks like the passenger display above a train door. Dark glass, amber text for what matters right now, and one line with its stops where the next stop has a blue ring. That's the whole system, in the app and on the website.

The logo is the same thing in small: an N drawn as a route, starting at an amber dot and ending at a blue ring. Source is `brand/logo.svg`.

## Colours

Generated with Material Color Utilities (TonalSpot, spec 2025). Neutrals come from a blue-graphite seed `#3D6FA8`, the primary from amber `#E9A23B`, and tertiary is the blue seed's primary. Everything is low chroma so nothing glares at night.

Dark (default):
- ground `#0c0e12`, panels `#111418` / `#161a1f` / `#1c2026`, text `#e2e5ef` and `#a7abb4`
- amber `#ebbf89` on `#573a10`: the main button, "now" text, the part of a trip behind you
- blue `#b0c8ec`: the next stop, focus rings
- on time `#8bd8a0`, late `#f5a06e`, very late or cancelled `#ff9993`, always with text next to it

Light is optional (button on the site, setting in the app): ground `#f9f9fe`, amber turns `#7f571e`, blue `#406189`.

The display card (live status in the app, the board and trip status on the site) stays dark glass `#161a1f` in light mode too, like the real thing.

Line colours: long distance `#b0283a`, regional `#5b4ba8`, S-Bahn `#2e7d32`, U-Bahn `#1565c0`, tram `#c2410c`, bus `#7b1fa2`. If the operator has its own colour, that one wins.

## Type

Atkinson Hyperlegible Next everywhere, made by the Braille Institute for people who struggle to read. Which is everybody at a stop in the rain. Tabular numbers for times. The zero is slashed; that's the font, not a bug. Self-hosted (`site/fonts/`, `android/app/src/main/res/font/`), OFL.

## Route strips

The one recurring shape. A 5 px track, 23 px dots with a 4 px ring.
- passed: filled amber
- next: blue ring, slightly bigger
- later: grey ring
- rides tint the track in their line colour, walks stay grey

Used for the trip status in the app and on the shared trip page, the stop picker on the website, recent trips in the app and the feature list on the website.

## Shapes and motion

Material 3 Expressive in the app (MotionScheme.expressive, button shape morphs). Panels have 28 px corners. On the site buttons squash a bit on press, board rows slide up 8 px when they load, the strip's amber fill slides to the chosen stop. Reduced motion turns all of it off.

## Files

`site/style.css` is shared, `site/index.html` is the start page, `site/trip/index.html` the shared trip. App theme in `android/app/src/main/java/app/navi/ui/Theme.kt`.
