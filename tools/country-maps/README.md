# Country maps generator

Generates the country **locator maps** for the **Maps of the World** global seed deck (FLA-211) — the
map counterpart to _Flags of the World_. Each card's front is a country highlighted in the app accent
among its surrounding countries; the back is the country name.

## What it does

For every country in [`../../backend/src/main/resources/seed/flags.json`](../../backend/src/main/resources/seed/flags.json)
that has [Natural Earth](https://www.naturalearthdata.com/) geometry, it renders `maps/{code}.png` and
writes a matching manifest to `../../backend/src/main/resources/seed/maps.json`. The backend seeds the
deck from that manifest, and the images are served over [jsDelivr](https://www.jsdelivr.com/) straight
from this repo — no upload to our own storage (same pattern as flagcdn for Flags):

```
https://cdn.jsdelivr.net/gh/rrbrambley/Flashcards@main/tools/country-maps/maps/{code}.png
```

## How the maps are framed

- **Data:** Natural Earth **1:50m** admin-0 countries (public domain), downloaded on first run
  (gitignored). 1:50m is crisp enough to be recognisable at card size; 1:110m looks blocky.
- **Projection:** Mercator, **rotated to the target country's centroid longitude** so it stays
  contiguous even across the antimeridian (Russia, Fiji).
- **Zoom:** scaled so the country fills ~42% of the frame, but never past a **34° minimum view span**
  so small countries (Luxembourg, etc.) still show enough surrounding continent to be placeable.
- **Overseas territories:** framing anchors on the country's **largest landmass** (e.g. metropolitan
  France, not French Guiana), so the highlight isn't skewed by far-flung parts.
- **Output:** PNG at 960px wide (2× the 480×360 canvas) — crisp on retina, universally decodable by
  every client's image loader (SDWebImage / Coil / `<img>`), and small for these flat-color maps.

Countries with no Natural Earth admin-0 feature (non-countries like the EU/UN, and French overseas
départements that Natural Earth folds into France) are skipped and logged.

## Regenerate

```bash
cd tools/country-maps
npm install
npm run generate
```

Commit the regenerated `maps/*.png` and `seed/maps.json` together so the deck and images stay in sync.
Because jsDelivr serves from `@main`, new/changed images resolve once merged to `main`.
