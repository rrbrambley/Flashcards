package com.rrbrambley.flashcards.shared.domain

import com.rrbrambley.flashcards.practice.grading.trailingCorrectStreak
import com.rrbrambley.flashcards.shared.AuthResult
import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import com.rrbrambley.flashcards.shared.systemTimeZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The mode-agnostic practice session runner shared by Android + iOS (FLA-197): start-or-resume (or
 * restore) a session, restore progress, grade + advance, persist each step, and — on completion —
 * read the overall streak and build the per-card review. Guest mode runs entirely in memory (no
 * session, no persistence) and offers to save by creating an account.
 *
 * State is exposed as a [StateFlow]; platforms observe it (Compose `collectAsState` / iOS `FlowAdapter`)
 * and keep only thin view models. Best-effort async work (record-answer, persist, complete, streak,
 * review) runs on the controller's own [dispatcher]-backed scope, cancelled by [close] — call it from
 * the platform's teardown (Android `onCleared`, iOS view-model deinit). Tests inject a test dispatcher.
 */
class PracticeSessionController(
    private val flashcardRepository: FlashcardRepository,
    private val sessionRepository: PracticeSessionRepository,
    private val apiClient: FlashcardApiClient,
    private val authService: AuthService?,
    private val entry: PracticeEntry,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    // The controller owns its scope so it's independent of any caller's lifetime; [close] cancels it.
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val _state = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    private val _saveState = MutableStateFlow<GuestSaveState>(GuestSaveState.Idle)
    val saveState: StateFlow<GuestSaveState> = _saveState.asStateFlow()

    private var sessionId: Long? = null
    var deckId: Long? = null
        private set
    var deckTitle: String = ""
        private set
    var isGuest: Boolean = false
        private set

    private var cards: List<Flashcard> = emptyList()
    private var index = 0
    private var numCorrect = 0
    private var numIncorrect = 0
    private var mode: String = PracticeMode.Classic.key
    private var shuffle = false
    private var shuffleSeed = 0L
    private var discussionsEnabled = false
    private var isGlobal = false
    private var answerStreak = 0
    private var reviewJob: Job? = null

    /** Whether leaving now should prompt a guest to save: guest, mid-session, with some progress. */
    val shouldPromptSave: Boolean
        get() = isGuest &&
            _state.value is PracticeUiState.ShowCard &&
            (index > 0 || numCorrect > 0 || numIncorrect > 0)

    /** Starts the run per the [entry]. Suspends until the first card (or failure) is shown. */
    suspend fun start() {
        reviewJob?.cancel()
        when (val e = entry) {
            is PracticeEntry.Deck -> {
                val sid = runCatching {
                    sessionRepository.startOrResumeSession(e.deckId, e.mode, e.shuffle)
                }.getOrNull()
                if (sid == null) {
                    _state.update { PracticeUiState.Failed }
                    return
                }
                sessionId = sid
                loadSession(sid)
            }
            is PracticeEntry.Session -> {
                sessionId = e.sessionId
                loadSession(e.sessionId)
            }
            is PracticeEntry.GuestDeck -> {
                isGuest = true
                deckId = e.deckId
                mode = e.mode
                // No session persists a guest seed, so mint one here (once per run) for a stable order.
                shuffle = e.shuffle
                shuffleSeed = if (e.shuffle) Random.nextInt(1, Int.MAX_VALUE).toLong() else 0L
                loadGuestDeck(e.deckId)
            }
        }
    }

    private suspend fun loadSession(sid: Long) {
        val session = sessionRepository.observeSession(sid).first()
        if (session == null || session.isCompleted) {
            _state.update { PracticeUiState.Failed }
            return
        }
        val deck = flashcardRepository.observeFlashcardDeck(session.deckId).first()
        val deckCards = deck?.flashcards.orEmpty()
        if (deckCards.isEmpty()) {
            _state.update { PracticeUiState.Failed }
            return
        }
        deckId = session.deckId
        deckTitle = session.deckTitle
        // Apply the session's stored shuffle order (FLA-200); the seed makes it stable across resume.
        shuffle = session.shuffle
        shuffleSeed = session.shuffleSeed
        cards = SessionOrdering.order(deckCards, session.shuffle, session.shuffleSeed)
        mode = session.mode
        // The discussions + global flags travel with the cached deck (FLA-122/134), available offline.
        discussionsEnabled = deck?.discussionsEnabled ?: false
        isGlobal = deck?.isGlobal ?: false
        index = session.currentCardIndex.coerceIn(0, cards.lastIndex)
        numCorrect = session.numCorrect
        numIncorrect = session.numIncorrect
        // Restore the in-session streak (FLA-99) from the answer log — the trailing run of consecutive
        // corrects — so resuming a session doesn't reset it. It's derived (every grade is logged), not
        // persisted; a fresh/empty log yields 0.
        val loggedAnswers = runCatching { sessionRepository.observeAnswers(session.id).first() }.getOrNull().orEmpty()
        answerStreak = trailingCorrectStreak(loggedAnswers.sortedBy { it.sequence }.map { it.correct })
        updateState()
    }

    private suspend fun loadGuestDeck(deckId: Long) {
        val deck = runCatching { apiClient.getCatalogDeck(deckId) }.getOrNull()
        val deckCards = deck?.flashcards.orEmpty().map {
            Flashcard(it.question, it.answer, it.imageUrl, it.alternativeAnswers, it.cardUid)
        }
        if (deck == null || deckCards.isEmpty()) {
            _state.update { PracticeUiState.Failed }
            return
        }
        deckTitle = deck.title
        discussionsEnabled = deck.discussionsEnabled
        isGlobal = deck.isGlobal
        // Guests have no persisted session; the seed minted in start() keeps this order stable per run.
        cards = SessionOrdering.order(deckCards, shuffle, shuffleSeed)
        index = 0
        numCorrect = 0
        numIncorrect = 0
        answerStreak = 0
        updateState()
    }

    /**
     * Grades the current card and advances in one step — the Classic swipe (reveal + score together).
     * [submittedText] is null for the flip.
     */
    fun onResult(correct: Boolean, submittedText: String? = null) {
        applyResult(correct, submittedText)
        goForward()
    }

    /**
     * Applies a graded outcome for the current card — score, in-session streak (FLA-99), and the answer
     * log — *without* advancing, so the streak badge surfaces on the revealed answer. Test/Multiple-
     * Choice call this on the verdict, then advance via [goForward] on Next.
     */
    fun applyResult(correct: Boolean, submittedText: String? = null) {
        if (correct) numCorrect++ else numIncorrect++
        answerStreak = if (correct) answerStreak + 1 else 0
        recordAnswer(correct, submittedText)
        updateState()
    }

    private fun recordAnswer(correct: Boolean, submittedText: String?) {
        val sid = sessionId ?: return // guests have no session
        val cardUid = cards.getOrNull(index)?.cardUid.orEmpty()
        if (cardUid.isBlank()) return
        scope.launch { runCatching { sessionRepository.recordAnswer(sid, cardUid, correct, submittedText) } }
    }

    fun goBack() {
        if (index > 0) {
            index--
            updateState()
            persist()
        }
    }

    fun goForward() {
        if (index < cards.size - 1) {
            index++
            updateState()
            persist()
        } else {
            _state.update { PracticeUiState.Completed(numCorrect = numCorrect, numIncorrect = numIncorrect) }
            complete()
        }
    }

    /**
     * Guest "save my progress": create an account, then push the in-progress session so it's resumable.
     * On success the token store flips the app to the signed-in state. Callers gate blank credentials
     * in the UI (the submit is disabled when empty).
     */
    suspend fun saveProgressByCreatingAccount(email: String, password: String) {
        val targetDeckId = deckId ?: return
        val auth = authService ?: return
        _saveState.update { GuestSaveState.Saving }
        when (val result = auth.register(email, password)) {
            AuthResult.Success -> {
                // Best-effort: the account exists either way; push the in-progress session if we can.
                runCatching {
                    val session = apiClient.createSession(targetDeckId, mode, shuffle)
                    apiClient.updateProgress(
                        session.id,
                        UpdateProgressRequest(index, numCorrect, numIncorrect),
                    )
                }
                _saveState.update { GuestSaveState.Saved }
            }
            is AuthResult.Failure -> _saveState.update { GuestSaveState.Error(result.message) }
        }
    }

    private fun updateState() {
        if (cards.isEmpty()) {
            _state.update { PracticeUiState.Failed }
            return
        }
        _state.update {
            PracticeUiState.ShowCard(
                card = cards[index],
                position = index,
                numCorrect = numCorrect,
                numIncorrect = numIncorrect,
                canGoBack = index > 0,
                mode = mode,
                deck = cards,
                discussionsEnabled = discussionsEnabled,
                isGlobal = isGlobal,
                streak = answerStreak,
            )
        }
    }

    private fun persist() {
        val sid = sessionId ?: return // guests have no session
        val i = index
        val c = numCorrect
        val w = numIncorrect
        scope.launch { runCatching { sessionRepository.updateProgress(sid, i, c, w) } }
    }

    private fun complete() {
        val sid = sessionId ?: return // guests just finish
        scope.launch {
            runCatching { sessionRepository.completeSession(sid) }
            // Read the overall streak only after completion lands, so it reflects the day just earned.
            val streak = runCatching { apiClient.getStreaks(systemTimeZoneId()).overall.current }.getOrNull()
            if (streak != null && streak > 0) {
                _state.update { s -> if (s is PracticeUiState.Completed) s.copy(streak = streak) else s }
            }
        }
        // Per-card recap (FLA-149): observe the answer log so the review fills in as Room syncs.
        reviewJob?.cancel()
        reviewJob = scope.launch {
            sessionRepository.observeAnswers(sid).collect { answers ->
                val review = answers.sortedBy { it.sequence }.map { answer ->
                    val card = cards.firstOrNull { it.cardUid == answer.cardUid }
                    ReviewItem(
                        answerUid = answer.answerUid,
                        cardUid = answer.cardUid,
                        question = card?.question.orEmpty(),
                        answer = card?.answer.orEmpty(),
                        imageUrl = card?.imageUrl,
                        correct = answer.correct,
                        submittedText = answer.submittedText,
                    )
                }
                _state.update { s -> if (s is PracticeUiState.Completed) s.copy(review = review) else s }
            }
        }
    }

    /** Cancels the controller's coroutines (record/persist/complete/review). Call on teardown. */
    fun close() {
        scope.cancel()
    }
}
