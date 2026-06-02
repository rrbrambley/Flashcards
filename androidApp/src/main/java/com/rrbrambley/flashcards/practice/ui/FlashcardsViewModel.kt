package com.rrbrambley.flashcards.practice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.domain.Flashcard
import com.rrbrambley.flashcards.domain.FlashcardRepository
import com.rrbrambley.flashcards.practice.domain.PracticeSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlashcardsViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val practiceSessionRepository: PracticeSessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FlashcardsUiState>(FlashcardsUiState.Loading)
    val uiState: StateFlow<FlashcardsUiState> = _uiState.asStateFlow()

    private var sessionId: Long? = null
    private var numIncorrect = 0
    private var numCorrect = 0
    private var currentFlashcardIndex = 0
    private var flashcards: List<Flashcard> = emptyList()
    private var loadJob: Job? = null

    fun loadSession(sessionId: Long?) {
        if (this.sessionId == sessionId && loadJob != null) return

        this.sessionId = sessionId
        loadJob?.cancel()
        _uiState.update { FlashcardsUiState.Loading }

        loadJob = viewModelScope.launch {
            if (sessionId == null) {
                loadDefaultFlashcards()
            } else {
                loadPracticeSession(sessionId)
            }
        }
    }

    fun swipeLeft() {
        numIncorrect++
        goForward()
    }

    fun swipeRight() {
        numCorrect++
        goForward()
    }

    fun goBack() {
        goToPreviousCard()
    }

    fun goForward() {
        goToNextCard()
    }

    private suspend fun loadDefaultFlashcards() {
        val cards = flashcardRepository.getFlashcards().first()
        flashcards = cards
        currentFlashcardIndex = 0
        numCorrect = 0
        numIncorrect = 0
        updateUiState()
    }

    private suspend fun loadPracticeSession(sessionId: Long) {
        val session = practiceSessionRepository.observeSession(sessionId).first()
        if (session == null || session.isCompleted) {
            _uiState.update { FlashcardsUiState.LoadingFailed }
            return
        }

        val deck = flashcardRepository.observeFlashcardDeck(session.deckId).first()
        val deckFlashcards = deck?.flashcards.orEmpty()
        if (deckFlashcards.isEmpty()) {
            _uiState.update { FlashcardsUiState.LoadingFailed }
            return
        }

        flashcards = deckFlashcards
        currentFlashcardIndex = session.currentCardIndex.coerceIn(0, flashcards.lastIndex)
        numCorrect = session.numCorrect
        numIncorrect = session.numIncorrect
        updateUiState()
    }

    private fun updateUiState() {
        if (flashcards.isEmpty()) {
            _uiState.update { FlashcardsUiState.LoadingFailed }
            return
        }

        _uiState.update {
            FlashcardsUiState.ShowFlashcard(
                numIncorrect = numIncorrect,
                numCorrect = numCorrect,
                flashcard = flashcards[currentFlashcardIndex],
                canGoBack = currentFlashcardIndex > 0,
            )
        }
    }

    private fun goToPreviousCard() {
        if (currentFlashcardIndex > 0) {
            currentFlashcardIndex--
            persistProgress()
            updateUiState()
        }
    }

    private fun goToNextCard() {
        val currentSessionId = sessionId
        if (currentFlashcardIndex < flashcards.size - 1) {
            currentFlashcardIndex++
            persistProgress()
            updateUiState()
        } else if (currentSessionId != null) {
            viewModelScope.launch {
                practiceSessionRepository.completeSession(currentSessionId)
            }
            _uiState.update {
                FlashcardsUiState.SessionCompleted(
                    numIncorrect = numIncorrect,
                    numCorrect = numCorrect,
                )
            }
        } else {
            updateUiState()
        }
    }

    private fun persistProgress() {
        val currentSessionId = sessionId ?: return
        viewModelScope.launch {
            practiceSessionRepository.updateProgress(
                sessionId = currentSessionId,
                currentCardIndex = currentFlashcardIndex,
                numCorrect = numCorrect,
                numIncorrect = numIncorrect,
            )
        }
    }
}
