// Generates country locator maps for the "Maps of the World" global seed deck (FLA-211).
//
// For every country in the Flags deck's `flags.json` that has Natural Earth geometry, renders a PNG
// showing that country highlighted (app accent) among its neighbours, and writes a matching
// `maps.json` manifest the backend seeds from. Run: `node generate.mjs` (see README.md).
//
// Data: Natural Earth 1:50m admin-0 (public domain), fetched on first run (gitignored).
// Output: maps/{code}.png (committed, served via jsDelivr) + ../../backend/.../seed/maps.json.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { geoMercator, geoPath, geoCentroid, geoArea } from 'd3-geo';
import { Resvg } from '@resvg/resvg-js';

const DIR = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(DIR, '../..');
const NE_FILE = path.join(DIR, 'ne_50m_admin_0_countries.geojson');
const NE_URL =
  'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson';
const FLAGS_JSON = path.join(REPO, 'backend/src/main/resources/seed/flags.json');
const MAPS_JSON = path.join(REPO, 'backend/src/main/resources/seed/maps.json');
const OUT_DIR = path.join(DIR, 'maps');

// Canvas is 4:3; rasterised at 2× (960px wide) so it stays crisp at card size on retina displays.
const W = 480;
const H = 360;
const RENDER_WIDTH = 960;
const FILL = 0.42; // target country fills ~42% of the frame...
const MIN_VIEW_DEG = 34; // ...but never zooms in past a 34° view, so tiny countries stay placeable.
// Skip countries whose highlight renders smaller than this (projected px² on the 480×360 canvas):
// below it the target is a near-zero-pixel dot with nothing to identify. ~8 keeps small-but-visible
// coastal nations (Singapore, Antigua, …) while dropping the invisible micro-states and scattered
// ocean specks (Vatican/Monaco/Nauru/Tuvalu render at 0px²) — all of which remain in the Flags deck.
const MIN_VISIBLE_PX = 8;
// Ocean is a clear medium blue; land a medium grey (dark enough that the white coastlines AND the
// internal country borders both read against it — a lighter grey washes the white borders out); the
// burnt-orange target pops against both.
const OCEAN = '#7fb2df';
const LAND = '#c1c9d2';
const TARGET = '#c2410c';
const BORDER = '#ffffff';
const BORDER_WIDTH = 0.6;

async function ensureNaturalEarth() {
  if (fs.existsSync(NE_FILE)) return;
  console.log('Downloading Natural Earth 1:50m admin-0 countries…');
  const res = await fetch(NE_URL);
  if (!res.ok) throw new Error(`Failed to download Natural Earth data: ${res.status}`);
  fs.writeFileSync(NE_FILE, Buffer.from(await res.arrayBuffer()));
}

const polygonArea = (coordinates) => geoArea({ type: 'Polygon', coordinates });

function totalArea(feature) {
  const g = feature.geometry;
  if (g.type === 'Polygon') return polygonArea(g.coordinates);
  return g.coordinates.reduce((sum, c) => sum + polygonArea(c), 0);
}

// The polygon to FRAME on: a country's largest landmass, so overseas territories (e.g. French Guiana
// on the "France" feature) don't skew the centroid/zoom. The whole country is still highlighted;
// far-flung parts simply fall outside the crop.
function anchorPolygon(feature) {
  const g = feature.geometry;
  if (g.type === 'Polygon') return feature;
  let best = null;
  let bestArea = -1;
  for (const coordinates of g.coordinates) {
    const a = polygonArea(coordinates);
    if (a > bestArea) {
      bestArea = a;
      best = { type: 'Feature', properties: {}, geometry: { type: 'Polygon', coordinates } };
    }
  }
  return best;
}

function locatorSvg(all, target) {
  const anchor = anchorPolygon(target);
  // Centre the projection on the anchor's longitude so it stays contiguous across the antimeridian.
  const c = geoCentroid(anchor);
  const proj = geoMercator().rotate([-c[0], 0]).scale(100).translate([W / 2, H / 2]);
  let path2 = geoPath(proj);

  let b = path2.bounds(anchor);
  const k = Math.min((W * FILL) / (b[1][0] - b[0][0]), (H * FILL) / (b[1][1] - b[0][1]));
  const scale = Math.min(100 * k, W / ((MIN_VIEW_DEG * Math.PI) / 180));
  proj.scale(scale);
  path2 = geoPath(proj);

  b = path2.bounds(anchor);
  const t = proj.translate();
  proj.translate([t[0] + (W / 2 - (b[0][0] + b[1][0]) / 2), t[1] + (H / 2 - (b[0][1] + b[1][1]) / 2)]);
  path2 = geoPath(proj);

  const parts = [];
  for (const f of all) {
    const fb = path2.bounds(f);
    if (!fb.every((p) => p.every(Number.isFinite))) continue;
    if (fb[1][0] < 0 || fb[0][0] > W || fb[1][1] < 0 || fb[0][1] > H) continue; // outside the frame
    const d = path2(f);
    if (!d) continue;
    const fill = f === target ? TARGET : LAND;
    parts.push(`<path d="${d}" fill="${fill}" stroke="${BORDER}" stroke-width="${BORDER_WIDTH}" stroke-linejoin="round"/>`);
  }
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">` +
    `<rect width="${W}" height="${H}" fill="${OCEAN}"/>${parts.join('')}</svg>`;
  // Projected area of the highlighted country, in canvas px² — how visible the target actually is.
  return { svg, targetPx: Math.abs(path2.area(target)) };
}

// Index NE features by ISO-3166 alpha-2, keeping the LARGEST feature per code so a country's mainland
// wins over a tiny territory that shares its code (e.g. Australia vs. its island dependencies). Uses
// ISO_A2_EH first (it fills the "-99" holes NE leaves on ISO_A2 for France/Norway/etc.).
function buildIsoIndex(all) {
  const byIso = new Map();
  const byName = new Map();
  for (const f of all) {
    const p = f.properties;
    f.__area = totalArea(f);
    for (const key of [p.ISO_A2_EH, p.ISO_A2, p.WB_A2]) {
      if (!key || key === '-99') continue;
      const k = key.toLowerCase();
      const cur = byIso.get(k);
      if (!cur || cur.__area < f.__area) byIso.set(k, f);
    }
    for (const name of [p.NAME, p.NAME_LONG]) if (name) byName.set(name.toLowerCase(), f);
  }
  return { byIso, byName };
}

async function main() {
  await ensureNaturalEarth();
  const world = JSON.parse(fs.readFileSync(NE_FILE, 'utf8'));
  const all = world.features.filter((f) => f.properties.NAME !== 'Antarctica');
  const { byIso, byName } = buildIsoIndex(all);
  const flags = JSON.parse(fs.readFileSync(FLAGS_JSON, 'utf8'));

  fs.rmSync(OUT_DIR, { recursive: true, force: true });
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const manifest = [];
  const noGeometry = [];
  const tooSmall = [];
  for (const { code, name } of flags) {
    const feature = byIso.get(code) || byName.get(name.toLowerCase());
    if (!feature) {
      noGeometry.push(name);
      continue;
    }
    const { svg, targetPx } = locatorSvg(all, feature);
    // Drop countries that render as an invisible dot — no identifiable landmass to quiz on.
    if (targetPx < MIN_VISIBLE_PX) {
      tooSmall.push(`${name} (${Math.round(targetPx)}px²)`);
      continue;
    }
    const png = new Resvg(svg, { fitTo: { mode: 'width', value: RENDER_WIDTH } }).render().asPng();
    fs.writeFileSync(path.join(OUT_DIR, `${code}.png`), png);
    manifest.push({ code, name });
  }

  fs.writeFileSync(MAPS_JSON, JSON.stringify(manifest, null, 2) + '\n');
  console.log(`Rendered ${manifest.length} maps → ${path.relative(REPO, OUT_DIR)}/`);
  console.log(`Wrote manifest → ${path.relative(REPO, MAPS_JSON)}`);
  console.log(`Skipped ${noGeometry.length} with no Natural Earth geometry: ${noGeometry.join(', ')}`);
  console.log(`Skipped ${tooSmall.length} too small to identify (<${MIN_VISIBLE_PX}px²): ${tooSmall.join(', ')}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
