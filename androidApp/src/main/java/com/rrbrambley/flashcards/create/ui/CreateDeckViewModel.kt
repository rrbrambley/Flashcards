package com.rrbrambley.flashcards.create.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import com.rrbrambley.flashcards.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val MinimumCompleteCardCount = 1

@HiltViewModel
class CreateDeckViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val apiClient: FlashcardApiClient,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateDeckUiState())
    val uiState: StateFlow<CreateDeckUiState> = _uiState.asStateFlow()

    private var nextDraftCardId = 2L

    fun onDeckTitleChange(deckTitle: String) {
        _uiState.update {
            it.copy(deckTitle = deckTitle, showValidationErrors = false, deckSaved = false)
        }
    }

    fun onTermChange(cardId: Long, term: String) {
        updateCard(cardId) { card -> card.copy(term = term) }
    }

    fun onDefinitionChange(cardId: Long, definition: String) {
        updateCard(cardId) { card -> card.copy(definition = definition) }
    }

    fun onImagePicked(cardId: Long, uri: Uri) {
        updateCard(cardId) { it.copy(uploading = true) }
        viewModelScope.launch {
            runCatching { uploadImage(uri) }
                .onSuccess { url -> updateCard(cardId) { it.copy(imageUrl = url, uploading = false) } }
                .onFailure { updateCard(cardId) { it.copy(uploading = false) } }
        }
    }

    fun onRemoveImage(cardId: Long) {
        updateCard(cardId) { it.copy(imageUrl = null) }
    }

    fun addDraftCard() {
        _uiState.update {
            it.copy(cards = it.cards + DeckFlashcardDraft(id = nextDraftCardId), deckSaved = false)
        }
        nextDraftCardId++
    }

    fun finishDeckCreation() {
        val currentState = _uiState.value
        val completeCards = currentState.cards.filter { it.isComplete() }
        val hasIncompleteStartedCard = currentState.cards.any { it.isStarted() && !it.isComplete() }
        val isValid = currentState.deckTitle.isNotBlank() &&
            completeCards.size >= MinimumCompleteCardCount &&
            !hasIncompleteStartedCard

        if (!isValid) {
            _uiState.update { it.copy(showValidationErrors = true, deckSaved = false) }
            return
        }

        viewModelScope.launch {
            val saved = runCatching {
                flashcardRepository.saveFlashcardDeck(
                    FlashcardDeck(
                        title = currentState.deckTitle.trim(),
                        flashcards = completeCards.map { card ->
                            Flashcard(
                                question = card.term.trim(),
                                answer = card.definition.trim(),
                                imageUrl = card.imageUrl,
                            )
                        },
                    ),
                )
            }.isSuccess
            // On failure (e.g. offline) keep the form so the user can retry.
            if (saved) resetDeckCreation()
        }
    }

    fun onDeckSavedHandled() {
        _uiState.update { it.copy(deckSaved = false) }
    }

    private suspend fun uploadImage(uri: Uri): String {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: error("Could not read the selected image")
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val filename = "image.${mime.substringAfterLast('/')}"
        return apiClient.uploadImage(bytes, filename, mime).url
    }

    private fun updateCard(
        cardId: Long,
        update: (DeckFlashcardDraft) -> DeckFlashcardDraft,
    ) {
        _uiState.update { state ->
            state.copy(
                cards = state.cards.map { card -> if (card.id == cardId) update(card) else card },
                showValidationErrors = false,
                deckSaved = false,
            )
        }
    }

    private fun resetDeckCreation() {
        nextDraftCardId = 2L
        _uiState.update { CreateDeckUiState(deckSaved = true) }
    }
}
