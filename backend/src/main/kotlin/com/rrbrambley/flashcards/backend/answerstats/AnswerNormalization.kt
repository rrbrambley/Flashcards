package com.rrbrambley.flashcards.backend.answerstats

private val WHITESPACE = Regex("\\s+")

/**
 * Canonicalizes a submitted answer into the grouping key used by the per-card "most common answers"
 * aggregation (#346): trim, lowercase, and collapse internal whitespace, so "Paris", "paris " and
 * "PARIS" all count as the same answer.
 *
 * This mirrors the normalization the shared Test-mode grader applies before its fuzzy compare
 * (`shared/.../practice/grading/TextAnswerGrading.kt`). It's duplicated here because `:backend`
 * depends only on `:shared:api`, not `:shared` — keep the two in sync. (We intentionally stop at the
 * canonical form; the grader's Levenshtein similarity is for correctness, not for grouping.)
 *
 * Returns null for a blank/whitespace-only answer, so "no real answer" isn't recorded as a common one.
 */
fun normalizeAnswer(text: String?): String? =
    text?.trim()?.lowercase()?.replace(WHITESPACE, " ")?.takeIf { it.isNotEmpty() }
