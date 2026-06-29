package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

/**
 * Body for POST /cards/{cardUid}/answer-suggestions (FLA-130) — a user's "this should be correct"
 * suggestion for a card's free-text answer. The admin review queue DTOs are a backend⇄web contract,
 * not part of this shared client.
 */
@Serializable
data class SuggestAnswerRequest(val suggestedAnswer: String)
