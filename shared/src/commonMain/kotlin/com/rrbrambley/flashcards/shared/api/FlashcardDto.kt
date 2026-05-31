package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class FlashcardDto(
    val question: String,
    val answer: String,
    val imageUrl: String? = null,
)
