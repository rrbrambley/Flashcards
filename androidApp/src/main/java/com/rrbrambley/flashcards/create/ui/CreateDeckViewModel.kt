package com.rrbrambley.flashcards.create.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.data.image.ImageUploader
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MinimumCompleteCardCount = 1

@HiltViewModel
class CreateDeckViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val imageUploader: ImageUploader,
    private val stringProvider: StringProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateDeckUiState())
    val uiState: StateFlow<CreateDeckUiState> = _uiState.asStateFlow()

    // One-shot user-facing messages (e.g. a failed save), surfaced as a snackbar.
    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private var nextDraftCardId = 2L

    fun onDeckTitleChange(deckTitle: String) {
        _uiState.update {
            it.copy(deckTitle = deckTitle, showValidationErrors = false, deckSaved = false)
        }
    }

    fun onCategoryChange(category: String) {
        _uiState.update { it.copy(category = category, deckSaved = false) }
    }

    fun onTermChange(cardId: Long, term: String) {
        updateCard(cardId) { card -> card.copy(term = term) }
    }

    fun onDefinitionChange(cardId: Long, definition: String) {
        updateCard(cardId) { card -> card.copy(definition = definition) }
    }

    fun onImagePicked(cardId: Long, uri: Uri) {
        updateCard(cardId) { it.copy(uploading = true, uploadError = null) }
        viewModelScope.launch {
            runCatching { imageUploader.upload(uri) }
                .onSuccess { url ->
                    updateCard(cardId) { it.copy(imageUrl = url, uploading = false, uploadError = null) }
                }
                .onFailure {
                    updateCard(cardId) {
                        it.copy(uploading = false, uploadError = stringProvider.getString(R.string.image_upload_error))
                    }
                }
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

    fun removeCard(cardId: Long) {
        _uiState.update { state ->
            state.copy(
                cards = state.cards.filterNot { it.id == cardId },
                showValidationErrors = false,
                deckSaved = false,
            )
        }
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
                        tags = currentState.category.toCategoryTags(),
                    ),
                )
            }.isSuccess
            // On failure (e.g. offline) keep the form so the user can retry, and tell them why.
            if (saved) {
                resetDeckCreation()
            } else {
                _userMessages.tryEmit(stringProvider.getString(R.string.deck_save_error))
            }
        }
    }

    fun onDeckSavedHandled() {
        _uiState.update { it.copy(deckSaved = false) }
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
