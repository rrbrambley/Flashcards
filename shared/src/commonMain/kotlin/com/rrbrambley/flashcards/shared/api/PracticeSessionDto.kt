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
    /** The practice mode this session runs in (web: flashcards / test / multiple_choice). Defaulted so
     *  the Room cache mapper can ignore it and mobile/older clients keep doing classic flashcards. */
    val mode: String = "flashcards",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
