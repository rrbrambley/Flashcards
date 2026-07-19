package com.rrbrambley.flashcards.shared.domain

data class PracticeSession(
    val id: Long = 0L,
    val deckId: Long,
    val deckTitle: String,
    val currentCardIndex: Int = 0,
    val numCorrect: Int = 0,
    val numIncorrect: Int = 0,
    val isCompleted: Boolean = false,
    // The practice mode this session runs in (e.g. "flashcards" / "test" / "multiple_choice").
    // Defaulted so callers that don't set it get classic flashcards.
    val mode: String = "flashcards",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    // True when this session has local progress not yet synced to the backend (started/advanced
    // offline). Defaulted; lets the UI optionally show a "saved locally" affordance (FLA-91).
    val pendingSync: Boolean = false,
    // Whether this session presents its cards in a randomized order, and the seed that makes that
    // order reproducible across resume/devices (FLA-200). Applied by SessionOrdering at load.
    val shuffle: Boolean = false,
    val shuffleSeed: Long = 0L,
    // How many cards this session practices — a subset of the deck (FLA-219); null = the whole deck.
    // Applied after ordering (take) at load; fixed at creation.
    val questionCount: Int? = null,
    // Whether the whole session is graded at the end (#293) — all cards answered in a list, then
    // submitted — instead of card-by-card. Only Test / Multiple Choice; fixed at creation.
    val gradeAtEnd: Boolean = false,
)
