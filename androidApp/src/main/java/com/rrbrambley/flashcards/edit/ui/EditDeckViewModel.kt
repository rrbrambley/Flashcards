package com.rrbrambley.flashcards.edit.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.core.StringProvider
import com.rrbrambley.flashcards.create.ui.DeckFlashcardDraft
import com.rrbrambley.flashcards.create.ui.isComplete
import com.rrbrambley.flashcards.create.ui.isStarted
import com.rrbrambley.flashcards.create.ui.toCategoryTags
import com.rrbrambley.flashcards.data.image.ImageUploader
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardDeck
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
class EditDeckViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val imageUploader: ImageUploader,
    private val stringProvider: StringProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditDeckUiState())
    val uiState: StateFlow<EditDeckUiState> = _uiState.asStateFlow()

    // One-shot user-facing messages (e.g. a failed save), surfaced as a snackbar.
    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

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
                // Surface only the first tag as the editable category (the backend keeps a list).
                val category = deck.tags.firstOrNull().orEmpty()
                val snapshot = EditDeckFormSnapshot(
                    deckTitle = deck.title,
                    category = category,
                    cards = drafts,
                )
                initialSnapshot = snapshot
                _uiState.update {
                    EditDeckUiState(
                        deckTitle = deck.title,
                        category = category,
                        cards = drafts,
                        isLoading = false,
                        isEditable = deck.isEditable,
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
                isDirty = isDirty(deckTitle = deckTitle, category = it.category, cards = it.cards),
                showValidationErrors = false,
                deckSaved = false,
            )
        }
    }

    fun onCategoryChange(category: String) {
        _uiState.update {
            it.copy(
                category = category,
                isDirty = isDirty(deckTitle = it.deckTitle, category = category, cards = it.cards),
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
        if (!_uiState.value.isEditable) return
        _uiState.update {
            val updatedCards = it.cards + DeckFlashcardDraft(id = nextDraftCardId)
            it.copy(
                cards = updatedCards,
                isDirty = isDirty(deckTitle = it.deckTitle, category = it.category, cards = updatedCards),
                deckSaved = false,
            )
        }
        nextDraftCardId++
    }

    fun removeCard(cardId: Long) {
        if (!_uiState.value.isEditable) return
        _uiState.update { state ->
            val updatedCards = state.cards.filterNot { it.id == cardId }
            state.copy(
                cards = updatedCards,
                isDirty = isDirty(deckTitle = state.deckTitle, category = state.category, cards = updatedCards),
                showValidationErrors = false,
                deckSaved = false,
            )
        }
    }

    fun finishDeckEditing() {
        val currentDeckId = deckId ?: return
        val currentState = _uiState.value
        if (!currentState.isEditable || currentState.isSaving) return // block repeat taps mid-save
        val completeCards = currentState.cards.filter { it.isComplete() }
        val hasIncompleteStartedCard = currentState.cards.any { it.isStarted() && !it.isComplete() }
        val isValid = currentState.deckTitle.isNotBlank() &&
            completeCards.size >= MinimumCompleteCardCount &&
            !hasIncompleteStartedCard

        if (!isValid) {
            _uiState.update { it.copy(showValidationErrors = true, deckSaved = false) }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
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
                                // Preserve any web-authored alternatives through an edit (FLA-109).
                                alternativeAnswers = card.alternativeAnswers,
                                // Preserve the stable card id so the backend updates in place (FLA-113).
                                cardUid = card.cardUid,
                            )
                        },
                        tags = currentState.category.toCategoryTags(),
                    ),
                )
            }.isSuccess
            // On failure (e.g. offline) keep the form for retry, and tell the user why.
            if (!saved) {
                _uiState.update { it.copy(isSaving = false) }
                _userMessages.tryEmit(stringProvider.getString(R.string.deck_save_error))
                return@launch
            }

            val snapshot = EditDeckFormSnapshot(
                deckTitle = currentState.deckTitle,
                category = currentState.category,
                cards = currentState.cards.stableForDirtyCheck(),
            )
            initialSnapshot = snapshot
            _uiState.update {
                it.copy(
                    deckSaved = true,
                    isSaving = false,
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
                isDirty = isDirty(deckTitle = state.deckTitle, category = state.category, cards = updatedCards),
                showValidationErrors = false,
                deckSaved = false,
            )
        }
    }

    private fun isDirty(deckTitle: String, category: String, cards: List<DeckFlashcardDraft>): Boolean {
        val snapshot = initialSnapshot ?: return false
        return snapshot != EditDeckFormSnapshot(
            deckTitle = deckTitle,
            category = category,
            cards = cards.stableForDirtyCheck(),
        )
    }

    /** Drops transient per-card UI state (upload progress/errors) so it can't mark the form dirty. */
    private fun List<DeckFlashcardDraft>.stableForDirtyCheck(): List<DeckFlashcardDraft> =
        map { it.copy(uploading = false, uploadError = null) }

    private fun FlashcardDeck.toDrafts(): List<DeckFlashcardDraft> = flashcards.mapIndexed { index, flashcard ->
        DeckFlashcardDraft(
            id = index + 1L,
            term = flashcard.question,
            definition = flashcard.answer,
            imageUrl = flashcard.imageUrl,
            alternativeAnswers = flashcard.alternativeAnswers,
            cardUid = flashcard.cardUid,
        )
    }.ifEmpty {
        listOf(DeckFlashcardDraft(id = 1L))
    }

    private data class EditDeckFormSnapshot(
        val deckTitle: String,
        val category: String,
        val cards: List<DeckFlashcardDraft>,
    )
}
