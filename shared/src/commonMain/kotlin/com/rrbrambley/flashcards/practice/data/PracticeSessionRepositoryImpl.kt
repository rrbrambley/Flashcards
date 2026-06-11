package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Sessions are owned by the backend. Writes (start/resume, progress, complete) hit the server
 * first and cache the returned state; reads serve the Room cache immediately while a best-effort
 * remote refresh runs in the background (the Room flow re-emits when it writes through).
 */
class PracticeSessionRepositoryImpl(
    private val apiClient: FlashcardApiClient,
    private val practiceSessionDao: PracticeSessionDao,
    private val flashcardDao: FlashcardDao,
) : PracticeSessionRepository {

    @Throws(Exception::class)
    override suspend fun startOrResumeSession(deckId: Long, mode: String): Long {
        // Start-or-resume is the backend's job (it keys on user+deck+mode); we just cache the result.
        val session = apiClient.createSession(deckId, mode)
        cache(session)
        return session.id
    }

    override fun observeActiveSessions(): Flow<List<PracticeSession>> = flow {
        coroutineScope {
            launch { runCatching { apiClient.getAllSessions(activeOnly = true).forEach { cache(it) } } }
            emitAll(practiceSessionDao.observeActiveSessions().map { sessions -> sessions.map { it.toDomain() } })
        }
    }

    override fun observeSession(sessionId: Long): Flow<PracticeSession?> = flow {
        coroutineScope {
            launch { runCatching { cache(apiClient.getSession(sessionId)) } }
            emitAll(practiceSessionDao.observeSession(sessionId).map { it?.toDomain() })
        }
    }

    override fun observeLastPracticedByDeck(): Flow<Map<Long, Long>> =
        practiceSessionDao.observeLastPracticedByDeck().map { rows ->
            rows.associate { it.deckId to it.lastPracticedAtMillis }
        }

    @Throws(Exception::class)
    override suspend fun updateProgress(sessionId: Long, currentCardIndex: Int, numCorrect: Int, numIncorrect: Int) {
        cache(
            apiClient.updateProgress(
                sessionId,
                UpdateProgressRequest(currentCardIndex, numCorrect, numIncorrect),
            ),
        )
    }

    @Throws(Exception::class)
    override suspend fun completeSession(sessionId: Long) {
        cache(apiClient.completeSession(sessionId))
    }

    private suspend fun cache(session: PracticeSessionDto) {
        // Ensure the session's deck exists locally (FK + relation) before caching the session.
        flashcardDao.insertDeckIfAbsent(session.toDeckStubEntity())
        practiceSessionDao.upsertSession(session.toEntity())
    }
}
