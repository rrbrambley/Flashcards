package com.rrbrambley.flashcards.practice.data

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import com.rrbrambley.flashcards.shared.domain.PracticeAnswer
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
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
    private val practiceAnswerDao: PracticeAnswerDao,
    private val now: () -> Long = ::nowMillis,
    private val timeZoneId: () -> String = ::systemTimeZoneId,
) : PracticeSessionRepository,
    PracticeSessionSyncer {

    // Single-flights syncPendingSessions so a connectivity flap / overlapping trigger can't double-create.
    private val syncMutex = Mutex()

    @Throws(Exception::class)
    override suspend fun startOrResumeSession(deckId: Long, mode: String, shuffle: Boolean): Long = try {
        // Start-or-resume is the backend's job (it keys on user+deck+mode); we cache the result.
        val session = apiClient.createSession(deckId, mode, shuffle)
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
            ?: mintLocalSession(deckId, mode, shuffle)
    }

    private suspend fun mintLocalSession(deckId: Long, mode: String, shuffle: Boolean): Long {
        val timestamp = now()
        return practiceSessionDao.insertLocalSession(
            PracticeSessionEntity(
                deckId = deckId,
                mode = mode,
                // A local seed so the offline session's order is stable across restart; on reconnect
                // the server mints its own authoritative seed (see syncMintedSession). 0 when unshuffled.
                shuffle = shuffle,
                shuffleSeed = if (shuffle) newShuffleSeed() else 0L,
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

    override suspend fun deleteSession(sessionId: Long) {
        // Offline-minted (negative id) sessions never reached the server — just drop the local row.
        if (sessionId < 0) {
            practiceSessionDao.deleteSessionById(sessionId)
            return
        }
        // Server-owned: try to delete now and hard-remove on success. Offline / failure → tombstone it
        // (pendingDelete) so the card disappears immediately; syncPendingSessions flushes the DELETE
        // on reconnect. A tombstoned row is skipped by cache(), so the background pull can't resurrect it.
        if (runCatching { apiClient.deleteSession(sessionId) }.isSuccess) {
            practiceSessionDao.deleteSessionById(sessionId)
        } else {
            practiceSessionDao.markPendingDelete(sessionId)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun recordAnswer(sessionId: Long, cardUid: String, correct: Boolean, submittedText: String?) {
        // Persist locally first (durable, flagged for sync). Sequence = next play-order slot; answerUid
        // is a UUID so the backend can upsert idempotently across re-syncs.
        val sequence = (practiceAnswerDao.maxSequence(sessionId) ?: -1) + 1
        val entity = PracticeAnswerEntity(
            sessionId = sessionId,
            answerUid = Uuid.random().toString(),
            cardUid = cardUid,
            correct = correct,
            sequence = sequence,
            answeredAtMillis = now(),
            submittedText = submittedText,
            pendingSync = true,
        )
        practiceAnswerDao.upsert(entity)
        // Best-effort flush for server-owned sessions; offline-minted (negative id) answers ride the
        // session remap in syncPendingSessions. cache() refreshes the server-recomputed counts.
        if (sessionId > 0) {
            runCatching {
                cache(apiClient.recordAnswers(sessionId, listOf(entity.toDto())))
                practiceAnswerDao.markSynced(sessionId, listOf(entity.answerUid))
            }
        }
    }

    override fun observeAnswers(sessionId: Long): Flow<List<PracticeAnswer>> = flow {
        coroutineScope {
            // Background pull for cross-device review — but never clobber unsynced local writes, so
            // skip the pull while this session still has pending answers.
            if (sessionId > 0) {
                launch {
                    runCatching {
                        if (practiceAnswerDao.getAnswers(sessionId).none { it.pendingSync }) {
                            val server = apiClient.getAnswers(sessionId)
                            practiceAnswerDao.replaceForSession(sessionId, server.map { it.toEntity(sessionId) })
                        }
                    }
                }
            }
            emitAll(practiceAnswerDao.observeAnswers(sessionId).map { rows -> rows.map { it.toDomain() } })
        }
    }

    /**
     * Flushes every locally-pending session to the backend (FLA-91). Completed sessions first (no
     * live screen holds them). Per row, on any network failure we leave `pendingSync = true` and move
     * on, so the next reconnect retries. Single-flighted via [syncMutex].
     */
    override suspend fun syncPendingSessions() = syncMutex.withLock {
        // Flush user-requested removals first (FLA-205): hard-delete on the server, then drop the local
        // tombstone. On failure the row stays pendingDelete (still hidden) and the next reconnect retries.
        for (row in practiceSessionDao.getPendingDeleteSessions()) {
            runCatching {
                apiClient.deleteSession(row.id)
                practiceSessionDao.deleteSessionById(row.id)
            }
        }
        for (row in practiceSessionDao.getPendingSessions().sortedByDescending { it.isCompleted }) {
            runCatching {
                if (row.id < 0) syncMintedSession(row) else pushServerSession(row)
            }
        }
        // Sessions are reconciled first (minted ones remapped + their answers re-pointed to the real
        // id), so every pending answer now hangs off a server id and can be flushed (FLA-99).
        syncPendingAnswers()
    }

    /** Flushes locally-pending answers per session to the backend, marking them synced on success. */
    private suspend fun syncPendingAnswers() {
        val pendingBySession = practiceAnswerDao.getPendingAnswers()
            .filter { it.sessionId > 0 }
            .groupBy { it.sessionId }
        for ((sessionId, answers) in pendingBySession) {
            runCatching {
                cache(apiClient.recordAnswers(sessionId, answers.map { it.toDto() }))
                practiceAnswerDao.markSynced(sessionId, answers.map { it.answerUid })
            }
        }
    }

    /** An offline-minted (negative-id) row: obtain a server id, merge, then remap the row to it. */
    private suspend fun syncMintedSession(row: PracticeSessionEntity) {
        // createSession returns the existing active server session for this deck+mode, or a fresh one.
        // Carry the shuffle flag so a fresh server session shuffles too; the server mints its own seed
        // (authoritative), which cache() then writes over the local one — the live run keeps its order.
        val server = apiClient.createSession(row.deckId, row.mode, row.shuffle)
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
        // Remap: cache the server state under its real id first (so the FK target exists), re-point
        // this session's answers onto it — otherwise deleting the negative row would CASCADE them away
        // (FLA-99) — then drop the negative row. If a server-id row already exists it's updated in
        // place, so there's exactly one row per deck+mode.
        cache(state)
        practiceAnswerDao.reassignAnswers(row.id, state.id)
        practiceSessionDao.deleteSessionById(row.id)
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

    // A positive, JS-safe (< 2^31) shuffle seed so it round-trips through the web app's JSON numbers.
    private fun newShuffleSeed(): Long = Random.nextInt(1, Int.MAX_VALUE).toLong()

    private suspend fun cache(session: PracticeSessionDto) {
        // Don't resurrect a session the user removed locally (FLA-205): a background getAllSessions
        // pull would otherwise re-upsert it (clearing pendingDelete) before the DELETE is flushed.
        if (practiceSessionDao.getSessionById(session.id)?.pendingDelete == true) return
        // Ensure the session's deck exists locally (FK + relation) before caching the session.
        flashcardDao.insertDeckIfAbsent(session.toDeckStubEntity())
        practiceSessionDao.upsertSession(session.toEntity())
    }
}
