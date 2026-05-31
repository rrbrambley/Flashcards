package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class FlashcardDeckDto(
    val id: Long = 0L,
    val title: String,
    val flashcards: List<FlashcardDto>,
)
