package com.rrbrambley.flashcards.create.ui

data class CreateDeckUiState(
    val deckTitle: String = "",
    /** Optional category — surfaced as the deck's single tag (the backend stores a list). */
    val category: String = "",
    val cards: List<DeckFlashcardDraft> = listOf(DeckFlashcardDraft(id = 1L)),
    val showValidationErrors: Boolean = false,
    val deckSaved: Boolean = false,
)
