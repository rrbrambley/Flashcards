package com.rrbrambley.flashcards.domain

data class FlashCard(
    val question: String,
    val answer: String,
    val imageUrl: String? = null,
)
