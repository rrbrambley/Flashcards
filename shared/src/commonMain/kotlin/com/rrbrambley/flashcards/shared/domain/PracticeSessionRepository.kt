package com.rrbrambley.flashcards.shared.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface PracticeSessionRepository {
    // @Throws so failures bridge to catchable Swift errors on iOS instead of crashing; no effect on
    // Kotlin (JVM/Android) callers. See FLA-57.
    @Throws(Exception::class)
    suspend fun startOrResumeSession(
        deckId: Long,
        mode: String = "flashcards",
        shuffle: Boolean = false,
        // A subset of the deck to practice (FLA-219); null = the whole deck. Fixed at creation.
        questionCount: Int? = null,
    ): Long
    fun observeActiveSessions(): Flow<List<PracticeSession>>
    fun observeSession(sessionId: Long): Flow<PracticeSession?>

    /** deckId -> the deck's most recent practice time (millis), for "recently practiced" sorting. */
    fun observeLastPracticedByDeck(): Flow<Map<Long, Long>> = flowOf(emptyMap())

    @Throws(Exception::class)
    suspend fun updateProgress(sessionId: Long, currentCardIndex: Int, numCorrect: Int, numIncorrect: Int)

    @Throws(Exception::class)
    suspend fun completeSession(sessionId: Long)

    /**
     * Removes (discards) an in-progress session — the home "×" action (FLA-205). Offline-first: a
     * server-owned session is tombstoned locally (hidden immediately) and the backend DELETE is
     * flushed on reconnect; an offline-minted one is just dropped. Default no-op so fakes compile.
     */
    @Throws(Exception::class)
    suspend fun deleteSession(sessionId: Long) {}

    /**
     * Appends an answer to the session's log (FLA-99): mints its play-order `sequence` + a UUID,
     * persists locally (offline-first), and best-effort flushes to the backend. Default no-op so
     * existing fakes/tests compile unchanged.
     */
    @Throws(Exception::class)
    suspend fun recordAnswer(sessionId: Long, cardUid: String, correct: Boolean, submittedText: String? = null) {}

    /** The session's answer log in play order — drives the in-session streak + end-of-session review. */
    fun observeAnswers(sessionId: Long): Flow<List<PracticeAnswer>> = flowOf(emptyList())
}
