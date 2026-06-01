package com.rrbrambley.flashcards.domain

import com.rrbrambley.flashcards.domain.Flashcard

data class FlashcardDeck(
    val id: Long = 0L,
    val title: String,
    val flashcards: List<Flashcard>,
    /** Whether the current user may edit this deck. False for the read-only global deck. */
    val isEditable: Boolean = true,
)
