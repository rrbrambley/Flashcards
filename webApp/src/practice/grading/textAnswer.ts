// Grading for the text-entry "Test" mode, kept standalone so other modes (e.g. a future "Learn"
// mode) can reuse it. Case-insensitive, whitespace-tolerant, and forgiving of small typos.

/** Minimum normalized edit-distance similarity (0–1) for a typed answer to count as correct. */
export const TEXT_ANSWER_THRESHOLD = 0.85;

/** Lower-cases, trims, and collapses internal whitespace so grading ignores those differences. */
function normalize(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, ' ');
}

/** Levenshtein edit distance (two-row DP). */
function levenshtein(a: string, b: string): number {
  if (a === b) return 0;
  if (a.length === 0) return b.length;
  if (b.length === 0) return a.length;

  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const curr = [i];
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      curr[j] = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost);
    }
    prev = curr;
  }
  return prev[b.length];
}

/** Normalized Levenshtein similarity (0–1, 1 = identical) between two already-normalized strings. */
function similarityOf(a: string, b: string): number {
  const maxLen = Math.max(a.length, b.length);
  return maxLen === 0 ? 1 : 1 - levenshtein(a, b) / maxLen;
}

/**
 * Grades [input] against the card's [answer] plus any [alternativeAnswers] (FLA-109): normalizes
 * each, then takes the best normalized Levenshtein similarity (1 = identical). Correct when that best
 * similarity ≥ [TEXT_ANSWER_THRESHOLD] — i.e. the input matches the primary OR any alternative.
 * Blank alternatives are ignored (so an empty input can't match an empty alternative).
 */
export function gradeTextAnswer(
  input: string,
  answer: string,
  alternativeAnswers: string[] = [],
): { correct: boolean; similarity: number } {
  const a = normalize(input);
  let best = similarityOf(a, normalize(answer));
  for (const alternative of alternativeAnswers) {
    const b = normalize(alternative);
    if (b.length === 0) continue;
    best = Math.max(best, similarityOf(a, b));
  }
  return { correct: best >= TEXT_ANSWER_THRESHOLD, similarity: best };
}
