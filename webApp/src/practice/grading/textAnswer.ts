import { spokenNumberVariants } from './spokenNumbers';

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
 * How alike two answers are, 0–1, ignoring case, surrounding space and internal run-length — the
 * same measure [gradeTextAnswer] scores against, exposed for callers that need the number rather
 * than a verdict (voice → multiple-choice matching, #388).
 *
 * Deliberately *not* a verdict: comparing this against [TEXT_ANSWER_THRESHOLD] answers "is this the
 * right answer?", which is a different question from "which of these options did they name?" and
 * wants a different threshold. See `voiceChoice.ts`.
 */
export function answerSimilarity(a: string, b: string): number {
  return similarityOf(normalize(a), normalize(b));
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

/**
 * Picks which of a recogniser's hypotheses to grade — n-best rescoring (#390).
 *
 * A recogniser returns several readings of the same audio, ranked by a general-purpose language
 * model biased toward everyday words, which is why proper nouns lose. This card's answer is
 * knowledge the recogniser doesn't have, so its own list is re-ranked with it: the first hypothesis
 * that grades correct wins.
 *
 * [gradeTextAnswer] is untouched and still decides, at the same threshold, so a spoken and a typed
 * string grade identically — what changes is which string gets graded. That does make voice more
 * forgiving than typing, since any hypothesis can win; deliberately so, because only the recogniser
 * proposed them and the mis-hear was never the user's mistake.
 *
 * Spoken numbers get the same treatment, one step later (#390): if no hypothesis grades correct as
 * heard, each is retried with its number words written as digits, so "nineteen eighty" can match a
 * card answered "1980". Digit variants are tried only *after* every original has failed, so a card
 * whose answer really is words ("one piece") still matches on the words and is recorded that way.
 *
 * Falls back to the top hypothesis rather than reporting nothing: a wrong answer still has to be
 * recordable, and it should be recorded as what they most likely said.
 *
 * Mirrors shared Kotlin `pickSpokenAnswer`.
 */
export function pickSpokenAnswer(
  hypotheses: string[],
  answer: string,
  alternativeAnswers: string[] = [],
): string | undefined {
  const matches = (candidate: string) => gradeTextAnswer(candidate, answer, alternativeAnswers).correct;

  const heard = hypotheses.find(matches);
  if (heard !== undefined) return heard;

  for (const hypothesis of hypotheses) {
    const asDigits = spokenNumberVariants(hypothesis).find(matches);
    if (asDigits !== undefined) return asDigits;
  }

  return hypotheses[0];
}
