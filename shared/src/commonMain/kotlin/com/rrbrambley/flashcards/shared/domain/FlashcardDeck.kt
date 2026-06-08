package com.rrbrambley.flashcards.shared.domain

data class FlashcardDeck(
    val id: Long = 0L,
    val title: String,
    val flashcards: List<Flashcard>,
    /** Whether the current user may edit this deck. False for the read-only global deck. */
    val isEditable: Boolean = true,
    /** User-facing tags / categories (for future grouping, sorting, and search). */
    val tags: List<String> = emptyList(),
)
