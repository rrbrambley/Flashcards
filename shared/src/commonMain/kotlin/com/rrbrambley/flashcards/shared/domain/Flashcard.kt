package com.rrbrambley.flashcards.shared.domain

data class Flashcard(
    val question: String,
    val answer: String,
    val imageUrl: String? = null,
    /** Extra answers accepted alongside [answer] when grading free-text Test mode (FLA-109). */
    val alternativeAnswers: List<String> = emptyList(),
)
