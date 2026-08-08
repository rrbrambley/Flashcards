# Country flags generator

Produces the flag images for the **Flags of the World** global seed deck. Each card's front is a
national flag; the back is the country name.

The flags still originate from [flagcdn.com](https://flagcdn.com/), but they are no longer served from
there. Every flag is **normalised** with [`usvg`](https://github.com/linebender/resvg) and the result
is committed here, served over [jsDelivr](https://www.jsdelivr.com/) straight from this repo — the
same pattern as `../country-maps/`:

```
https://cdn.jsdelivr.net/gh/rrbrambley/Flashcards@main/tools/country-flags/flags/{code}.svg
```

## Why normalise (#369)

flagcdn doesn't draw a star as a path. It draws **half of one point**, then mirrors and rotates it
through nested `<use>` references — Liberia's entire star is one 4-character path plus four `<use>`
elements, three levels deep. That's valid SVG, and browsers render it perfectly. Partial SVG
implementations do not, and every client platform ships a different partial implementation:

| Renderer | Used by | Failed on the raw flagcdn set |
|---|---|---|
| CoreSVG (`SDWebImageSVGCoder`) | iOS | `<use>` + transform — stars mangled, wrong size, or stacked into a blob |
| SVGKit | (evaluated as a replacement) | 12 blank, 8 partial — mostly viewBoxes with a non-zero origin |
| AndroidSVG (Coil) | Android | sized Qatar off its view box rather than its declared size (#363) |

Swapping renderers just trades one broken subset for another. Normalising the **content** instead
removes what they disagree about. Across the 252 seeded flags, `usvg`:

| | before | after |
|---|---|---|
| files containing `<use>` | 90 | 0 |
| files with a non-zero viewBox origin | 19 | 0 |
| files whose viewBox ratio ≠ declared ratio (the #363 trap) | 1 | 0 |

The output is plain paths with a `0 0` origin, so there is nothing left for a partial implementation
to get wrong. Geometry is preserved exactly — Qatar's `preserveAspectRatio="none"` stretch, for
instance, is baked into an explicit `transform="matrix(…)"` rather than dropped.

Cost is size: expanding `<use>` duplicates geometry, so the set grows from ~4.4 MB to ~8.1 MB
(~32 KB/flag). It's static CDN content, so that's an acceptable trade for rendering the same
everywhere.

## Usage

Requires the `usvg` CLI:

```bash
brew install resvg
```

Then:

```bash
cd tools/country-flags
npm run generate    # re-download + re-normalise every flag
npm run check       # verify the committed output, no network
```

`generate` reads the country codes from
[`../../backend/src/main/resources/seed/flags.json`](../../backend/src/main/resources/seed/flags.json)
— the same manifest the backend seeds from — so the two can't drift. It **fails loudly** if any flag
can't be fetched or still contains an un-normalised construct afterwards: a gap here would leave a
card pointing at a dead image.

`check` re-verifies the committed files and also flags codes that are seeded but not generated (or
generated but not seeded). It needs no network, so it's the one to wire into CI.

## Caveats

- **jsDelivr caches `@main` for ~12 hours.** Regenerating a flag *in place* (same filename, new bytes)
  will keep serving the old image until the cache expires or is purged. New files are fetched
  immediately. See the purge endpoint at https://www.jsdelivr.com/tools/purge.
- Re-running `generate` re-downloads all 252 flags from flagcdn. It's polite to do that rarely —
  the committed output only needs regenerating when flagcdn updates a flag or `usvg` improves.
- Attribution: the flags are public-domain national flags, as published by flagcdn.
