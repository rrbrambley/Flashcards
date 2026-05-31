package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class PracticeSessionDto(
    val id: Long,
    val deckId: Long,
    val deckTitle: String,
    val currentCardIndex: Int = 0,
    val numCorrect: Int = 0,
    val numIncorrect: Int = 0,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
