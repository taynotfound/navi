#!/usr/bin/env bash
# Regenerates the app screenshots on the website (site/shots/*.webp), German and English.
# Needs the Android SDK, Node with Playwright, Python with Pillow, and network access to the map tiles.
set -euo pipefail
cd "$(dirname "$0")/.."
run() { (cd android && ./gradlew -q :app:testDebugUnitTest --tests app.navi.ui.SiteScreenshots -PsiteShots --rerun); }
rm -rf android/app/build/site-shots
run                         # screens, plus what each map shows
node tools/site-maps.js     # draw the maps
run                         # screens again, now with the maps in
python3 - <<'PY'
from PIL import Image
for n in ['journey', 'trip', 'walk']:
    for l in ['de', 'en']:
        im = Image.open(f'android/app/build/site-shots/{n}-{l}.png').convert('RGB')
        im.resize((822, round(im.height * 822 / im.width)), Image.LANCZOS).save(f'site/shots/{n}-{l}.webp', 'WEBP', quality=82, method=6)
        print(f'site/shots/{n}-{l}.webp')
PY
