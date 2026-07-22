package com.rrbrambley.flashcards.backend.sessions

import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.PracticeAnswers
import com.rrbrambley.flashcards.backend.db.PracticeSessions
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.mapping.toPracticeAnswerDto
import com.rrbrambley.flashcards.backend.mapping.toPracticeSessionDto
import com.rrbrambley.flashcards.backend.routes.Cursor
import com.rrbrambley.flashcards.shared.api.Page
import com.rrbrambley.flashcards.shared.api.PracticeAnswerDto
import com.rrbrambley.flashcards.shared.api.PracticeSessionDto
import com.rrbrambley.flashcards.shared.api.RecordAnswersRequest
import com.rrbrambley.flashcards.shared.api.UpdateProgressRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.random.Random

object SessionRepository {

    /**
     * Resume the active session for this (deck, mode) if one exists, else create one. Keying on mode
     * means a user can have concurrent in-progress sessions on the same deck in different modes (e.g.
     * classic + test), each resuming to its own mode.
     */
    suspend fun startOrResume(
        userId: Long,
        deckId: Long,
        mode: String,
        shuffle: Boolean,
        questionCount: Int? = null,
        gradeAtEnd: Boolean = false,
        timeLimitSeconds: Int? = null,
    ): PracticeSessionDto {
        // A subset session (FLA-219) must ask for at least one card; null = the whole deck. The upper
        // bound is left to the client (it has the deck) — clients take(count), which clamps naturally.
        require(questionCount == null || questionCount >= 1) { "questionCount must be at least 1" }
        // A timed session (#289) must allow at least one second; null = untimed. Expiry is client-driven.
        require(timeLimitSeconds == null || timeLimitSeconds >= 1) { "timeLimitSeconds must be at least 1" }
        return dbQuery {
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
            // Resume wins: an existing active session keeps its stored order, so `shuffle` on the request
            // only takes effect when a brand-new session is created below.
            if (active != null) return@dbQuery active.toPracticeSessionDto(deckTitle)

            val now = System.currentTimeMillis()
            // Mint the seed once, server-authoritative, in a JS-safe range (< 2^31) so it round-trips
            // through the web app's JSON numbers. 0 when unshuffled.
            val seed = if (shuffle) Random.nextInt(1, Int.MAX_VALUE).toLong() else 0L
            val newId = PracticeSessions.insertAndGetId {
                it[PracticeSessions.userId] = userId
                it[PracticeSessions.deckId] = deckId
                it[PracticeSessions.mode] = mode
                it[PracticeSessions.shuffle] = shuffle
                it[shuffleSeed] = seed
                it[PracticeSessions.questionCount] = questionCount
                it[PracticeSessions.gradeAtEnd] = gradeAtEnd
                it[PracticeSessions.timeLimitSeconds] = timeLimitSeconds
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
                shuffle = shuffle,
                shuffleSeed = seed,
                questionCount = questionCount,
                gradeAtEnd = gradeAtEnd,
                timeLimitSeconds = timeLimitSeconds,
            )
        }
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

    /** Discards an in-progress session the caller owns (FLA-205); the FK cascade drops its answers. */
    suspend fun delete(userId: Long, sessionId: Long): Unit = dbQuery {
        val deleted = PracticeSessions.deleteWhere {
            (PracticeSessions.id eq sessionId) and (PracticeSessions.userId eq userId)
        }
        if (deleted == 0) throw NotFoundException("Session $sessionId not found")
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

    /**
     * Appends a batch of answers to a session's log (FLA-99), idempotent per `answerUid` (a re-sync
     * can't double-count), then recomputes the session's `numCorrect`/`numIncorrect` from the log —
     * the log is the source of truth, the counters a projection. Returns the refreshed session.
     */
    suspend fun recordAnswers(userId: Long, sessionId: Long, request: RecordAnswersRequest): PracticeSessionDto =
        dbQuery {
            requireOwnedSession(userId, sessionId)

            val existingUids = PracticeAnswers.selectAll()
                .where { PracticeAnswers.sessionId eq sessionId }
                .map { it[PracticeAnswers.answerUid] }
                .toSet()
            val toInsert = request.answers.filter { it.answerUid !in existingUids }
            if (toInsert.isNotEmpty()) {
                // ignore = true → ON CONFLICT DO NOTHING, so a concurrent insert of the same answerUid
                // can't fail the batch (the unique index backs it).
                PracticeAnswers.batchInsert(toInsert, ignore = true) { answer ->
                    this[PracticeAnswers.sessionId] = sessionId
                    this[PracticeAnswers.answerUid] = answer.answerUid
                    this[PracticeAnswers.cardUid] = answer.cardUid
                    this[PracticeAnswers.correct] = answer.correct
                    this[PracticeAnswers.sequence] = answer.sequence
                    this[PracticeAnswers.answeredAtMillis] = answer.answeredAtMillis
                    this[PracticeAnswers.submittedText] = answer.submittedText
                }
            }

            val outcomes = PracticeAnswers.selectAll()
                .where { PracticeAnswers.sessionId eq sessionId }
                .map { it[PracticeAnswers.correct] }
            val correctCount = outcomes.count { it }
            PracticeSessions.update({ PracticeSessions.id eq sessionId }) {
                it[numCorrect] = correctCount
                it[numIncorrect] = outcomes.size - correctCount
                it[updatedAtMillis] = System.currentTimeMillis()
            }
            fetchSession(userId, sessionId)!!
        }

    /** The session's answer log, oldest first (play order), for an end-of-session review (FLA-99). */
    suspend fun listAnswers(userId: Long, sessionId: Long): List<PracticeAnswerDto> = dbQuery {
        requireOwnedSession(userId, sessionId)
        PracticeAnswers.selectAll()
            .where { PracticeAnswers.sessionId eq sessionId }
            .orderBy(PracticeAnswers.sequence to SortOrder.ASC)
            .map { it.toPracticeAnswerDto() }
    }

    /** Throws [NotFoundException] unless [sessionId] exists and belongs to [userId] (hides others'). */
    private fun requireOwnedSession(userId: Long, sessionId: Long) {
        PracticeSessions.selectAll()
            .where { (PracticeSessions.id eq sessionId) and (PracticeSessions.userId eq userId) }
            .firstOrNull() ?: throw NotFoundException("Session $sessionId not found")
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
