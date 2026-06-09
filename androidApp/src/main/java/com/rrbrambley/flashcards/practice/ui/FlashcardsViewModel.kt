package com.rrbrambley.flashcards.practice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.shared.domain.Flashcard
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
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
    private var mode: String = PracticeMode.CLASSIC.key
    private var loadJob: Job? = null
    private var loadedKey: Pair<Long?, Long?>? = null

    /**
     * Practices an existing [sessionId], or — given a [deckId] (the Home "Practice" action) —
     * starts/resumes a session for that deck (parity with the Library practice flow + iOS).
     */
    fun load(sessionId: Long?, deckId: Long?) {
        if (loadedKey == (sessionId to deckId) && loadJob != null) return
        loadedKey = sessionId to deckId
        loadJob?.cancel()
        _uiState.update { FlashcardsUiState.Loading }

        loadJob = viewModelScope.launch {
            val resolvedSessionId = sessionId
                ?: deckId?.let { runCatching { practiceSessionRepository.startOrResumeSession(it) }.getOrNull() }
            this@FlashcardsViewModel.sessionId = resolvedSessionId
            if (resolvedSessionId == null) {
                _uiState.update { FlashcardsUiState.LoadingFailed }
            } else {
                loadPracticeSession(resolvedSessionId)
            }
        }
    }

    /**
     * Records the outcome for the current card and advances. Used by every mode — a Classic swipe, or
     * Test/Multiple-Choice after the answer is graded.
     */
    fun onResult(correct: Boolean) {
        if (correct) numCorrect++ else numIncorrect++
        goForward()
    }

    fun goBack() {
        goToPreviousCard()
    }

    fun goForward() {
        goToNextCard()
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
        mode = session.mode
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
                deck = flashcards,
                mode = mode,
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
