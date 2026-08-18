// Matching a spoken answer to one of a multiple-choice card's options (#388).
//
// Imports nothing from React or the DOM on purpose: this is hand-ported to Kotlin when mobile voice
// lands (#389), same posture as textAnswer.ts and multipleChoice.ts.

import { answerSimilarity } from './textAnswer';

/**
 * How alike the transcript must be to an option before it counts as naming it.
 *
 * Deliberately far below [TEXT_ANSWER_THRESHOLD] (0.85), and the two must never be conflated —
 * they answer different questions. Test's threshold asks *"is this the right answer?"*: free recall
 * against one target, so it has to be strict. This one asks *"which of these four did they name?"*,
 * a 1-of-4 discrimination where the wrong options are usually nothing like each other. At 0.85 the
 * utterance "the eiffel tower" would fail to match the option "Eiffel Tower" (≈0.76) — obviously
 * wrong behaviour for a question whose answer is on screen.
 */
export const VOICE_CHOICE_FLOOR = 0.6;

/**
 * How far ahead the best option must be before we believe it.
 *
 * This matters more than the floor. "1980" and "1990" score ≈0.75 against each other — over any
 * sane floor — so a single mis-heard digit would silently select a different, definite answer.
 * A near-tie is not a weak match, it's an ambiguous one, and the only safe response is to re-prompt.
 *
 * Mis-hears cost more here than in Test mode: a bad transcript there produces a visible near-miss
 * the user can see and dispute, whereas here it lands squarely on an option they never said.
 */
export const VOICE_CHOICE_MARGIN = 0.1;

/**
 * The index of the option [transcript] names, or `null` for "didn't catch that".
 *
 * Null covers three cases that all deserve a re-prompt rather than a guess: nothing was said, no
 * option is close enough, or two options are too close to separate. Callers must never fall back to
 * "best guess" — an auto-submitted wrong answer is recorded and cannot be un-graded.
 *
 * Blank options are skipped (a deck can produce fewer than four choices), and never match a blank
 * transcript.
 */
export function matchSpokenChoice(transcript: string, options: string[]): number | null {
  if (transcript.trim() === '') return null;

  let bestIndex = -1;
  let best = 0;
  let second = 0;
  options.forEach((option, index) => {
    if (option.trim() === '') return;
    const score = answerSimilarity(transcript, option);
    if (score > best) {
      second = best;
      best = score;
      bestIndex = index;
    } else if (score > second) {
      second = score;
    }
  });

  if (bestIndex === -1 || best < VOICE_CHOICE_FLOOR) return null;
  if (best - second < VOICE_CHOICE_MARGIN) return null;
  return bestIndex;
}
