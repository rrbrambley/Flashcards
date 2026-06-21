package com.rrbrambley.flashcards.backend.sessions

import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.PracticeSessions
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.mapping.toPracticeSessionDto
import com.rrbrambley.flashcards.backend.routes.Cursor
import com.rrbrambley.flashcards.shared.api.Page
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

object SessionRepository {

    /**
     * Resume the active session for this (deck, mode) if one exists, else create one. Keying on mode
     * means a user can have concurrent in-progress sessions on the same deck in different modes (e.g.
     * classic + test), each resuming to its own mode.
     */
    suspend fun startOrResume(userId: Long, deckId: Long, mode: String): PracticeSessionDto = dbQuery {
        val deckTitle = visibleDeckTitle(userId, deckId)
            ?: throw NotFoundException("Deck $deckId not found")

        val active = PracticeSessions.selectAll()
            .where {
                (PracticeSessions.userId eq userId) and
                    (PracticeSessions.deckId eq deckId) and
                    (PracticeSessions.mode eq mode) and
                    (PracticeSessions.isCompleted eq false)
            }
            .firstOrNull()
        if (active != null) return@dbQuery active.toPracticeSessionDto(deckTitle)

        val now = System.currentTimeMillis()
        val newId = PracticeSessions.insertAndGetId {
            it[PracticeSessions.userId] = userId
            it[PracticeSessions.deckId] = deckId
            it[PracticeSessions.mode] = mode
            it[createdAtMillis] = now
            it[updatedAtMillis] = now
        }.value
        PracticeSessionDto(
            id = newId,
            deckId = deckId,
            deckTitle = deckTitle,
            currentCardIndex = 0,
            numCorrect = 0,
            numIncorrect = 0,
            isCompleted = false,
            mode = mode,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    }

    /**
     * One page of the user's sessions (optionally only active ones), most-recently-updated first.
     * The cursor packs (updatedAtMillis, id); id is the tiebreaker so rows sharing an
     * updatedAtMillis can't straddle a page boundary and get skipped or duplicated.
     */
    suspend fun listSessions(userId: Long, activeOnly: Boolean, limit: Int, cursor: String?): Page<PracticeSessionDto> =
        dbQuery {
            val after = cursor?.let { decodeSessionCursor(it) }
            val query = (PracticeSessions innerJoin Decks)
                .selectAll()
                .where {
                    if (activeOnly) {
                        (PracticeSessions.userId eq userId) and (PracticeSessions.isCompleted eq false)
                    } else {
                        PracticeSessions.userId eq userId
                    }
                }
            if (after != null) {
                val (updatedAt, id) = after
                query.andWhere {
                    (PracticeSessions.updatedAtMillis less updatedAt) or
                        (
                            (PracticeSessions.updatedAtMillis eq updatedAt) and
                                (PracticeSessions.id less id)
                            )
                }
            }
            val rows = query
                .orderBy(
                    PracticeSessions.updatedAtMillis to SortOrder.DESC,
                    PracticeSessions.id to SortOrder.DESC,
                )
                .limit(limit + 1)
                .toList()

            val pageRows = rows.take(limit)
            val nextCursor = if (rows.size > limit) {
                val last = pageRows.last()
                Cursor.encode("${last[PracticeSessions.updatedAtMillis]}:${last[PracticeSessions.id].value}")
            } else {
                null
            }
            Page(items = pageRows.map { it.toPracticeSessionDto(it[Decks.title]) }, nextCursor = nextCursor)
        }

    /** Decodes a session cursor into its (updatedAtMillis, id) ordering key. */
    private fun decodeSessionCursor(token: String): Pair<Long, Long> {
        val parts = Cursor.decode(token).split(":")
        val updatedAt = parts.getOrNull(0)?.toLongOrNull()
        val id = parts.getOrNull(1)?.toLongOrNull()
        if (parts.size != 2 || updatedAt == null || id == null) {
            throw IllegalArgumentException("Invalid pagination cursor")
        }
        return updatedAt to id
    }

    suspend fun getSession(userId: Long, sessionId: Long): PracticeSessionDto = dbQuery {
        fetchSession(userId, sessionId) ?: throw NotFoundException("Session $sessionId not found")
    }

    suspend fun updateProgress(userId: Long, sessionId: Long, request: UpdateProgressRequest): PracticeSessionDto =
        dbQuery {
            val updated = PracticeSessions.update({
                (PracticeSessions.id eq sessionId) and (PracticeSessions.userId eq userId)
            }) {
                it[currentCardIndex] = request.currentCardIndex
                it[numCorrect] = request.numCorrect
                it[numIncorrect] = request.numIncorrect
                it[updatedAtMillis] = System.currentTimeMillis()
            }
            if (updated == 0) throw NotFoundException("Session $sessionId not found")
            fetchSession(userId, sessionId)!!
        }

    suspend fun complete(userId: Long, sessionId: Long, timeZone: String?): PracticeSessionDto = dbQuery {
        val now = System.currentTimeMillis()
        val updated = PracticeSessions.update({
            (PracticeSessions.id eq sessionId) and (PracticeSessions.userId eq userId)
        }) {
            it[isCompleted] = true
            it[updatedAtMillis] = now
            // Stamp the completion instant + device tz for day-based streaks (FLA-105).
            it[completedAtMillis] = now
            it[completedTimeZone] = timeZone
        }
        if (updated == 0) throw NotFoundException("Session $sessionId not found")
        fetchSession(userId, sessionId)!!
    }

    private fun fetchSession(userId: Long, sessionId: Long): PracticeSessionDto? = (PracticeSessions innerJoin Decks)
        .selectAll()
        .where { (PracticeSessions.id eq sessionId) and (PracticeSessions.userId eq userId) }
        .firstOrNull()
        ?.let { it.toPracticeSessionDto(it[Decks.title]) }

    private fun visibleDeckTitle(userId: Long, deckId: Long): String? = Decks.selectAll()
        .where {
            (Decks.id eq deckId) and ((Decks.ownerUserId eq userId) or (Decks.isGlobal eq true))
        }
        .firstOrNull()
        ?.get(Decks.title)
}
