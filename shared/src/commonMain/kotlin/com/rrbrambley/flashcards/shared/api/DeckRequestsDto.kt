package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

/** Body for POST /decks (create) and PUT /decks/{id} (update). */
@Serializable
data class CreateDeckRequest(
    val title: String,
    val flashcards: List<FlashcardDto>,
)
