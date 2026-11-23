# Design notes

## The idea

The website is a bus stop. There's the pole on the left, the round sign at the top with the logo, the name plate, and a glass case with the live departures. Below that is a second case with a printed notice where I explain what Navi is, like the "Fahrgastinformation" sheets people tape up at real stops.

The shared trip page uses the same pole as the route. It turns the colour of the line you're on, stops you've passed fade out, and the next one is highlighted.

## Colours

Generated from the logo with Material Color Utilities (TonalSpot): teal `#1FA39A` from the route, coral `#F0603F` from the pin. The app uses the same palette.

Dark is the default. Light is a button in the top bar and gets remembered.

- background `#0a0f0e`, panels `#131b1a` and `#1d2826`, text `#dce8e5` and `#a2adab`
- name plate `#265b56` with `#b8eee7` text and a `#9cd1ca` edge
- primary button `#9cd1ca` on `#0e4844`
- coral `#f9b7a7` for "live" and the focus ring, `#76463a` with `#ffdbd3` for "what to do now" and platform changes
- on time `#8bd8a0`, a bit late `#f2c27a`, very late or cancelled `#ffa49a`, always with text next to it

Line colours match the app: long distance `#b0283a`, regional `#5b4ba8`, S-Bahn `#2e7d32`, U-Bahn `#1565c0`, tram `#c2410c`, bus `#7b1fa2`. If the operator has its own colour, that one wins.

## Type

DINish everywhere. It's an open version of DIN 1451, the font on German road and station signs. Self-hosted in `site/fonts/`, one variable file. Narrow and heavy for the name plate and headings, wide for times, tabular numbers on the board.

## Shapes and motion

Plates have 28px corners, the case has a metal frame and a glass sheen. Buttons are pills that squash a bit when you hover or press them (the Material 3 Expressive spring). The only real animation is the departure rows flipping in when the board loads. Reduced motion turns all of it off.

## Files

`site/style.css` is shared. `site/index.html` is the start page, `site/trip/index.html` the shared trip. Data comes from https://navi-api.taymaerz.de.
