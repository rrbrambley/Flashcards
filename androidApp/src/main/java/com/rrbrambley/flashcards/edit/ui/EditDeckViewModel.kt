package com.rrbrambley.flashcards.edit.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.create.ui.DeckFlashcardDraft
import com.rrbrambley.flashcards.create.ui.isComplete
import com.rrbrambley.flashcards.create.ui.isStarted
import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import com.rrbrambley.flashcards.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val MinimumCompleteCardCount = 1

@HiltViewModel
class EditDeckViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val apiClient: FlashcardApiClient,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditDeckUiState())
    val uiState: StateFlow<EditDeckUiState> = _uiState.asStateFlow()

    private var deckId: Long? = null
    private var nextDraftCardId = 1L
    private var initialSnapshot: EditDeckFormSnapshot? = null
    private var loadDeckJob: Job? = null

    fun loadDeck(deckId: Long) {
        if (this.deckId == deckId) return

        this.deckId = deckId
        initialSnapshot = null
        loadDeckJob?.cancel()
        _uiState.update { EditDeckUiState() }

        loadDeckJob = viewModelScope.launch {
            flashcardRepository.observeFlashcardDeck(deckId).collect { deck ->
                if (deck == null) return@collect
                val drafts = deck.toDrafts()
                nextDraftCardId = (drafts.maxOfOrNull { it.id } ?: 0L) + 1L
                val snapshot = EditDeckFormSnapshot(
                    deckTitle = deck.title,
                    cards = drafts,
                )
                initialSnapshot = snapshot
                _uiState.update {
                    EditDeckUiState(
                        deckTitle = deck.title,
                        cards = drafts,
                        isLoading = false,
                    )
                }
                loadDeckJob?.cancel()
            }
        }
    }

    fun onDeckTitleChange(deckTitle: String) {
        _uiState.update {
            it.copy(
                deckTitle = deckTitle,
                isDirty = isDirty(deckTitle = deckTitle, cards = it.cards),
                showValidationErrors = false,
                deckSaved = false,
            )
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
            val updatedCards = it.cards + DeckFlashcardDraft(id = nextDraftCardId)
            it.copy(
                cards = updatedCards,
                isDirty = isDirty(deckTitle = it.deckTitle, cards = updatedCards),
                deckSaved = false,
            )
        }
        nextDraftCardId++
    }

    fun finishDeckEditing() {
        val currentDeckId = deckId ?: return
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
                flashcardRepository.updateFlashcardDeck(
                    FlashcardDeck(
                        id = currentDeckId,
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
            // On failure (e.g. offline, or the read-only global deck) keep the form for retry.
            if (!saved) return@launch

            val snapshot = EditDeckFormSnapshot(
                deckTitle = currentState.deckTitle,
                cards = currentState.cards,
            )
            initialSnapshot = snapshot
            _uiState.update {
                it.copy(
                    deckSaved = true,
                    isDirty = false,
                    showValidationErrors = false,
                )
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
            val updatedCards = state.cards.map { card ->
                if (card.id == cardId) update(card) else card
            }
            state.copy(
                cards = updatedCards,
                isDirty = isDirty(deckTitle = state.deckTitle, cards = updatedCards),
                showValidationErrors = false,
                deckSaved = false,
            )
        }
    }

    private fun isDirty(deckTitle: String, cards: List<DeckFlashcardDraft>): Boolean {
        val snapshot = initialSnapshot ?: return false
        return snapshot != EditDeckFormSnapshot(deckTitle = deckTitle, cards = cards)
    }

    private fun FlashcardDeck.toDrafts(): List<DeckFlashcardDraft> = flashcards.mapIndexed { index, flashcard ->
        DeckFlashcardDraft(
            id = index + 1L,
            term = flashcard.question,
            definition = flashcard.answer,
            imageUrl = flashcard.imageUrl,
        )
    }.ifEmpty {
        listOf(DeckFlashcardDraft(id = 1L))
    }

    private suspend fun uploadImage(uri: Uri): String {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: error("Could not read the selected image")
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val filename = "image.${mime.substringAfterLast('/')}"
        return apiClient.uploadImage(bytes, filename, mime).url
    }

    private data class EditDeckFormSnapshot(
        val deckTitle: String,
        val cards: List<DeckFlashcardDraft>,
    )
}
