package com.rrbrambley.flashcards.shared.domain

import com.rrbrambley.flashcards.practice.grading.gradeTextAnswer
import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.systemTimeZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val _state = MutableStateFlow<BatchPracticeUiState>(BatchPracticeUiState.Loading)
    val state: StateFlow<BatchPracticeUiState> = _state.asStateFlow()

    private var sessionId: Long? = null
    var deckId: Long? = null
        private set
    var deckTitle: String = ""
        private set

    private var cards: List<Flashcard> = emptyList()
    private var mode: String = PracticeMode.Test.key

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
        // Same stored, resume-stable order + subset the card-by-card runner uses (FLA-200 / FLA-219).
        cards = SessionOrdering.order(deckCards, session.shuffle, session.shuffleSeed)
            .let { ordered -> session.questionCount?.let(ordered::take) ?: ordered }
        _state.update { BatchPracticeUiState.Answering(cards = cards, mode = mode) }
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
        // Guests have no persisted session, so mint a seed here for a stable order (once per run).
        val seed = if (e.shuffle) Random.nextInt(1, Int.MAX_VALUE).toLong() else 0L
        cards = SessionOrdering.order(deckCards, e.shuffle, seed)
            .let { ordered -> e.questionCount?.let(ordered::take) ?: ordered }
        _state.update { BatchPracticeUiState.Answering(cards = cards, mode = mode) }
    }

    /**
     * Grades the whole session at once (#293). [answers] is aligned with the [Answering] card list:
     * the typed text (Test) or the chosen option's text (Multiple Choice) per card, or null/blank when
     * left unanswered (graded incorrect). Builds the recap immediately, then — for a signed-in run —
     * best-effort records the batch + completes the session + reads the streak.
     */
    fun submit(answers: List<String?>) {
        if (cards.isEmpty()) return
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
}
