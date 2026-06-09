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

/**
 * Grades [input] against the card's [answer]: normalizes both, then compares by normalized
 * Levenshtein similarity (1 = identical). Correct when similarity >= [TEXT_ANSWER_THRESHOLD].
 * Mirrors the web's `gradeTextAnswer` so every platform grades identically.
 */
fun gradeTextAnswer(input: String, answer: String): TextAnswerGrade {
    val a = normalize(input)
    val b = normalize(answer)
    val maxLen = maxOf(a.length, b.length)
    val similarity = if (maxLen == 0) 1.0 else 1.0 - levenshtein(a, b).toDouble() / maxLen
    return TextAnswerGrade(correct = similarity >= TEXT_ANSWER_THRESHOLD, similarity = similarity)
}
