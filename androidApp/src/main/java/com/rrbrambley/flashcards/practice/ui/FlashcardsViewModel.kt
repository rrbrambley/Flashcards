package com.rrbrambley.flashcards.practice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.shared.AuthResult
import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
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
import java.time.ZoneId
import javax.inject.Inject

/** State of the guest "create an account to save your progress" flow (FLA-103). */
sealed interface GuestSaveState {
    data object Idle : GuestSaveState
    data object Saving : GuestSaveState
    data class Error(val message: String) : GuestSaveState
    data object Saved : GuestSaveState
}

@HiltViewModel
class FlashcardsViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val practiceSessionRepository: PracticeSessionRepository,
    private val apiClient: FlashcardApiClient,
    private val authService: AuthService,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FlashcardsUiState>(FlashcardsUiState.Loading)
    val uiState: StateFlow<FlashcardsUiState> = _uiState.asStateFlow()

    private val _saveState = MutableStateFlow<GuestSaveState>(GuestSaveState.Idle)
    val saveState: StateFlow<GuestSaveState> = _saveState.asStateFlow()

    private var sessionId: Long? = null
    private var deckId: Long? = null
    private var deckTitle: String = ""
    private var isGuest: Boolean = false
    private var numIncorrect = 0
    private var numCorrect = 0
    private var currentFlashcardIndex = 0
    private var flashcards: List<Flashcard> = emptyList()
    private var mode: String = PracticeMode.CLASSIC.key
    private var loadJob: Job? = null
    private var loadedKey: LoadKey? = null

    private data class LoadKey(val sessionId: Long?, val deckId: Long?, val isGuest: Boolean, val mode: String)

    /**
     * Practices an existing [sessionId], or — given a [deckId] — starts/resumes a session for that
     * deck. When [isGuest] is true the deck is practiced from the public catalog entirely in memory:
     * no session is created and nothing is persisted (FLA-103).
     */
    fun load(sessionId: Long?, deckId: Long?, isGuest: Boolean = false, mode: String = PracticeMode.CLASSIC.key) {
        val key = LoadKey(sessionId, deckId, isGuest, mode)
        if (loadedKey == key && loadJob != null) return
        loadedKey = key
        loadJob?.cancel()
        this.isGuest = isGuest
        _uiState.update { FlashcardsUiState.Loading }

        loadJob = viewModelScope.launch {
            if (isGuest && deckId != null) {
                loadGuestDeck(deckId, mode)
            } else {
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

    /** Whether leaving now should prompt a guest to save: guest, mid-session, with some progress. */
    fun shouldPromptSave(): Boolean =
        isGuest &&
            _uiState.value is FlashcardsUiState.ShowFlashcard &&
            (currentFlashcardIndex > 0 || numCorrect > 0 || numIncorrect > 0)

    /** The deck currently being practiced (id + title), for the Share action; null until loaded. */
    fun sharedDeck(): Pair<Long, String>? = deckId?.let { it to deckTitle }

    /**
     * Guest "save my progress": create an account, then create a server session and push the current
     * progress so it's resumable. On success the token store flips the app to the logged-in state.
     */
    fun saveProgressByCreatingAccount(email: String, password: String) {
        val targetDeckId = deckId ?: return
        _saveState.update { GuestSaveState.Saving }
        viewModelScope.launch {
            when (val result = authService.register(email, password)) {
                AuthResult.Success -> {
                    // Best-effort: the account exists either way; push the in-progress session if we can.
                    runCatching {
                        val session = apiClient.createSession(targetDeckId, mode)
                        apiClient.updateProgress(
                            session.id,
                            UpdateProgressRequest(currentFlashcardIndex, numCorrect, numIncorrect),
                        )
                    }
                    _saveState.update { GuestSaveState.Saved }
                }
                is AuthResult.Failure -> _saveState.update { GuestSaveState.Error(result.message) }
            }
        }
    }

    private suspend fun loadGuestDeck(deckId: Long, mode: String) {
        val deck = runCatching { apiClient.getCatalogDeck(deckId) }.getOrNull()
        val cards = deck?.flashcards.orEmpty()
            .map { Flashcard(it.question, it.answer, it.imageUrl, it.alternativeAnswers, it.cardUid) }
        if (deck == null || cards.isEmpty()) {
            _uiState.update { FlashcardsUiState.LoadingFailed }
            return
        }
        this.deckId = deckId
        this.deckTitle = deck.title
        sessionId = null
        flashcards = cards
        this.mode = mode
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

        deckId = session.deckId
        deckTitle = session.deckTitle
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
        if (currentFlashcardIndex < flashcards.size - 1) {
            currentFlashcardIndex++
            persistProgress()
            updateUiState()
        } else {
            // Last card: complete. For a server session, best-effort sync (offline it throws —
            // swallow it; local progress already drives the completion screen). Guests just finish.
            _uiState.update {
                FlashcardsUiState.SessionCompleted(numIncorrect = numIncorrect, numCorrect = numCorrect)
            }
            sessionId?.let { id ->
                viewModelScope.launch {
                    runCatching { practiceSessionRepository.completeSession(id) }
                    // Read the overall streak only after the completion lands, so it reflects the day
                    // just earned. Best-effort: a failure (or no streak) just leaves the badge off.
                    val streak = runCatching {
                        apiClient.getStreaks(ZoneId.systemDefault().id).overall.current
                    }.getOrNull()
                    if (streak != null && streak > 0) {
                        _uiState.update { state ->
                            if (state is FlashcardsUiState.SessionCompleted) state.copy(streak = streak) else state
                        }
                    }
                }
            }
        }
    }

    private fun persistProgress() {
        val currentSessionId = sessionId ?: return
        // Best-effort server sync; offline it throws, so swallow it (an uncaught failure here would
        // crash the app). In-memory progress keeps the session usable until connectivity returns.
        viewModelScope.launch {
            runCatching {
                practiceSessionRepository.updateProgress(
                    sessionId = currentSessionId,
                    currentCardIndex = currentFlashcardIndex,
                    numCorrect = numCorrect,
                    numIncorrect = numIncorrect,
                )
            }
        }
    }
}
