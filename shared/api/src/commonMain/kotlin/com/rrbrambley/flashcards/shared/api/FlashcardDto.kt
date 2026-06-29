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
    /**
     * Stable per-card id (FLA-113): assigned by the backend, preserved across deck edits, so per-card
     * features (e.g. discussions) stay attached to the right card. Blank for a not-yet-saved card;
     * clients round-trip it on edit so ids aren't regenerated. Additive + defaulted for old clients.
     */
    val cardUid: String = "",
)
