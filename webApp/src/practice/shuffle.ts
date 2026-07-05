// Deterministic seeded card shuffle for practice (FLA-200). Mirrors the shared Kotlin
// `SessionOrdering` byte-for-byte: a mulberry32 PRNG driving a Fisher–Yates. The same `(seed, size)`
// yields the same order on every platform, so a session reproduces its order across resume,
// re-render, and devices. Pinned to the Kotlin side by the golden fixture
// (testFixtures/practice-shuffle/shuffle-fixtures.json) via shuffle.parity.test.ts.
//
// The explicit 32-bit ops (`| 0`, Math.imul, `>>> 0`) reproduce Kotlin's Int arithmetic; do not
// "simplify" them or the two platforms drift and CI fails against the fixture.

function mulberry32(seed: number): () => number {
  let state = seed | 0;
  return () => {
    state = (state + 0x6d2b79f5) | 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return (t ^ (t >>> 14)) >>> 0; // unsigned 32-bit
  };
}

/** Orders [items] for a session: unchanged when [shuffle] is off, else a seeded shuffle of [seed]. */
export function orderCards<T>(items: T[], shuffle: boolean, seed: number): T[] {
  if (!shuffle || items.length < 2) return items;
  const result = [...items];
  const next = mulberry32(seed);
  // Fisher–Yates, high index to low (matching the Kotlin loop direction).
  for (let i = result.length - 1; i >= 1; i--) {
    const j = next() % (i + 1);
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result;
}

/** The permuted 0-based index order for [size] cards — the fixture's canonical form. */
export function orderIndices(size: number, shuffle: boolean, seed: number): number[] {
  return orderCards(
    Array.from({ length: size }, (_, i) => i),
    shuffle,
    seed,
  );
}
