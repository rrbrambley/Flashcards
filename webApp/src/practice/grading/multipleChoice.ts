import type { FlashcardDto } from '../../api/types';

// Builds the option set for multiple-choice practice. Distractors come from OTHER cards' answers in
// the same deck (MVP) — no extra authoring required. Kept standalone (and RNG-injectable) so it's
// unit-testable and reusable by a future "Learn" mode.

/** Fisher–Yates shuffle using the injected RNG (so tests can be deterministic). */
function shuffle<T>(items: T[], rng: () => number): T[] {
  const a = items.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/**
 * Returns up to [count] answer options for [card]: the correct answer plus distractors sampled from
 * other cards' (non-blank) answers in [deck], de-duplicated case-insensitively and shuffled. Yields
 * fewer than [count] when the deck doesn't have enough distinct answers.
 */
export function buildChoices(
  card: FlashcardDto,
  deck: FlashcardDto[],
  count = 4,
  rng: () => number = Math.random,
): string[] {
  const correct = card.answer.trim();
  const seen = new Set<string>([correct.toLowerCase()]);

  const distractors: string[] = [];
  for (const other of deck) {
    if (other === card) continue;
    const answer = other.answer.trim();
    const key = answer.toLowerCase();
    if (answer.length === 0 || seen.has(key)) continue;
    seen.add(key);
    distractors.push(answer);
  }

  const chosen = shuffle(distractors, rng).slice(0, Math.max(0, count - 1));
  return shuffle([correct, ...chosen], rng);
}
