package com.rrbrambley.flashcards.edit.ui

import com.rrbrambley.flashcards.create.ui.DeckFlashcardDraft

data class EditDeckUiState(
    val deckTitle: String = "",
    /** Optional category — surfaced as the deck's single tag (the backend stores a list). */
    val category: String = "",
    val cards: List<DeckFlashcardDraft> = emptyList(),
    val isLoading: Boolean = true,
    val isDirty: Boolean = false,
    val showValidationErrors: Boolean = false,
    val deckSaved: Boolean = false,
    /** Read-only decks (e.g. the global catalog) are shown but can't be edited. */
    val isEditable: Boolean = true,
)
