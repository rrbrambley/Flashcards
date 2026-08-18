package com.rrbrambley.flashcards.practice.grading

/**
 * Matching a spoken answer to one of a multiple-choice card's options (#388), shared by Android +
 * iOS. The web keeps its own TypeScript copy in `webApp/src/practice/grading/voiceChoice.ts`; the
 * golden fixture (`testFixtures/practice-grading/grading-fixtures.json`) pins the two to identical
 * behaviour, so a threshold tweak on one side fails the other side's parity test in CI.
 *
 * Pure functions, no platform types — the recogniser differs per platform, this decision doesn't.
 */

/**
 * How alike the transcript must be to an option before it counts as naming it.
 *
 * Deliberately far below [TEXT_ANSWER_THRESHOLD] (0.85), and the two must never be conflated — they
 * answer different questions. Test's threshold asks *"is this the right answer?"*: free recall
 * against one target, so it has to be strict. This one asks *"which of these four did they name?"*,
 * a 1-of-4 discrimination where the wrong options are usually nothing like each other. At 0.85 the
 * utterance "the eiffel tower" would fail to match the option "Eiffel Tower" (~0.76) — obviously
 * wrong behaviour for a question whose answer is on screen.
 */
const val VOICE_CHOICE_FLOOR: Double = 0.6

/**
 * How far ahead the best option must be before we believe it.
 *
 * This matters more than the floor. A clipped transcript can sit 0.75 from *two* options at once
 * ("ira" is one character from both "Iran" and "Iraq") — over any sane floor, yet pure ambiguity.
 * The only safe response is to re-prompt.
 *
 * Mis-hears cost more here than in Test mode: a bad transcript there produces a visible near-miss
 * the user can see and dispute, whereas here it lands squarely on an option they never said, and a
 * graded answer cannot be un-graded.
 */
const val VOICE_CHOICE_MARGIN: Double = 0.1

/**
 * The index of the option [transcript] names, or `null` for "didn't catch that".
 *
 * Null covers three cases that all deserve a re-prompt rather than a guess: nothing was said, no
 * option is close enough, or two options are too close to separate. Callers must never fall back to
 * a best guess.
 *
 * Blank options are skipped (a deck can produce fewer than four choices) and never match a blank
 * transcript. Mirrors the web's `matchSpokenChoice`.
 */
fun matchSpokenChoice(transcript: String, options: List<String>): Int? {
    if (transcript.isBlank()) return null

    var bestIndex = -1
    var best = 0.0
    var second = 0.0
    options.forEachIndexed { index, option ->
        if (option.isBlank()) return@forEachIndexed
        val score = answerSimilarity(transcript, option)
        if (score > best) {
            second = best
            best = score
            bestIndex = index
        } else if (score > second) {
            second = score
        }
    }

    if (bestIndex == -1 || best < VOICE_CHOICE_FLOOR) return null
    if (best - second < VOICE_CHOICE_MARGIN) return null
    return bestIndex
}
