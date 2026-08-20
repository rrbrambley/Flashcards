package com.rrbrambley.flashcards.practice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.auth.FeatureFlagRepository
import com.rrbrambley.flashcards.auth.FeatureFlags
import com.rrbrambley.flashcards.practice.voice.VoiceInputPreference
import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.domain.BatchPracticeController
import com.rrbrambley.flashcards.shared.domain.BatchPracticeUiState
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which practice runner drives the screen — resolved from the session's `gradeAtEnd` flag (#293). */
sealed interface FlashcardsScreenState {
    data object Loading : FlashcardsScreenState

    /** The card-by-card loop (Classic / Test / Multiple Choice), driven by [PracticeSessionController]. */
    data class CardByCard(val state: PracticeUiState) : FlashcardsScreenState

    /** The grade-at-the-end batch list, driven by [BatchPracticeController]. */
    data class Batch(val state: BatchPracticeUiState) : FlashcardsScreenState
}

/**
 * Thin Android adapter over the shared practice controllers (FLA-197): resolves the session's
 * `gradeAtEnd` flag (#293), then builds and drives the matching runner — the card-by-card
 * [PracticeSessionController] or the grade-at-the-end [BatchPracticeController] — re-exposing its state
 * as a [FlashcardsScreenState] and delegating the runner actions. The only platform logic left here is
 * applying the `discussions` feature flag to the shared (raw) opt-in — a guest keeps read-only
 * discussions, a signed-in user needs the flag on (mirrors iOS's view gate).
 */
@HiltViewModel
class FlashcardsViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val practiceSessionRepository: PracticeSessionRepository,
    private val apiClient: FlashcardApiClient,
    private val authService: AuthService,
    private val featureFlagRepository: FeatureFlagRepository,
    private val voiceInputPreference: VoiceInputPreference,
) : ViewModel() {
    private val _screenState = MutableStateFlow<FlashcardsScreenState>(FlashcardsScreenState.Loading)
    val screenState: StateFlow<FlashcardsScreenState> = _screenState.asStateFlow()

    private val _saveState = MutableStateFlow<GuestSaveState>(GuestSaveState.Idle)
    val saveState: StateFlow<GuestSaveState> = _saveState.asStateFlow()

    // Remaining seconds for a timed run (#289), mirrored from the active controller; null = untimed.
    private val _remainingSeconds = MutableStateFlow<Int?>(null)
    val remainingSeconds: StateFlow<Int?> = _remainingSeconds.asStateFlow()

    // Whether this run is single-sitting — timed / grade-at-the-end (#306) — so the screen hides the
    // exit affordance and warns on leaving while it's in progress.
    private val _isSingleSitting = MutableStateFlow(false)
    val isSingleSitting: StateFlow<Boolean> = _isSingleSitting.asStateFlow()

    // Answering by voice (#389): the flag decides whether it's *offered*, the local preference
    // whether it's *on*. Note `isEnabled` (fail-closed), NOT the `!= false` idiom the other practice
    // flags use — those are default-ON kill switches, this one is seeded off and dark-launched, so
    // failing open would ship it to everyone whose flag fetch merely failed.
    private val _voiceInputEnabled = MutableStateFlow(false)
    val voiceInputEnabled: StateFlow<Boolean> = _voiceInputEnabled.asStateFlow()

    // The one-time speech-processing disclosure (#402 review): shown until it's actually been seen,
    // then never again. Held as its own state so it can't flip mid-card and vanish as it's read.
    private val _showVoicePrivacyNotice = MutableStateFlow(false)
    val showVoicePrivacyNotice: StateFlow<Boolean> = _showVoicePrivacyNotice.asStateFlow()

    /** Called once the disclosure has been on screen for a whole card. */
    fun markVoicePrivacyNoticeSeen() {
        _showVoicePrivacyNotice.value = false
        viewModelScope.launch { voiceInputPreference.markPrivacyNoticeSeen() }
    }

    /** Switches voice off for good — offered by the panel when the microphone turns out to be blocked. */
    fun disableVoiceInput() {
        _voiceInputEnabled.value = false
        viewModelScope.launch { voiceInputPreference.setEnabled(false) }
    }

    private var controller: PracticeSessionController? = null
    private var batchController: BatchPracticeController? = null
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
                _screenState.value = FlashcardsScreenState.CardByCard(PracticeUiState.Failed)
                return
            }
        }
        viewModelScope.launch {
            discussionsFlag =
                runCatching { featureFlagRepository.isEnabled(FeatureFlags.DISCUSSIONS) }.getOrDefault(false)
            val voiceFlag =
                runCatching { featureFlagRepository.isEnabled(FeatureFlags.PRACTICE_VOICE_INPUT) }.getOrDefault(false)
            _voiceInputEnabled.value = voiceFlag &&
                runCatching { voiceInputPreference.enabled().first() }.getOrDefault(false)
            _showVoicePrivacyNotice.value = _voiceInputEnabled.value &&
                !runCatching { voiceInputPreference.privacyNoticeSeen().first() }.getOrDefault(true)
            // Resolve the run's settings: a stored session is authoritative (resume keeps its choice),
            // a deck/guest entry carries the picker's choice. Then drive the matching shared controller.
            val settings = resolveSettings(entry)
            _isSingleSitting.value = settings.gradeAtEnd || settings.timeLimitSeconds != null
            if (settings.gradeAtEnd) {
                startBatch(entry)
            } else {
                startCardByCard(entry)
            }
        }
    }

    private data class RunSettings(val gradeAtEnd: Boolean, val timeLimitSeconds: Int?)

    private suspend fun resolveSettings(entry: PracticeEntry): RunSettings = when (entry) {
        is PracticeEntry.Session -> {
            val session = runCatching { practiceSessionRepository.observeSession(entry.sessionId).first() }.getOrNull()
            RunSettings(session?.gradeAtEnd ?: false, session?.timeLimitSeconds)
        }
        is PracticeEntry.Deck -> RunSettings(entry.gradeAtEnd, entry.timeLimitSeconds)
        is PracticeEntry.GuestDeck -> RunSettings(entry.gradeAtEnd, entry.timeLimitSeconds)
    }

    private fun startCardByCard(entry: PracticeEntry) {
        val c = PracticeSessionController(
            flashcardRepository,
            practiceSessionRepository,
            apiClient,
            authService,
            entry,
        )
        controller = c
        viewModelScope.launch {
            launch {
                c.state.collect { state ->
                    _screenState.value = FlashcardsScreenState.CardByCard(applyDiscussionsFlag(state, c))
                }
            }
            launch { c.saveState.collect { _saveState.value = it } }
            launch { c.remainingSeconds.collect { _remainingSeconds.value = it } }
            c.start()
        }
    }

    private fun startBatch(entry: PracticeEntry) {
        val c = BatchPracticeController(flashcardRepository, practiceSessionRepository, apiClient, entry)
        batchController = c
        viewModelScope.launch {
            launch { c.state.collect { _screenState.value = FlashcardsScreenState.Batch(it) } }
            launch { c.remainingSeconds.collect { _remainingSeconds.value = it } }
            c.start()
        }
    }

    /** The deck being practiced (id + title), for the Share action; null until loaded. */
    fun sharedDeck(): Pair<Long, String>? = controller?.let { c -> c.deckId?.let { it to c.deckTitle } }
        ?: batchController?.let { c -> c.deckId?.let { it to c.deckTitle } }

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

    /** Grade the whole batch (#293): [answers] align with the list order (typed text / chosen option). */
    fun submitBatch(answers: List<String?>) {
        batchController?.submit(answers)
    }

    /**
     * Reports an opening batch card's prompt image settling (#372). The batch runner shows the whole
     * deck at once, so instead of pausing per card it holds the clock until the first few have drawn.
     */
    fun onBatchPromptImageSettled(index: Int) {
        batchController?.onPromptImageSettled(index)
    }

    /** Leaves the "Time's up" reveal for the completion recap (#375). */
    fun continueAfterTimeUp() {
        controller?.continueAfterTimeUp()
    }

    /** Pause/resume the timed countdown (#311) while the current card's prompt image loads. No-op untimed. */
    fun pauseTimer() {
        controller?.pauseTimer()
    }

    fun resumeTimer() {
        controller?.resumeTimer()
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
        batchController?.close()
        super.onCleared()
    }
}
