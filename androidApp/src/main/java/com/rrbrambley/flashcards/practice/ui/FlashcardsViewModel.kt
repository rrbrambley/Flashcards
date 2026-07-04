package com.rrbrambley.flashcards.practice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.auth.FeatureFlagRepository
import com.rrbrambley.flashcards.auth.FeatureFlags
import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.domain.FlashcardRepository
import com.rrbrambley.flashcards.shared.domain.GuestSaveState
import com.rrbrambley.flashcards.shared.domain.PracticeEntry
import com.rrbrambley.flashcards.shared.domain.PracticeMode
import com.rrbrambley.flashcards.shared.domain.PracticeSessionController
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import com.rrbrambley.flashcards.shared.domain.PracticeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin Android adapter over the shared [PracticeSessionController] (FLA-197): builds the controller for
 * the requested entry, re-exposes its `state`/`saveState`, and delegates the runner actions. The only
 * platform logic left here is applying the `discussions` feature flag to the shared (raw) opt-in — a
 * guest keeps read-only discussions, a signed-in user needs the flag on (mirrors iOS's view gate).
 */
@HiltViewModel
class FlashcardsViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val practiceSessionRepository: PracticeSessionRepository,
    private val apiClient: FlashcardApiClient,
    private val authService: AuthService,
    private val featureFlagRepository: FeatureFlagRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private val _saveState = MutableStateFlow<GuestSaveState>(GuestSaveState.Idle)
    val saveState: StateFlow<GuestSaveState> = _saveState.asStateFlow()

    private var controller: PracticeSessionController? = null
    private var loaded = false
    private var discussionsFlag = false

    fun load(sessionId: Long?, deckId: Long?, isGuest: Boolean = false, mode: String = PracticeMode.Classic.key) {
        if (loaded) return
        loaded = true
        val entry = when {
            isGuest && deckId != null -> PracticeEntry.GuestDeck(deckId, mode)
            sessionId != null -> PracticeEntry.Session(sessionId)
            deckId != null -> PracticeEntry.Deck(deckId, mode)
            else -> {
                _uiState.value = PracticeUiState.Failed
                return
            }
        }
        val c = PracticeSessionController(
            flashcardRepository,
            practiceSessionRepository,
            apiClient,
            authService,
            entry,
        )
        controller = c
        viewModelScope.launch {
            discussionsFlag = runCatching { featureFlagRepository.isEnabled(FeatureFlags.DISCUSSIONS) }.getOrDefault(false)
            launch { c.state.collect { state -> _uiState.value = applyDiscussionsFlag(state, c) } }
            launch { c.saveState.collect { _saveState.value = it } }
            c.start()
        }
    }

    /** The deck being practiced (id + title), for the Share action; null until loaded. */
    fun sharedDeck(): Pair<Long, String>? = controller?.let { c -> c.deckId?.let { it to c.deckTitle } }

    fun onResult(correct: Boolean, submittedText: String? = null) {
        controller?.onResult(correct, submittedText)
    }

    fun applyResult(correct: Boolean, submittedText: String? = null) {
        controller?.applyResult(correct, submittedText)
    }

    fun goBack() {
        controller?.goBack()
    }

    fun goForward() {
        controller?.goForward()
    }

    fun shouldPromptSave(): Boolean = controller?.shouldPromptSave ?: false

    fun saveProgressByCreatingAccount(email: String, password: String) {
        val c = controller ?: return
        viewModelScope.launch { c.saveProgressByCreatingAccount(email, password) }
    }

    // The shared controller emits the deck's raw discussions opt-in; the `discussions` flag is applied
    // here — guests bypass it (read-only), a signed-in user needs it enabled (FLA-180).
    private fun applyDiscussionsFlag(state: PracticeUiState, c: PracticeSessionController): PracticeUiState =
        if (state is PracticeUiState.ShowCard) {
            state.copy(discussionsEnabled = state.discussionsEnabled && (c.isGuest || discussionsFlag))
        } else {
            state
        }

    override fun onCleared() {
        controller?.close()
        super.onCleared()
    }
}
