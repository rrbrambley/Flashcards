package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

/** Body for POST /decks (create) and PUT /decks/{id} (update). */
@Serializable
data class CreateDeckRequest(
    val title: String,
    val flashcards: List<FlashcardDto>,
    /** Optional tags / categories. Normalized + validated server-side. Defaults to empty so older
     *  clients that omit the field keep working. */
    val tags: List<String> = emptyList(),
)
