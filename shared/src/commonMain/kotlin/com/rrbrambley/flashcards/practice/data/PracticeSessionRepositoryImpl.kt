package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import com.rrbrambley.flashcards.shared.domain.PracticeSession
import com.rrbrambley.flashcards.shared.domain.PracticeSessionRepository
import com.rrbrambley.flashcards.shared.domain.PracticeSessionSyncer
import com.rrbrambley.flashcards.shared.nowMillis
import com.rrbrambley.flashcards.shared.systemTimeZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Offline-first practice sessions (FLA-91). Online, writes hit the backend first and cache the
 * returned state. Offline, sessions are minted with a **negative local id** and every write is
 * persisted to Room first (flagged `pendingSync`) so progress survives an app restart with no
 * connection. [syncPendingSessions] reconciles those rows to the backend on reconnect: it obtains a
 * server id (remapping the negative row to it) and replays the local progress, merging with any
 * existing server session for the same deck + mode. Reads serve the Room cache immediately while a
 * best-effort remote refresh runs in the background.
 */
class PracticeSessionRepositoryImpl(
    private val apiClient: FlashcardApiClient,
    private val practiceSessionDao: PracticeSessionDao,
    private val flashcardDao: FlashcardDao,
    private val now: () -> Long = ::nowMillis,
    private val timeZoneId: () -> String = ::systemTimeZoneId,
) : PracticeSessionRepository,
    PracticeSessionSyncer {

    // Single-flights syncPendingSessions so a connectivity flap / overlapping trigger can't double-create.
    private val syncMutex = Mutex()

    @Throws(Exception::class)
    override suspend fun startOrResumeSession(deckId: Long, mode: String): Long = try {
        // Start-or-resume is the backend's job (it keys on user+deck+mode); we cache the result.
        val session = apiClient.createSession(deckId, mode)
        cache(session)
        session.id
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Offline: resume the cached active session for this deck + mode, or mint a brand-new local
        // session (negative id, pendingSync) so a never-practiced deck is still startable offline.
        // The deck is already cached (offline practice is only offered on cached decks), so its row
        // satisfies the FK. syncPendingSessions reconciles the minted session on reconnect.
        practiceSessionDao.findActiveByDeckAndMode(deckId, mode)?.id
            ?: mintLocalSession(deckId, mode)
    }

    private suspend fun mintLocalSession(deckId: Long, mode: String): Long {
        val timestamp = now()
        return practiceSessionDao.insertLocalSession(
            PracticeSessionEntity(
                deckId = deckId,
                mode = mode,
                pendingSync = true,
                createdAtMillis = timestamp,
                updatedAtMillis = timestamp,
            ),
        )
    }

    override fun observeActiveSessions(): Flow<List<PracticeSession>> = flow {
        coroutineScope {
            launch { runCatching { apiClient.getAllSessions(activeOnly = true).forEach { cache(it) } } }
            emitAll(practiceSessionDao.observeActiveSessions().map { sessions -> sessions.map { it.toDomain() } })
        }
    }

    override fun observeSession(sessionId: Long): Flow<PracticeSession?> = flow {
        coroutineScope {
            // Only refresh server-owned sessions; an offline-minted (negative-id) session isn't there yet.
            if (sessionId > 0) launch { runCatching { cache(apiClient.getSession(sessionId)) } }
            emitAll(practiceSessionDao.observeSession(sessionId).map { it?.toDomain() })
        }
    }

    override fun observeLastPracticedByDeck(): Flow<Map<Long, Long>> =
        practiceSessionDao.observeLastPracticedByDeck().map { rows ->
            rows.associate { it.deckId to it.lastPracticedAtMillis }
        }

    override suspend fun updateProgress(sessionId: Long, currentCardIndex: Int, numCorrect: Int, numIncorrect: Int) {
        // Persist to the local row first (durable across restart, flagged for sync). A negative id
        // whose row was already remapped to a server id reads back null → clean no-op, never a
        // resurrected duplicate. A server id (id > 0) still pushes even without a cached row.
        val row = practiceSessionDao.getSessionById(sessionId)
        if (row != null) {
            practiceSessionDao.upsertSession(
                row.copy(
                    currentCardIndex = currentCardIndex,
                    numCorrect = numCorrect,
                    numIncorrect = numIncorrect,
                    updatedAtMillis = now(),
                    pendingSync = true,
                ),
            )
        } else if (sessionId < 0) {
            return
        }
        // Best-effort server sync for server-owned sessions; cache() clears pendingSync on success.
        if (sessionId > 0) {
            runCatching {
                cache(
                    apiClient.updateProgress(
                        sessionId,
                        UpdateProgressRequest(currentCardIndex, numCorrect, numIncorrect),
                    ),
                )
            }
        }
    }

    override suspend fun completeSession(sessionId: Long) {
        val row = practiceSessionDao.getSessionById(sessionId)
        if (row != null) {
            practiceSessionDao.upsertSession(row.copy(isCompleted = true, updatedAtMillis = now(), pendingSync = true))
        } else if (sessionId < 0) {
            return
        }
        if (sessionId > 0) {
            // Stamp the device tz with the completion so day-based streaks bucket locally (FLA-105).
            runCatching { cache(apiClient.completeSession(sessionId, timeZoneId())) }
        }
    }

    /**
     * Flushes every locally-pending session to the backend (FLA-91). Completed sessions first (no
     * live screen holds them). Per row, on any network failure we leave `pendingSync = true` and move
     * on, so the next reconnect retries. Single-flighted via [syncMutex].
     */
    override suspend fun syncPendingSessions() = syncMutex.withLock {
        for (row in practiceSessionDao.getPendingSessions().sortedByDescending { it.isCompleted }) {
            runCatching {
                if (row.id < 0) syncMintedSession(row) else pushServerSession(row)
            }
        }
    }

    /** An offline-minted (negative-id) row: obtain a server id, merge, then remap the row to it. */
    private suspend fun syncMintedSession(row: PracticeSessionEntity) {
        // createSession returns the existing active server session for this deck+mode, or a fresh one.
        val server = apiClient.createSession(row.deckId, row.mode)
        // Furthest-progress wins: a fresh server session is index 0, so local progress is pushed; a
        // server session strictly further along (e.g. another device) is kept as-is.
        var state = server
        if (row.currentCardIndex >= server.currentCardIndex) {
            state = apiClient.updateProgress(
                server.id,
                UpdateProgressRequest(row.currentCardIndex, row.numCorrect, row.numIncorrect),
            )
            if (row.isCompleted) state = apiClient.completeSession(server.id, timeZoneId())
        }
        // Remap: drop the negative row and cache the server state under its real id (pendingSync=false).
        // If a server-id row already exists it's updated in place — exactly one row per deck+mode.
        practiceSessionDao.deleteSessionById(row.id)
        cache(state)
    }

    /** A server-owned row (id > 0) with offline edits: replay the latest local state, then cache. */
    private suspend fun pushServerSession(row: PracticeSessionEntity) {
        var state = apiClient.updateProgress(
            row.id,
            UpdateProgressRequest(row.currentCardIndex, row.numCorrect, row.numIncorrect),
        )
        if (row.isCompleted) state = apiClient.completeSession(row.id, timeZoneId())
        cache(state)
    }

    private suspend fun cache(session: PracticeSessionDto) {
        // Ensure the session's deck exists locally (FK + relation) before caching the session.
        flashcardDao.insertDeckIfAbsent(session.toDeckStubEntity())
        practiceSessionDao.upsertSession(session.toEntity())
    }
}
