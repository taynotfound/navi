# Design notes

## The idea

Navi looks like the passenger display above a train door. Dark glass, muted sand text for what matters right now, and one line with its stops where the next stop has a blue ring. That's the whole system, in the app and on the website.

The logo is the same thing in small: an N drawn as a route, starting at a sand dot and ending at a blue ring. Source is `brand/logo.svg`.

## Colours

Neutrals come from Material Color Utilities (TonalSpot, blue-graphite seed `#3D6FA8`). The accents are hand-tuned and deliberately dull: sand instead of LED amber, a dusty blue for "next". Nothing glares at night.

Dark (default):
- ground `#0c0e12`, panels `#111418` / `#161a1f` / `#1c2026`, text `#e2e5ef` and `#a7abb4`
- sand `#d6c3a5` on `#3a2e1c`: the main button, "now" text, the part of a trip behind you
- blue `#afbccf`: the next stop, focus rings
- on time `#9dc7a8`, late `#ddad8a`, very late or cancelled `#e8a19b`, always with text next to it

Light is optional (button on the site, setting in the app): ground `#f9f9fe`, sand turns `#6b5a40`, blue `#4e5d72`.

The display card (live status in the app, the board and trip status on the site) stays dark glass `#161a1f` in light mode too, like the real thing.

Line colours: long distance `#8e3b45`, regional `#5a5290`, S-Bahn `#3f6e47`, U-Bahn `#3d5f8c`, tram `#9a5a36`, bus `#6e4a85`. If the operator has its own colour, that one wins, but it gets damped first (saturation capped, kept dark enough for white text) so a list of chips doesn't shout.

## Type

Google Sans Flex everywhere, the typeface Material 3 Expressive is built around (OFL since 2025). Round and calm, with clear numbers, which matters in an app that is mostly times. Proportional numbers: tabular ones leave gaps around every 1. Self-hosted (`site/fonts/`, `android/app/src/main/res/font/`).

## Route strips

The one recurring shape. A 5 px track, 23 px dots with a 4 px ring.
- passed: filled sand
- next: blue ring, slightly bigger
- later: grey ring
- rides tint the track in their line colour, walks stay grey

Used for the trip status in the app and on the shared trip page, the stop picker on the website, recent trips in the app and the feature list on the website.

## Shapes and motion

Material 3 Expressive in the app (MotionScheme.expressive, button shape morphs). Panels have 28 px corners. On the site buttons squash a bit on press, board rows slide up 8 px when they load, the strip's sand fill slides to the chosen stop. Reduced motion turns all of it off.

## Files

`site/style.css` is shared, `site/index.html` is the start page, `site/trip/index.html` the shared trip. App theme in `android/app/src/main/java/app/navi/ui/Theme.kt`.
