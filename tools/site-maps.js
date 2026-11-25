// Draws the maps for the website screenshots. The app's screenshot test (SiteScreenshots) can't run MapLibre,
// so it writes what each map shows to android/app/build/site-shots/maps/*.json; this renders those with
// MapLibre GL JS and the same OpenFreeMap style the app uses, at the app's size and density.
//
//   node tools/site-maps.js            (needs Playwright and network access to the tiles)
//
// Then run the screenshot test again; it puts the pictures into the map slots. See tools/site-shots.sh.

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const dir = path.join(__dirname, '..', 'android', 'app', 'build', 'site-shots', 'maps');
const STYLE = 'https://tiles.openfreemap.org/styles/dark';

const page = (spec) => `<!doctype html><html><head><meta charset="utf-8">
<link href="https://cdn.jsdelivr.net/npm/maplibre-gl@4.7.1/dist/maplibre-gl.css" rel="stylesheet">
<script src="https://cdn.jsdelivr.net/npm/maplibre-gl@4.7.1/dist/maplibre-gl.js"></script>
<style>html,body,#m{margin:0;width:${spec.width}px;height:${spec.height}px;overflow:hidden}</style></head>
<body><div id="m"></div><script>
const spec = ${JSON.stringify(spec)};
const map = new maplibregl.Map({ container: 'm', style: '${STYLE}', attributionControl: false, interactive: false,
  center: spec.center || [10.45, 51.16], zoom: spec.zoom || 5, bearing: spec.bearing || 0, pitch: spec.pitch || 0,
  fadeDuration: 0 });
map.on('load', () => {
  const lines = { type: 'FeatureCollection', features: spec.lines.filter(l => l.points.length >= 2).map(l => ({
    type: 'Feature', properties: { color: l.color, dashed: l.dashed }, geometry: { type: 'LineString', coordinates: l.points } })) };
  const points = { type: 'FeatureCollection', features: spec.points.map(p => ({
    type: 'Feature', properties: { color: p.color, label: p.label, radius: p.radius }, geometry: { type: 'Point', coordinates: [p.lon, p.lat] } })) };
  map.addSource('l', { type: 'geojson', data: lines });
  map.addSource('p', { type: 'geojson', data: points });
  // same layers as NaviMap.kt
  map.addLayer({ id: 'casing', type: 'line', source: 'l', filter: ['!=', ['get', 'dashed'], true],
    paint: { 'line-color': '#000000', 'line-width': 9 }, layout: { 'line-cap': 'round', 'line-join': 'round' } });
  map.addLayer({ id: 'solid', type: 'line', source: 'l', filter: ['!=', ['get', 'dashed'], true],
    paint: { 'line-color': ['get', 'color'], 'line-width': 5 }, layout: { 'line-cap': 'round', 'line-join': 'round' } });
  map.addLayer({ id: 'dashed', type: 'line', source: 'l', filter: ['==', ['get', 'dashed'], true],
    paint: { 'line-color': ['get', 'color'], 'line-width': 4, 'line-dasharray': [0.5, 2] }, layout: { 'line-cap': 'round' } });
  map.addLayer({ id: 'pts', type: 'circle', source: 'p',
    paint: { 'circle-color': ['get', 'color'], 'circle-radius': ['to-number', ['get', 'radius']], 'circle-stroke-color': '#FFFFFF', 'circle-stroke-width': 2 } });
  map.addLayer({ id: 'labels', type: 'symbol', source: 'p',
    layout: { 'text-field': ['get', 'label'], 'text-font': ['Noto Sans Regular'], 'text-size': 12, 'text-anchor': 'top', 'text-offset': [0, 1.1], 'text-optional': true },
    paint: { 'text-color': '#FFFFFF', 'text-halo-color': '#000000', 'text-halo-width': 1.5 } });
  if (spec.me) {
    // the location puck, like MapLibre Android's
    map.addSource('me', { type: 'geojson', data: { type: 'Point', coordinates: spec.me } });
    map.addLayer({ id: 'me-halo', type: 'circle', source: 'me', paint: { 'circle-color': '#4A90E2', 'circle-opacity': 0.2, 'circle-radius': 22 } });
    map.addLayer({ id: 'me', type: 'circle', source: 'me', paint: { 'circle-color': '#1E88E5', 'circle-radius': 8, 'circle-stroke-color': '#FFFFFF', 'circle-stroke-width': 3 } });
  }
  if (spec.fit) {
    const b = new maplibregl.LngLatBounds();
    spec.lines.forEach(l => l.points.forEach(p => b.extend(p)));
    map.fitBounds(b, { padding: spec.padding, maxZoom: spec.maxZoom, duration: 0 });
  }
  map.once('idle', () => { window.done = true; });
});
</script></body></html>`;

(async () => {
  const browser = await chromium.launch();
  for (const f of fs.readdirSync(dir).filter(f => f.endsWith('.json'))) {
    const spec = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
    const ctx = await browser.newContext({ viewport: { width: spec.width, height: spec.height }, deviceScaleFactor: 3 });
    const p = await ctx.newPage();
    await p.setContent(page(spec), { waitUntil: 'load' });
    await p.waitForFunction(() => window.done === true, null, { timeout: 60000 });
    await p.waitForTimeout(500);
    const out = path.join(dir, f.replace('.json', '.png'));
    await p.screenshot({ path: out });
    console.log('map', out);
    await ctx.close();
  }
  await browser.close();
})();
