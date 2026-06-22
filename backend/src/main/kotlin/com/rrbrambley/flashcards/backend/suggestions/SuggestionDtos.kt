package com.rrbrambley.flashcards.backend.suggestions

import kotlinx.serialization.Serializable

/**
 * Admin review-queue DTO for an open answer suggestion (FLA-130) — the suggestion plus the card it
 * targets (so a moderator can judge it in context). A backend⇄web contract like the RBAC/discussion
 * admin DTOs; the web hand-mirrors it in TypeScript and it's not part of the shared client SDK.
 */
@Serializable
data class AnswerSuggestionDto(
    val id: Long,
    val cardUid: String,
    val suggestedAnswer: String,
    val deckId: Long,
    val deckTitle: String,
    val question: String,
    val currentAnswer: String,
    val suggesterDisplayName: String,
    val createdAtMillis: Long,
)
