package com.rrbrambley.flashcards.practice.domain

data class FlashcardDeck(
    val id: Long = 0L,
    val title: String,
    val flashcards: List<Flashcard>,
)
