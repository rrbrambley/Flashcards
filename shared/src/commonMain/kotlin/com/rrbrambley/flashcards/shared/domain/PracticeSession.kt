package com.rrbrambley.flashcards.shared.domain

data class PracticeSession(
    val id: Long = 0L,
    val deckId: Long,
    val deckTitle: String,
    val currentCardIndex: Int = 0,
    val numCorrect: Int = 0,
    val numIncorrect: Int = 0,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)
