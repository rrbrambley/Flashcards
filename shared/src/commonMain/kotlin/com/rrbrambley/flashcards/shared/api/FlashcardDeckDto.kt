package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class FlashcardDeckDto(
    val id: Long = 0L,
    val title: String,
    val flashcards: List<FlashcardDto>,
    /**
     * Whether the requesting user may edit this deck. False for the global catalog deck
     * (no owner). Defaults to true so an older/omitting payload degrades to the previous
     * behavior (editing allowed, rejected only at save time).
     */
    val editable: Boolean = true,
    /** User-facing tags / categories for grouping, sorting, and search. Defaults to empty so an
     *  older payload that omits the field degrades to "untagged". */
    val tags: List<String> = emptyList(),
)
