package com.rrbrambley.flashcards.shared.domain

import com.rrbrambley.flashcards.practice.grading.gradeTextAnswer
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
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
 * The "grade at the end" runner shared by Android + iOS (#293): loads every card of a session in one
 * list, lets the view collect an answer per card in any order, then on [submit] grades the whole set
 * at once (via the shared grading), records the batch + completes the session, and lands on the same
 * [BatchPracticeUiState.Completed] recap the card-by-card runner shows (#298).
 *
 * Only Test / Multiple Choice reach here — Classic has no objective grade to defer. A single sitting:
 * answers live in the view until submit (no per-card persistence, nothing to resume). Best-effort
 * persistence runs on the controller's own [dispatcher]-backed scope, cancelled by [close].
 */
class BatchPracticeController(
    private val flashcardRepository: FlashcardRepository,
    private val sessionRepository: PracticeSessionRepository,
    private val apiClient: FlashcardApiClient,
    private val entry: PracticeEntry,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    // Injectable clock (millis) so tests can drive the timed countdown deterministically.
    private val now: () -> Long = ::nowMillis,
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val _state = MutableStateFlow<BatchPracticeUiState>(BatchPracticeUiState.Loading)
    val state: StateFlow<BatchPracticeUiState> = _state.asStateFlow()

    // Remaining seconds for a timed batch run (#289); null = untimed. Ticks ~1×/sec to 0; the view
    // auto-submits whatever's answered when it reaches 0 (it owns the entries).
    private val _remainingSeconds = MutableStateFlow<Int?>(null)
    val remainingSeconds: StateFlow<Int?> = _remainingSeconds.asStateFlow()

    // Timed deadline (epoch millis; null = untimed) + the instant the clock was frozen, mirroring the
    // card-by-card runner (#311). Used here to hold the countdown while the opening prompt images load
    // (#372) rather than per card.
    private var deadlineMillis: Long? = null
    private var pausedAtMillis: Long? = null

    // Leading cards whose prompt image the clock is waiting on; emptied as each settles.
    private val awaitedPromptCards = mutableSetOf<Int>()
    private var holdTimeoutJob: Job? = null

    private var sessionId: Long? = null
    var deckId: Long? = null
        private set
    var deckTitle: String = ""
        private set

    private var cards: List<Flashcard> = emptyList()
    private var mode: String = PracticeMode.Test.key

    // Whether the practiced deck is global (catalog) — gates the Test-mode answer-suggestion action on
    // the recap (#338), since suggestions only apply to global decks.
    private var isGlobal: Boolean = false
    private var timerJob: Job? = null

    /** Starts the run per the [entry]. Suspends until the card list (or failure) is shown. */
    suspend fun start() {
        when (val e = entry) {
            is PracticeEntry.Deck -> {
                val sid = runCatching {
                    sessionRepository.startOrResumeSession(
                        e.deckId,
                        e.mode,
                        e.shuffle,
                        e.questionCount,
                        gradeAtEnd = true,
                        timeLimitSeconds = e.timeLimitSeconds,
                    )
                }.getOrNull()
                if (sid == null) {
                    _state.update { BatchPracticeUiState.Failed }
                    return
                }
                sessionId = sid
                loadSession(sid)
            }
            is PracticeEntry.Session -> {
                sessionId = e.sessionId
                loadSession(e.sessionId)
            }
            is PracticeEntry.GuestDeck -> loadGuestDeck(e)
        }
    }

    private suspend fun loadSession(sid: Long) {
        val session = sessionRepository.observeSession(sid).first()
        if (session == null || session.isCompleted) {
            _state.update { BatchPracticeUiState.Failed }
            return
        }
        val deck = flashcardRepository.observeFlashcardDeck(session.deckId).first()
        val deckCards = deck?.flashcards.orEmpty()
        if (deckCards.isEmpty()) {
            _state.update { BatchPracticeUiState.Failed }
            return
        }
        deckId = session.deckId
        deckTitle = session.deckTitle
        mode = session.mode
        isGlobal = deck?.isGlobal ?: false
        // Same stored, resume-stable order + subset the card-by-card runner uses (FLA-200 / FLA-219).
        cards = SessionOrdering.order(deckCards, session.shuffle, session.shuffleSeed)
            .let { ordered -> session.questionCount?.let(ordered::take) ?: ordered }
        _state.update { BatchPracticeUiState.Answering(cards = cards, mode = mode) }
        startTimer(session.timeLimitSeconds?.let { session.createdAtMillis + it * 1000L }, session.timeLimitSeconds)
    }

    private suspend fun loadGuestDeck(e: PracticeEntry.GuestDeck) {
        val deck = runCatching { apiClient.getCatalogDeck(e.deckId) }.getOrNull()
        val deckCards = deck?.flashcards.orEmpty().map {
            Flashcard(it.question, it.answer, it.imageUrl, it.alternativeAnswers, it.cardUid)
        }
        if (deck == null || deckCards.isEmpty()) {
            _state.update { BatchPracticeUiState.Failed }
            return
        }
        deckTitle = deck.title
        mode = e.mode
        // Guests only ever practice the public catalog, which is the global decks.
        isGlobal = true
        // Guests have no persisted session, so mint a seed here for a stable order (once per run).
        val seed = if (e.shuffle) Random.nextInt(1, Int.MAX_VALUE).toLong() else 0L
        cards = SessionOrdering.order(deckCards, e.shuffle, seed)
            .let { ordered -> e.questionCount?.let(ordered::take) ?: ordered }
        _state.update { BatchPracticeUiState.Answering(cards = cards, mode = mode) }
        // Guests have no persisted createdAt, so the timed deadline is minted here (once) from now().
        startTimer(e.timeLimitSeconds?.let { now() + it * 1000L }, e.timeLimitSeconds)
    }

    /**
     * Runs the timed countdown (#289) to [deadline] (epoch millis; null = untimed). Ticks the remaining
     * seconds ~1×/sec to 0. The view owns the per-card entries, so it watches [remainingSeconds] and
     * submits whatever's answered when it reaches 0 (a wall-clock deadline already past → 0 at once).
     *
     * Starts **held** when the leading cards have prompt images still to load (#372): a batch run puts
     * the whole deck on screen at once, so otherwise the clock burns while the first cards are still
     * spinners — a slow connection could eat much of the budget before anything is answerable. The hold
     * shifts the deadline forward, exactly as the card-by-card runner's pause does (#311); that's safe
     * with the wall-clock model because timed runs are single-sitting (#306).
     */
    private fun startTimer(deadline: Long?, limitSeconds: Int?) {
        timerJob?.cancel()
        holdTimeoutJob?.cancel()
        pausedAtMillis = null
        deadlineMillis = deadline
        if (deadline == null) return

        awaitedPromptCards.clear()
        cards.take(PROMPT_CARDS_BEFORE_START).forEachIndexed { index, card ->
            if (!card.imageUrl.isNullOrBlank()) awaitedPromptCards += index
        }
        if (awaitedPromptCards.isNotEmpty()) {
            pausedAtMillis = now()
            // A card that never reports — a list too short to compose it, a view that never appears —
            // would otherwise hold the clock indefinitely, so release regardless after a bounded wait.
            holdTimeoutJob = scope.launch {
                delay(MAX_PROMPT_HOLD_MILLIS)
                releasePromptHold()
            }
        }

        timerJob = scope.launch {
            while (true) {
                val d = deadlineMillis ?: break
                // While held, measure from the frozen instant so the clock holds; releasing has already
                // shifted the deadline forward, so it picks up where it left off.
                val paused = pausedAtMillis
                val remainingMs = d - (paused ?: now())
                if (remainingMs <= 0 && paused == null) {
                    _remainingSeconds.value = 0
                    break
                }
                // Never report more than the configured budget. The deadline is derived from the
                // session's stored createdAt, which is the *server's* clock, so a device running a
                // shade behind it makes the first remainder round up — a 60s run opening on "1:01"
                // (#374 review). Harmless when the clock ticks immediately, but the hold above freezes
                // that first value on screen for the whole load, which is where it became visible.
                val budgetMs = limitSeconds?.let { it * 1000L } ?: Long.MAX_VALUE
                val shownMs = remainingMs.coerceIn(0, budgetMs)
                _remainingSeconds.value = ((shownMs + 999) / 1000).toInt()
                delay(1000)
            }
        }
    }

    /**
     * Reported by the view when the card at [index]'s prompt image settles — **loaded or failed** (#372).
     * Failure has to count: the flags 404'd once already, and a hold that only ends on success would
     * have frozen those sessions outright. Cards past the opening screenful are ignored; they load in
     * the background while the early ones are answered.
     */
    fun onPromptImageSettled(index: Int) {
        if (awaitedPromptCards.remove(index) && awaitedPromptCards.isEmpty()) releasePromptHold()
    }

    /** Ends the hold, moving the deadline on by however long it lasted so no budget was spent. */
    private fun releasePromptHold() {
        holdTimeoutJob?.cancel()
        holdTimeoutJob = null
        awaitedPromptCards.clear()
        val pausedAt = pausedAtMillis ?: return
        val deadline = deadlineMillis ?: return
        deadlineMillis = deadline + (now() - pausedAt)
        pausedAtMillis = null
    }

    /**
     * Grades the whole session at once (#293). [answers] is aligned with the [Answering] card list:
     * the typed text (Test) or the chosen option's text (Multiple Choice) per card, or null/blank when
     * left unanswered (graded incorrect). Builds the recap immediately, then — for a signed-in run —
     * best-effort records the batch + completes the session + reads the streak.
     */
    fun submit(answers: List<String?>) {
        if (cards.isEmpty()) return
        timerJob?.cancel()
        holdTimeoutJob?.cancel()
        val graded = cards.mapIndexed { i, card -> gradeCard(card, answers.getOrNull(i)) }
        val numCorrect = graded.count { it.correct }
        val review = graded.mapIndexed { i, g ->
            val card = cards[i]
            ReviewItem(
                answerUid = "batch-$i",
                cardUid = card.cardUid.orEmpty(),
                question = card.question,
                answer = card.answer,
                imageUrl = card.imageUrl,
                correct = g.correct,
                submittedText = g.submittedText,
            )
        }
        _state.update {
            BatchPracticeUiState.Completed(
                numCorrect = numCorrect,
                numIncorrect = cards.size - numCorrect,
                review = review,
                mode = mode,
                isGlobal = isGlobal,
            )
        }
        persistAndComplete(graded)
    }

    private data class Graded(val correct: Boolean, val submittedText: String?)

    private fun gradeCard(card: Flashcard, raw: String?): Graded {
        val submitted = raw?.takeIf { it.isNotBlank() }
        val correct = if (mode == PracticeMode.Test.key) {
            gradeTextAnswer(raw.orEmpty(), card.answer, card.alternativeAnswers).correct
        } else {
            // Multiple Choice: the chosen option must be the correct answer (case-sensitive, like the web).
            submitted != null && submitted.trim() == card.answer.trim()
        }
        return Graded(correct, submitted)
    }

    private fun persistAndComplete(graded: List<Graded>) {
        val sid = sessionId ?: return // guests just finish — the recap is already shown
        scope.launch {
            // Log the batch in list order (recordAnswer mints each sequence), then complete the session.
            graded.forEachIndexed { i, g ->
                val cardUid = cards[i].cardUid.orEmpty()
                if (cardUid.isNotBlank()) {
                    runCatching { sessionRepository.recordAnswer(sid, cardUid, g.correct, g.submittedText) }
                }
            }
            runCatching { sessionRepository.completeSession(sid) }
            // Read the overall streak only after completion lands, so it reflects the day just earned.
            val streak = runCatching { apiClient.getStreaks(systemTimeZoneId()).overall.current }.getOrNull()
            if (streak != null && streak > 0) {
                _state.update { s -> if (s is BatchPracticeUiState.Completed) s.copy(streak = streak) else s }
            }
        }
    }

    /** Cancels the controller's coroutines (record/complete/streak). Call on teardown. */
    fun close() {
        scope.cancel()
    }

    companion object {
        /**
         * How many leading cards count as "the opening screenful" for the start gate (#372). Roughly
         * what fits on a phone at this card size; small enough that a list shorter than the whole deck
         * still composes them, so the hold ends on their reports rather than on the timeout.
         */
        internal const val PROMPT_CARDS_BEFORE_START = 3

        /** Longest the clock is held waiting for those images before it starts regardless. */
        internal const val MAX_PROMPT_HOLD_MILLIS = 10_000L
    }
}
