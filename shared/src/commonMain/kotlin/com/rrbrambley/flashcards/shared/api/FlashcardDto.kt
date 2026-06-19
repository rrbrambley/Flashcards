package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class FlashcardDto(
    val question: String,
    val answer: String,
    val imageUrl: String? = null,
    /**
     * Extra answers accepted in addition to [answer], used to grade free-text Test mode (FLA-109).
     * Additive + defaulted so older clients/payloads that omit it keep working.
     */
    val alternativeAnswers: List<String> = emptyList(),
)
