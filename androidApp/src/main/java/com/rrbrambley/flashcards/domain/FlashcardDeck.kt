package com.rrbrambley.flashcards.domain

import com.rrbrambley.flashcards.domain.Flashcard

data class FlashcardDeck(
    val id: Long = 0L,
    val title: String,
    val flashcards: List<Flashcard>,
)
