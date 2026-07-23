package com.rrbrambley.flashcards.shared.domain

import com.rrbrambley.flashcards.practice.grading.trailingCorrectStreak
import com.rrbrambley.flashcards.shared.AuthResult
import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import com.rrbrambley.flashcards.shared.nowMillis
import com.rrbrambley.flashcards.shared.systemTimeZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    // Injectable clock (millis) so tests can drive the timed countdown deterministically.
    private val now: () -> Long = ::nowMillis,
) {
    // The controller owns its scope so it's independent of any caller's lifetime; [close] cancels it.
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val _state = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    private val _saveState = MutableStateFlow<GuestSaveState>(GuestSaveState.Idle)
    val saveState: StateFlow<GuestSaveState> = _saveState.asStateFlow()

    // Remaining seconds for a timed session (#289); null = untimed. Ticks ~1×/sec from the deadline
    // (createdAt + timeLimit); the run auto-completes at 0. Platforms show it as a m:ss countdown.
    private val _remainingSeconds = MutableStateFlow<Int?>(null)
    val remainingSeconds: StateFlow<Int?> = _remainingSeconds.asStateFlow()

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

    // Guest-run subset size (FLA-219); a signed-in run reads it from the stored session instead.
    private var questionCount: Int? = null

    // Guest-run time limit (#289); a signed-in run reads it from the stored session instead.
    private var timeLimitSeconds: Int? = null
    private var discussionsEnabled = false
    private var isGlobal = false
    private var answerStreak = 0
    private var reviewJob: Job? = null
    private var timerJob: Job? = null

    // Timed-session deadline (epoch millis; null = untimed) + a pause accumulator (#311). While the
    // current card's prompt image loads, [pauseTimer] freezes the countdown; [resumeTimer] shifts the
    // deadline forward by the paused span so it doesn't eat into the budget. Safe with the wall-clock
    // model (#289) because timed runs are single-sitting (#306) — the deadline lives only for this run.
    private var deadlineMillis: Long? = null
    private var pausedAtMillis: Long? = null

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
                    sessionRepository.startOrResumeSession(
                        e.deckId,
                        e.mode,
                        e.shuffle,
                        e.questionCount,
                        e.gradeAtEnd,
                        e.timeLimitSeconds,
                    )
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
                questionCount = e.questionCount
                timeLimitSeconds = e.timeLimitSeconds
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
        // Order, then take the subset (FLA-219): questionCount cards, or the whole deck when null.
        cards = SessionOrdering.order(deckCards, session.shuffle, session.shuffleSeed)
            .let { ordered -> session.questionCount?.let(ordered::take) ?: ordered }
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
        // Timed session (#289): the deadline is wall-clock from creation, so it keeps running while away.
        startTimer(session.timeLimitSeconds?.let { session.createdAtMillis + it * 1000L })
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
        // Take the subset (FLA-219) after ordering, or the whole deck when null.
        cards = SessionOrdering.order(deckCards, shuffle, shuffleSeed)
            .let { ordered -> questionCount?.let(ordered::take) ?: ordered }
        index = 0
        numCorrect = 0
        numIncorrect = 0
        answerStreak = 0
        updateState()
        // Guests have no persisted createdAt, so the timed deadline is minted here (once) from now().
        startTimer(timeLimitSeconds?.let { now() + it * 1000L })
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
            completeRun()
        }
    }

    /** Ends the run wherever it is — the last card finishing, or the timed countdown hitting 0 (#289). */
    private fun completeRun() {
        timerJob?.cancel()
        _state.update { PracticeUiState.Completed(numCorrect = numCorrect, numIncorrect = numIncorrect) }
        complete()
    }

    /**
     * Runs the timed countdown (#289) to [deadline] (epoch millis; null = untimed → no timer). Ticks the
     * remaining seconds ~1×/sec and auto-completes the run at expiry. Wall-clock, so a deadline already
     * past at load completes immediately (resuming after time ran out lands on "complete").
     */
    private fun startTimer(deadline: Long?) {
        timerJob?.cancel()
        pausedAtMillis = null
        deadlineMillis = deadline
        if (deadline == null) return
        timerJob = scope.launch {
            while (true) {
                val d = deadlineMillis ?: break
                // While paused (#311), measure remaining from the frozen pause instant so it holds; when
                // resumed the deadline has already been shifted forward, so it picks up where it left off.
                val paused = pausedAtMillis
                val remainingMs = d - (paused ?: now())
                if (remainingMs <= 0 && paused == null) {
                    _remainingSeconds.value = 0
                    if (_state.value is PracticeUiState.ShowCard) completeRun()
                    break
                }
                // Ceil so the clock reads 1 until it truly hits 0 (matches the web's formatRemaining).
                _remainingSeconds.value = ((remainingMs.coerceAtLeast(0) + 999) / 1000).toInt()
                delay(1000)
            }
        }
    }

    /** Pauses the timed countdown (#311) — e.g. while the current card's prompt image loads. No-op if
     *  untimed or already paused. */
    fun pauseTimer() {
        if (deadlineMillis != null && pausedAtMillis == null) pausedAtMillis = now()
    }

    /** Resumes the countdown, shifting the deadline forward by the paused span so no time was lost. */
    fun resumeTimer() {
        val pausedAt = pausedAtMillis ?: return
        val deadline = deadlineMillis ?: return
        deadlineMillis = deadline + (now() - pausedAt)
        pausedAtMillis = null
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
