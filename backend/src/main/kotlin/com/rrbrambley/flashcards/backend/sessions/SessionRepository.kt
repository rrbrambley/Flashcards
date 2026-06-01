package com.rrbrambley.flashcards.backend.sessions

import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.PracticeSessions
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.mapping.toPracticeSessionDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

object SessionRepository {

    /** Mirrors the app's startOrResumeSession: resume the deck's active session if one exists, else create. */
    suspend fun startOrResume(userId: Long, deckId: Long): PracticeSessionDto = dbQuery {
        val deckTitle = visibleDeckTitle(userId, deckId)
            ?: throw NotFoundException("Deck $deckId not found")

        val active = PracticeSessions.selectAll()
            .where {
                (PracticeSessions.userId eq userId) and
                    (PracticeSessions.deckId eq deckId) and
                    (PracticeSessions.isCompleted eq false)
            }
            .firstOrNull()
        if (active != null) return@dbQuery active.toPracticeSessionDto(deckTitle)

        val now = System.currentTimeMillis()
        val newId = PracticeSessions.insertAndGetId {
            it[PracticeSessions.userId] = userId
            it[PracticeSessions.deckId] = deckId
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
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    }

    suspend fun listSessions(userId: Long, activeOnly: Boolean): List<PracticeSessionDto> = dbQuery {
        (PracticeSessions innerJoin Decks)
            .selectAll()
            .where {
                if (activeOnly) {
                    (PracticeSessions.userId eq userId) and (PracticeSessions.isCompleted eq false)
                } else {
                    PracticeSessions.userId eq userId
                }
            }
            .orderBy(PracticeSessions.updatedAtMillis to SortOrder.DESC)
            .map { it.toPracticeSessionDto(it[Decks.title]) }
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

    suspend fun complete(userId: Long, sessionId: Long): PracticeSessionDto = dbQuery {
        val updated = PracticeSessions.update({
            (PracticeSessions.id eq sessionId) and (PracticeSessions.userId eq userId)
        }) {
            it[isCompleted] = true
            it[updatedAtMillis] = System.currentTimeMillis()
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
            (Decks.id eq deckId) and ((Decks.ownerUserId eq userId) or Decks.ownerUserId.isNull())
        }
        .firstOrNull()
        ?.get(Decks.title)
}
