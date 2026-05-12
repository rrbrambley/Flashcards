package com.rrbrambley.flashcards.domain

data class Flashcard(
    val question: String,
    val answer: String,
    val imageUrl: String? = null,
)
