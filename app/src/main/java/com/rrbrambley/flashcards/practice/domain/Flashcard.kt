package com.rrbrambley.flashcards.practice.domain

data class Flashcard(
    val question: String,
    val answer: String,
    val imageUrl: String? = null,
)
