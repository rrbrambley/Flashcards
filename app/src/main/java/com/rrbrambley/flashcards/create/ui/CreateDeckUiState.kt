package com.rrbrambley.flashcards.create.ui

data class CreateDeckUiState(
    val deckTitle: String = "",
    val cards: List<DeckFlashcardDraft> = listOf(DeckFlashcardDraft(id = 1L)),
    val showValidationErrors: Boolean = false,
    val deckSaved: Boolean = false,
)
