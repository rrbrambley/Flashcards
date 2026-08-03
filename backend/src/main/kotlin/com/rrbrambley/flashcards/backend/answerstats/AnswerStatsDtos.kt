package com.rrbrambley.flashcards.backend.answerstats

import kotlinx.serialization.Serializable

/**
 * One entry in a card's frequency-sorted "most common answers" list (#346): a distinct submitted
 * answer (grouped by its normalized form), how often it was submitted across everyone who practiced
 * the card, and how many of those submissions were graded correct. Groundwork data — not yet wired to
 * an endpoint; it'll back later features (Multiple-Choice distractors, Test-mode hints).
 */
@Serializable
data class CommonAnswerDto(
    /** A representative raw form for display: the most common raw spelling (ties broken by recency). */
    val answer: String,
    /** The canonical grouping key (trim + lowercase + collapse whitespace). */
    val normalizedAnswer: String,
    /** Total submissions of this (normalized) answer for the card. */
    val count: Int,
    /** How many of those submissions were graded correct; `count - correctCount` were incorrect. */
    val correctCount: Int,
)
