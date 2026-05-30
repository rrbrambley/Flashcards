package com.rrbrambley.flashcards.edit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.create.ui.DeckFlashcardDraft
import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import com.rrbrambley.flashcards.domain.FlashcardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MinimumCompleteCardCount = 1

@HiltViewModel
class EditDeckViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
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
        val completeCards = currentState.completeCards()
        val hasIncompleteStartedCard = currentState.cards.any { it.isIncompleteStartedCard() }
        val isValid = currentState.deckTitle.isNotBlank() &&
            completeCards.size >= MinimumCompleteCardCount &&
            !hasIncompleteStartedCard

        if (!isValid) {
            _uiState.update { it.copy(showValidationErrors = true, deckSaved = false) }
            return
        }

        viewModelScope.launch {
            flashcardRepository.updateFlashcardDeck(
                FlashcardDeck(
                    id = currentDeckId,
                    title = currentState.deckTitle.trim(),
                    flashcards = completeCards.map { card ->
                        Flashcard(
                            question = card.term.trim(),
                            answer = card.definition.trim(),
                        )
                    },
                ),
            )
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
        )
    }.ifEmpty {
        listOf(DeckFlashcardDraft(id = 1L))
    }

    private fun EditDeckUiState.completeCards(): List<DeckFlashcardDraft> = cards.filter {
        it.term.isNotBlank() && it.definition.isNotBlank()
    }

    private fun DeckFlashcardDraft.isIncompleteStartedCard(): Boolean =
        term.isNotBlank() && definition.isBlank() || term.isBlank() && definition.isNotBlank()

    private data class EditDeckFormSnapshot(
        val deckTitle: String,
        val cards: List<DeckFlashcardDraft>,
    )
}
