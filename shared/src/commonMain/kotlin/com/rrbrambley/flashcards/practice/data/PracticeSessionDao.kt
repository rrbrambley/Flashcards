package com.rrbrambley.flashcards.practice.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeSessionDao {
    // pendingDelete rows are locally-removed sessions awaiting the backend DELETE (FLA-205) — hidden
    // from every read below so the card disappears immediately.
    @Transaction
    @Query("SELECT * FROM practice_sessions WHERE isCompleted = 0 AND pendingDelete = 0 ORDER BY updatedAtMillis DESC")
    fun observeActiveSessions(): Flow<List<PracticeSessionWithDeck>>

    @Transaction
    @Query("SELECT * FROM practice_sessions WHERE id = :sessionId AND pendingDelete = 0")
    fun observeSession(sessionId: Long): Flow<PracticeSessionWithDeck?>

    /** Most recent practice time per deck (across active and completed sessions); for sorting. */
    @Query(
        "SELECT deckId, MAX(updatedAtMillis) AS lastPracticedAtMillis FROM practice_sessions " +
            "WHERE pendingDelete = 0 GROUP BY deckId",
    )
    fun observeLastPracticedByDeck(): Flow<List<DeckLastPracticed>>

    /** Caches a backend session under its backend id (in-place update, no cascade). */
    @Upsert
    suspend fun upsertSession(session: PracticeSessionEntity)

    /** A single cached session by id (non-Flow), or null. Used by the offline write/sync paths. */
    @Query("SELECT * FROM practice_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): PracticeSessionEntity?

    /**
     * The most-recently-updated active (incomplete) session for this deck + mode, or null. Used to
     * resume an existing session offline rather than minting a duplicate (FLA-91).
     */
    @Query(
        "SELECT * FROM practice_sessions WHERE deckId = :deckId AND mode = :mode AND isCompleted = 0 " +
            "ORDER BY updatedAtMillis DESC LIMIT 1",
    )
    suspend fun findActiveByDeckAndMode(deckId: Long, mode: String): PracticeSessionEntity?

    /** Rows with local writes not yet flushed to the backend (FLA-91 sync); excludes pending-deletes. */
    @Query("SELECT * FROM practice_sessions WHERE pendingSync = 1 AND pendingDelete = 0")
    suspend fun getPendingSessions(): List<PracticeSessionEntity>

    /** Rows the user removed locally, awaiting the backend DELETE flush on reconnect (FLA-205). */
    @Query("SELECT * FROM practice_sessions WHERE pendingDelete = 1")
    suspend fun getPendingDeleteSessions(): List<PracticeSessionEntity>

    /** Marks a session as locally removed (a tombstone); syncPendingSessions flushes the DELETE. */
    @Query("UPDATE practice_sessions SET pendingDelete = 1 WHERE id = :id")
    suspend fun markPendingDelete(id: Long)

    /** Smallest existing id, or null when empty; used to mint a decreasing negative offline id. */
    @Query("SELECT MIN(id) FROM practice_sessions")
    suspend fun minSessionId(): Long?

    /** Deletes one cached session by id (used to remap an offline-minted row to its server id). */
    @Query("DELETE FROM practice_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    /**
     * Atomically mints a negative, decreasing offline id and inserts [session] under it, returning
     * the id. Negative ids mark "offline-minted, no server id yet" until reconnect remaps them.
     */
    @Transaction
    suspend fun insertLocalSession(session: PracticeSessionEntity): Long {
        val localId = (minSessionId() ?: 0L).coerceAtMost(0L) - 1L
        upsertSession(session.copy(id = localId))
        return localId
    }

    /** Clears every cached practice session (e.g. on logout). */
    @Query("DELETE FROM practice_sessions")
    suspend fun deleteAllSessions()
}
