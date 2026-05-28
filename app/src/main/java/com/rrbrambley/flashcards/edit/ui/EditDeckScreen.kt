package com.rrbrambley.flashcards.edit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.create.ui.CreateDeckContent
import com.rrbrambley.flashcards.practice.domain.FlashcardDeck

@Composable
fun EditDeckScreen(
    deck: FlashcardDeck,
    modifier: Modifier = Modifier,
    editDeckViewModel: EditDeckViewModel = hiltViewModel(),
) {
    LaunchedEffect(deck.id) {
        editDeckViewModel.loadDeck(deck)
    }

    val uiState by editDeckViewModel.uiState.collectAsState()

    CreateDeckContent(
        title = "Edit Flashcards",
        description = "Update the deck title, terms, and definitions for this saved deck.",
        deckTitle = uiState.deckTitle,
        cards = uiState.cards,
        showValidationErrors = uiState.showValidationErrors,
        onDeckTitleChange = editDeckViewModel::onDeckTitleChange,
        onTermChange = editDeckViewModel::onTermChange,
        onDefinitionChange = editDeckViewModel::onDefinitionChange,
        modifier = modifier,
    )
}
