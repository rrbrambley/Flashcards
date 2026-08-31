package com.rrbrambley.flashcards.practice.grading

/**
 * Grading for the text-entry "Test" practice mode, shared by Android + iOS (the web keeps its own
 * TypeScript copy in `webApp/src/practice/grading/`). Case-insensitive, whitespace-tolerant, and
 * forgiving of small typos. Kept standalone (pure functions) so a future "Learn" mode can reuse it.
 */

/** Minimum normalized edit-distance similarity (0–1) for a typed answer to count as correct. */
const val TEXT_ANSWER_THRESHOLD: Double = 0.85

/** The result of grading a typed answer: whether it counts as correct, and its 0–1 similarity. */
data class TextAnswerGrade(val correct: Boolean, val similarity: Double)

private val WHITESPACE = Regex("\\s+")

/** Lower-cases, trims, and collapses internal whitespace so grading ignores those differences. */
private fun normalize(value: String): String = value.trim().lowercase().replace(WHITESPACE, " ")

/** Levenshtein edit distance (two-row dynamic-programming). */
private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val curr = IntArray(b.length + 1)
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        }
        prev = curr
    }
    return prev[b.length]
}

/** Normalized Levenshtein similarity (0–1, 1 = identical) between two already-normalized strings. */
private fun similarityOf(a: String, b: String): Double {
    val maxLen = maxOf(a.length, b.length)
    return if (maxLen == 0) 1.0 else 1.0 - levenshtein(a, b).toDouble() / maxLen
}

/**
 * How alike two answers are, 0-1, ignoring case, surrounding space and internal run-length — the
 * same measure [gradeTextAnswer] scores against, exposed for callers that need the number rather
 * than a verdict (voice -> multiple-choice matching, #388).
 *
 * Deliberately *not* a verdict: comparing this against [TEXT_ANSWER_THRESHOLD] answers "is this the
 * right answer?", a different question from "which of these options did they name?" that wants a
 * different threshold. See `VoiceChoice.kt`.
 */
fun answerSimilarity(a: String, b: String): Double = similarityOf(normalize(a), normalize(b))

/**
 * Grades [input] against the card's [answer] plus any [alternativeAnswers] (FLA-109): normalizes
 * each, then takes the best normalized Levenshtein similarity (1 = identical). Correct when that best
 * similarity >= [TEXT_ANSWER_THRESHOLD] — i.e. the input matches the primary OR any alternative.
 * Blank alternatives are ignored. Mirrors the web's `gradeTextAnswer` so every platform grades identically.
 */
fun gradeTextAnswer(input: String, answer: String, alternativeAnswers: List<String> = emptyList()): TextAnswerGrade {
    val a = normalize(input)
    var best = similarityOf(a, normalize(answer))
    for (alternative in alternativeAnswers) {
        val b = normalize(alternative)
        if (b.isEmpty()) continue
        best = maxOf(best, similarityOf(a, b))
    }
    return TextAnswerGrade(correct = best >= TEXT_ANSWER_THRESHOLD, similarity = best)
}

/**
 * Which of a recogniser's hypotheses to grade — n-best rescoring (#390).
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
 * Falls back to the top hypothesis rather than reporting nothing: a wrong answer still has to be
 * recordable, and it should be recorded as what they most likely said.
 */
fun pickSpokenAnswer(
    hypotheses: List<String>,
    answer: String,
    alternativeAnswers: List<String> = emptyList(),
): String? {
    val matched = hypotheses.firstOrNull { gradeTextAnswer(it, answer, alternativeAnswers).correct }
    return matched ?: hypotheses.firstOrNull()
}
