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
)
