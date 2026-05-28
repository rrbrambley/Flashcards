package com.rrbrambley.flashcards.edit.ui

import com.rrbrambley.flashcards.create.ui.DeckFlashcardDraft

data class EditDeckUiState(
    val deckTitle: String = "",
    val cards: List<DeckFlashcardDraft> = emptyList(),
    val showValidationErrors: Boolean = false,
    val deckSaved: Boolean = false,
)
