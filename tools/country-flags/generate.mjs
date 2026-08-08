#!/usr/bin/env node
// Downloads every seeded country flag from flagcdn and normalises it with `usvg`, writing the result
// to flags/{code}.svg. See README.md for why the normalisation exists (#369).
//
//   npm run generate            regenerate everything
//   npm run generate -- --check verify the committed output is normalised, without downloading
//
// Requires the `usvg` CLI: brew install resvg

import { spawnSync } from 'node:child_process'
import { mkdirSync, readFileSync, writeFileSync, readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const OUT_DIR = join(HERE, 'flags')
const SEED = join(HERE, '..', '..', 'backend', 'src', 'main', 'resources', 'seed', 'flags.json')
const SOURCE = (code) => `https://flagcdn.com/${code}.svg`
const CONCURRENCY = 8

/**
 * The constructs that make a flag render differently across SVG implementations (#369). `usvg`
 * resolves all three; anything left is a normalisation failure, not a cosmetic nit.
 */
function unnormalisedReasons(svg) {
  const reasons = []
  if (svg.includes('<use')) reasons.push('<use> reference')
  const viewBox = svg.match(/viewBox="\s*(-?[\d.]+)[ ,]+(-?[\d.]+)/)
  if (viewBox && (Number(viewBox[1]) !== 0 || Number(viewBox[2]) !== 0)) reasons.push('non-zero viewBox origin')
  if (svg.includes('preserveAspectRatio')) reasons.push('preserveAspectRatio')
  return reasons
}

function requireUsvg() {
  const probe = spawnSync('usvg', ['--version'], { encoding: 'utf8' })
  if (probe.error || probe.status !== 0) {
    console.error('`usvg` not found. Install it with:\n\n  brew install resvg\n')
    process.exit(1)
  }
  return probe.stdout.trim()
}

function codes() {
  return JSON.parse(readFileSync(SEED, 'utf8')).map((entry) => entry.code)
}

/** Fetches one flag and pipes it through usvg (stdin → stdout), so no temp files are needed. */
async function normalise(code) {
  const response = await fetch(SOURCE(code))
  if (!response.ok) throw new Error(`${SOURCE(code)} → HTTP ${response.status}`)
  const source = Buffer.from(await response.arrayBuffer())

  const result = spawnSync('usvg', ['-', '-c'], { input: source, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  if (result.status !== 0) throw new Error(`usvg failed: ${(result.stderr || '').trim()}`)

  const reasons = unnormalisedReasons(result.stdout)
  if (reasons.length > 0) throw new Error(`still not normalised (${reasons.join(', ')})`)

  writeFileSync(join(OUT_DIR, `${code}.svg`), result.stdout)
  return result.stdout.length
}

/** Re-checks the committed output without hitting the network — what CI would run. */
function check(all) {
  const onDisk = new Set(readdirSync(OUT_DIR).filter((f) => f.endsWith('.svg')).map((f) => f.slice(0, -4)))
  const failures = []
  for (const code of all) {
    if (!onDisk.has(code)) {
      failures.push(`${code}: missing (seeded but not generated)`)
      continue
    }
    const reasons = unnormalisedReasons(readFileSync(join(OUT_DIR, `${code}.svg`), 'utf8'))
    if (reasons.length > 0) failures.push(`${code}: ${reasons.join(', ')}`)
  }
  for (const code of onDisk) {
    if (!all.includes(code)) failures.push(`${code}: orphaned (generated but not seeded)`)
  }
  if (failures.length > 0) {
    console.error(`✗ ${failures.length} problem(s):`)
    failures.forEach((f) => console.error(`  ${f}`))
    process.exit(1)
  }
  console.log(`✓ ${all.length} flags present and normalised.`)
}

async function main() {
  const all = codes()
  mkdirSync(OUT_DIR, { recursive: true })

  if (process.argv.includes('--check')) return check(all)

  const version = requireUsvg()
  console.log(`Normalising ${all.length} flags with ${version} → tools/country-flags/flags/`)

  const failures = []
  let bytes = 0
  let done = 0
  const queue = [...all]
  await Promise.all(
    Array.from({ length: CONCURRENCY }, async () => {
      while (queue.length > 0) {
        const code = queue.shift()
        try {
          // Read `bytes` after the await, not before: `bytes += await …` captures the old value
          // first, so concurrent workers would clobber each other's increments.
          const written = await normalise(code)
          bytes += written
        } catch (error) {
          failures.push(`${code}: ${error.message}`)
        }
        if (++done % 50 === 0) console.log(`  ${done}/${all.length}`)
      }
    }),
  )

  console.log(`\nWrote ${all.length - failures.length} flags, ${(bytes / 1024 / 1024).toFixed(1)} MB total.`)
  if (failures.length > 0) {
    // Every seeded code must produce a flag — a gap would leave a card with a dead image.
    console.error(`\n✗ ${failures.length} failed:`)
    failures.forEach((f) => console.error(`  ${f}`))
    process.exit(1)
  }
  console.log('✓ No <use>, no offset view boxes, no preserveAspectRatio left.')
}

await main()
