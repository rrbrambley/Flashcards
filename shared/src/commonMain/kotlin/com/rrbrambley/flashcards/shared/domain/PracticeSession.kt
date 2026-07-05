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
)
