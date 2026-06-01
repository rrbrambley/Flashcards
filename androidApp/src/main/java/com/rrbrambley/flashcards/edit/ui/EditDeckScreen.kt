package com.rrbrambley.flashcards.edit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rrbrambley.flashcards.create.ui.CreateDeckContent

@Composable
fun EditDeckScreen(
    deckId: Long,
    modifier: Modifier = Modifier,
    editDeckViewModel: EditDeckViewModel = hiltViewModel(),
) {
    LaunchedEffect(deckId) {
        editDeckViewModel.loadDeck(deckId)
    }

    val uiState by editDeckViewModel.uiState.collectAsState()

    CreateDeckContent(
        deckTitle = uiState.deckTitle,
        cards = uiState.cards,
        showValidationErrors = uiState.showValidationErrors,
        onDeckTitleChange = editDeckViewModel::onDeckTitleChange,
        onTermChange = editDeckViewModel::onTermChange,
        onDefinitionChange = editDeckViewModel::onDefinitionChange,
        onImageSelected = editDeckViewModel::onImagePicked,
        onRemoveImage = editDeckViewModel::onRemoveImage,
        modifier = modifier,
    )
}
