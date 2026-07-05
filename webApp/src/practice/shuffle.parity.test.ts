import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { orderCards, orderIndices } from './shuffle';

// Parity guard (FLA-200): asserts the web's seeded shuffle matches the canonical golden fixture that
// the shared Kotlin's jvmTest suite also loads (shared/.../ShuffleParityFixtureTest.kt). The fixture
// is the single source of truth — a change to the mulberry32 PRNG or the Fisher–Yates on only one
// platform fails that platform's run against it, so web↔mobile drift is caught in CI. Unlike the
// grading fixture (which asserts seed-independent properties), the shuffle order IS reproducible
// across platforms by construction, so we assert the exact permutation.
//
// Read via node:fs (Vitest runs in Node) rather than an `import`, so the fixture can live outside
// webApp's root without tripping Vite's filesystem-allow restrictions.

interface OrderCase {
  name: string;
  seed: number;
  size: number;
  expectedOrder: number[];
}

const here = dirname(fileURLToPath(import.meta.url));
const fixturePath = resolve(here, '../../../testFixtures/practice-shuffle/shuffle-fixtures.json');
const fixtures: { seededOrders: OrderCase[] } = JSON.parse(readFileSync(fixturePath, 'utf-8'));

describe('shuffle parity (golden fixture)', () => {
  it.each(fixtures.seededOrders)('seeded order: $name', (c) => {
    expect(orderIndices(c.size, true, c.seed)).toEqual(c.expectedOrder);
  });
});

describe('orderCards', () => {
  it('keeps the saved order when shuffle is off', () => {
    const cards = ['a', 'b', 'c', 'd'];
    expect(orderCards(cards, false, 12345)).toEqual(cards);
  });

  it('is deterministic for a given seed (stable across resume/re-render)', () => {
    expect(orderIndices(30, true, 777)).toEqual(orderIndices(30, true, 777));
  });

  it('is a permutation that loses no cards', () => {
    expect([...orderIndices(25, true, 999)].sort((a, b) => a - b)).toEqual(
      Array.from({ length: 25 }, (_, i) => i),
    );
  });

  it('leaves 0- or 1-card decks unchanged', () => {
    expect(orderCards<number>([], true, 5)).toEqual([]);
    expect(orderCards([7], true, 5)).toEqual([7]);
  });
});
