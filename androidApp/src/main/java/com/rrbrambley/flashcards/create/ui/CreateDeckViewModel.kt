package com.rrbrambley.flashcards.create.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardDeck
import com.rrbrambley.flashcards.domain.FlashcardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MinimumCompleteCardCount = 1

@HiltViewModel
class CreateDeckViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateDeckUiState())
    val uiState: StateFlow<CreateDeckUiState> = _uiState.asStateFlow()

    private var nextDraftCardId = 2L

    fun onDeckTitleChange(deckTitle: String) {
        _uiState.update {
            it.copy(
                deckTitle = deckTitle,
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
            it.copy(
                cards = it.cards + DeckFlashcardDraft(id = nextDraftCardId),
                deckSaved = false,
            )
        }
        nextDraftCardId++
    }

    fun finishDeckCreation() {
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
            val saved = runCatching {
                flashcardRepository.saveFlashcardDeck(
                    FlashcardDeck(
                        title = currentState.deckTitle.trim(),
                        flashcards = completeCards.map { card ->
                            Flashcard(
                                question = card.term.trim(),
                                answer = card.definition.trim(),
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

    private fun updateCard(
        cardId: Long,
        update: (DeckFlashcardDraft) -> DeckFlashcardDraft,
    ) {
        _uiState.update { state ->
            state.copy(
                cards = state.cards.map { card ->
                    if (card.id == cardId) update(card) else card
                },
                showValidationErrors = false,
                deckSaved = false,
            )
        }
    }

    private fun resetDeckCreation() {
        nextDraftCardId = 2L
        _uiState.update {
            CreateDeckUiState(deckSaved = true)
        }
    }

    private fun CreateDeckUiState.completeCards(): List<DeckFlashcardDraft> = cards.filter {
        it.term.isNotBlank() && it.definition.isNotBlank()
    }

    private fun DeckFlashcardDraft.isIncompleteStartedCard(): Boolean =
        term.isNotBlank() && definition.isBlank() || term.isBlank() && definition.isNotBlank()
}
