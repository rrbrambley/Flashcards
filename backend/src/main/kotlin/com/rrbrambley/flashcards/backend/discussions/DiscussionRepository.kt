package com.rrbrambley.flashcards.backend.discussions

import com.rrbrambley.flashcards.backend.auth.AuthService
import com.rrbrambley.flashcards.backend.auth.Avatars
import com.rrbrambley.flashcards.backend.db.Decks
import com.rrbrambley.flashcards.backend.db.DiscussionMessages
import com.rrbrambley.flashcards.backend.db.DiscussionReports
import com.rrbrambley.flashcards.backend.db.DiscussionThreads
import com.rrbrambley.flashcards.backend.db.Flashcards
import com.rrbrambley.flashcards.backend.db.Users
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.ForbiddenException
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.error.TooManyRequestsException
import com.rrbrambley.flashcards.backend.routes.Cursor
import com.rrbrambley.flashcards.backend.validation.Validation
import com.rrbrambley.flashcards.shared.api.DiscussionMessageDto
import com.rrbrambley.flashcards.shared.api.DiscussionThreadDto
import com.rrbrambley.flashcards.shared.api.Page
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Per-card discussions (FLA-115). Threads are keyed by the card's stable [Flashcards.cardUid] and
 * created lazily on first post (or first admin lock). Posting is gated on the deck being a global
 * (ownerless) deck with discussions enabled, the thread not being locked, content moderation, and a
 * per-(user, thread) rate limit; threads auto-lock once they grow past [AUTO_LOCK_AT] messages.
 */
object DiscussionRepository {

    private const val AUTO_LOCK_AT = 500

    // Moderation report statuses (FLA-118).
    private const val STATUS_OPEN = "open"
    private const val STATUS_RESOLVED = "resolved"
    private const val STATUS_DISMISSED = "dismissed"
    private const val MAX_REASON_LENGTH = 500

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS

    /** Max messages a user may post to a single thread within each (limit, window-millis) bucket. */
    private val RATE_LIMITS = listOf(2L to MINUTE_MILLIS, 4L to HOUR_MILLIS, 8L to DAY_MILLIS)

    /** A thread's row, before mapping to a DTO. */
    private data class ThreadRow(val id: Long, val isLocked: Boolean, val messageCount: Int)

    /** Thread metadata for a card (creates nothing); a card with no thread yet reports zero/unlocked. */
    suspend fun threadMeta(cardUid: String): DiscussionThreadDto = dbQuery {
        requireDiscussionCard(cardUid)
        val thread = existingThread(cardUid)
        DiscussionThreadDto(cardUid, isLocked = thread?.isLocked ?: false, messageCount = thread?.messageCount ?: 0)
    }

    /** One chronological page of a thread's messages (public read); empty when no thread exists yet. */
    suspend fun listMessages(cardUid: String, limit: Int, cursor: String?): Page<DiscussionMessageDto> = dbQuery {
        requireDiscussionCard(cardUid)
        val thread = existingThread(cardUid) ?: return@dbQuery Page(emptyList(), null)

        val after = cursor?.let { decodeCursor(it) }
        val query = (DiscussionMessages innerJoin Users).selectAll()
            .where { DiscussionMessages.threadId eq thread.id }
        if (after != null) {
            val (millis, id) = after
            query.andWhere {
                (DiscussionMessages.createdAtMillis greater millis) or
                    ((DiscussionMessages.createdAtMillis eq millis) and (DiscussionMessages.id greater id))
            }
        }
        val rows = query
            .orderBy(DiscussionMessages.createdAtMillis to SortOrder.ASC, DiscussionMessages.id to SortOrder.ASC)
            .limit(limit + 1)
            .toList()

        val pageRows = rows.take(limit)
        val nextCursor = if (rows.size > limit) {
            val last = pageRows.last()
            Cursor.encode("${last[DiscussionMessages.createdAtMillis]}:${last[DiscussionMessages.id].value}")
        } else {
            null
        }
        Page(items = pageRows.map { it.toMessageDto() }, nextCursor = nextCursor)
    }

    /** Posts a message to a card's thread (authenticated), enforcing locking, rate limits, and moderation. */
    suspend fun createMessage(
        userId: Long,
        cardUid: String,
        content: String,
        parentMessageId: Long?,
    ): DiscussionMessageDto = dbQuery {
        val deckId = requireDiscussionCard(cardUid)
        val now = System.currentTimeMillis()
        val thread = getOrCreateThread(cardUid, deckId, now)
        if (thread.isLocked) throw ForbiddenException("This discussion is locked")

        enforceRateLimit(userId, thread.id, now)
        val text = Validation.normalizeDiscussionMessage(content)
        validateParent(parentMessageId, thread.id)

        val messageId = DiscussionMessages.insertAndGetId {
            it[threadId] = thread.id
            it[authorUserId] = userId
            it[DiscussionMessages.parentMessageId] = parentMessageId
            it[DiscussionMessages.content] = text
            it[createdAtMillis] = now
        }.value

        val newCount = thread.messageCount + 1
        DiscussionThreads.update({ DiscussionThreads.id eq thread.id }) {
            it[messageCount] = newCount
            if (newCount >= AUTO_LOCK_AT) it[isLocked] = true
        }

        DiscussionMessageDto(
            id = messageId,
            authorDisplayName = authorDisplayName(userId),
            authorAvatarUrl = authorAvatarUrl(userId),
            content = text,
            parentMessageId = parentMessageId,
            createdAtMillis = now,
        )
    }

    /** Locks or unlocks a card's thread (admin). Creates the thread if needed so an empty card can be locked. */
    suspend fun setLocked(cardUid: String, locked: Boolean): DiscussionThreadDto = dbQuery {
        val deckId = requireDiscussionCard(cardUid)
        val thread = getOrCreateThread(cardUid, deckId, System.currentTimeMillis())
        DiscussionThreads.update({ DiscussionThreads.id eq thread.id }) { it[isLocked] = locked }
        DiscussionThreadDto(cardUid, isLocked = locked, messageCount = thread.messageCount)
    }

    /**
     * Reports/flags a message for moderation (FLA-118; any signed-in user). Idempotent per
     * (message, reporter) — a second report by the same user is a no-op. 404 if the message isn't in
     * an available (global, discussion-enabled) thread.
     */
    suspend fun reportMessage(userId: Long, messageId: Long, reason: String?) = dbQuery {
        requireReportableMessage(messageId)
        val alreadyReported = DiscussionReports.selectAll().where {
            (DiscussionReports.messageId eq messageId) and (DiscussionReports.reporterUserId eq userId)
        }.any()
        if (!alreadyReported) {
            DiscussionReports.insert {
                it[DiscussionReports.messageId] = messageId
                it[reporterUserId] = userId
                it[DiscussionReports.reason] = reason?.takeIf { r -> r.isNotBlank() }?.take(MAX_REASON_LENGTH)
                it[createdAtMillis] = System.currentTimeMillis()
            }
        }
    }

    /**
     * Soft-deletes a message (admin) — sets the tombstone + audit fields and resolves the message's
     * open reports. Idempotent: re-deleting returns the same tombstoned message. 404 if missing.
     */
    suspend fun deleteMessage(adminUserId: Long, messageId: Long): DiscussionMessageDto = dbQuery {
        val now = System.currentTimeMillis()
        DiscussionMessages.update({
            (DiscussionMessages.id eq messageId) and DiscussionMessages.deletedAtMillis.isNull()
        }) {
            it[deletedAtMillis] = now
            it[deletedByUserId] = adminUserId
        }
        DiscussionReports.update({
            (DiscussionReports.messageId eq messageId) and (DiscussionReports.status eq STATUS_OPEN)
        }) {
            it[status] = STATUS_RESOLVED
            it[resolvedByUserId] = adminUserId
            it[resolvedAtMillis] = now
        }
        (DiscussionMessages innerJoin Users).selectAll()
            .where { DiscussionMessages.id eq messageId }
            .firstOrNull()
            ?.toMessageDto()
            ?: throw NotFoundException("Message not found")
    }

    /** One page of the open-report moderation queue (admin), newest first. */
    suspend fun listOpenReports(limit: Int, cursor: String?): Page<ReportedMessageDto> = dbQuery {
        val reporter = Users.alias("reporter")
        val author = Users.alias("author")
        val query = DiscussionReports
            .join(DiscussionMessages, JoinType.INNER, DiscussionReports.messageId, DiscussionMessages.id)
            .join(DiscussionThreads, JoinType.INNER, DiscussionMessages.threadId, DiscussionThreads.id)
            .join(author, JoinType.INNER, DiscussionMessages.authorUserId, author[Users.id])
            .join(reporter, JoinType.INNER, DiscussionReports.reporterUserId, reporter[Users.id])
            .selectAll()
            .where { DiscussionReports.status eq STATUS_OPEN }

        val after = cursor?.let { decodeReportCursor(it) }
        if (after != null) {
            val (millis, id) = after
            query.andWhere {
                (DiscussionReports.createdAtMillis less millis) or
                    ((DiscussionReports.createdAtMillis eq millis) and (DiscussionReports.id less id))
            }
        }
        val rows = query
            .orderBy(DiscussionReports.createdAtMillis to SortOrder.DESC, DiscussionReports.id to SortOrder.DESC)
            .limit(limit + 1)
            .toList()

        val pageRows = rows.take(limit)
        val nextCursor = if (rows.size > limit) {
            val last = pageRows.last()
            Cursor.encode("${last[DiscussionReports.createdAtMillis]}:${last[DiscussionReports.id].value}")
        } else {
            null
        }
        Page(
            items = pageRows.map { row ->
                ReportedMessageDto(
                    reportId = row[DiscussionReports.id].value,
                    reason = row[DiscussionReports.reason],
                    status = row[DiscussionReports.status],
                    reportedAtMillis = row[DiscussionReports.createdAtMillis],
                    reporterDisplayName = AuthService.displayNameOrDefault(
                        row[reporter[Users.displayName]],
                        row[reporter[Users.email]],
                    ),
                    messageId = row[DiscussionMessages.id].value,
                    cardUid = row[DiscussionThreads.cardUid],
                    authorDisplayName = AuthService.displayNameOrDefault(
                        row[author[Users.displayName]],
                        row[author[Users.email]],
                    ),
                    // Admins see the reported text (even if later removed) to triage; this is admin-only.
                    content = row[DiscussionMessages.content],
                    deleted = row[DiscussionMessages.deletedAtMillis] != null,
                    messageCreatedAtMillis = row[DiscussionMessages.createdAtMillis],
                )
            },
            nextCursor = nextCursor,
        )
    }

    /** Updates a report's status (admin) — e.g. "dismissed" for an acceptable message. 404 if missing. */
    suspend fun updateReportStatus(adminUserId: Long, reportId: Long, status: String) = dbQuery {
        require(status == STATUS_DISMISSED || status == STATUS_RESOLVED) { "Invalid report status: $status" }
        val updated = DiscussionReports.update({ DiscussionReports.id eq reportId }) {
            it[DiscussionReports.status] = status
            it[resolvedByUserId] = adminUserId
            it[resolvedAtMillis] = System.currentTimeMillis()
        }
        if (updated == 0) throw NotFoundException("Report not found")
    }

    /** Resolves [messageId] to nothing (throws) unless it's a real message in an available discussion. */
    private fun requireReportableMessage(messageId: Long) {
        (DiscussionMessages innerJoin DiscussionThreads innerJoin Decks)
            .select(DiscussionMessages.id)
            .where {
                (DiscussionMessages.id eq messageId) and
                    (Decks.isGlobal eq true) and
                    (Decks.discussionEnabled eq true)
            }
            .firstOrNull() ?: throw NotFoundException("Message not found")
    }

    private fun decodeReportCursor(token: String): Pair<Long, Long> {
        val parts = Cursor.decode(token).split(":")
        val millis = parts.getOrNull(0)?.toLongOrNull()
        val id = parts.getOrNull(1)?.toLongOrNull()
        require(parts.size == 2 && millis != null && id != null) { "Invalid pagination cursor" }
        return millis to id
    }

    /** Resolves [cardUid] to its deck id, requiring the deck to be global + discussion-enabled (else 404). */
    private fun requireDiscussionCard(cardUid: String): Long {
        val row = (Flashcards innerJoin Decks)
            .select(Flashcards.deckId, Decks.isGlobal, Decks.discussionEnabled)
            .where { Flashcards.cardUid eq cardUid }
            .firstOrNull()
            ?: throw NotFoundException("Discussion not found")
        val available = row[Decks.isGlobal] && row[Decks.discussionEnabled]
        if (!available) throw NotFoundException("Discussion not found")
        return row[Flashcards.deckId].value
    }

    private fun existingThread(cardUid: String): ThreadRow? =
        DiscussionThreads.selectAll().where { DiscussionThreads.cardUid eq cardUid }.firstOrNull()?.toThreadRow()

    private fun getOrCreateThread(cardUid: String, deckId: Long, now: Long): ThreadRow {
        existingThread(cardUid)?.let { return it }
        val id = DiscussionThreads.insertAndGetId {
            it[DiscussionThreads.cardUid] = cardUid
            it[DiscussionThreads.deckId] = deckId
            it[createdAtMillis] = now
        }.value
        return ThreadRow(id, isLocked = false, messageCount = 0)
    }

    private fun enforceRateLimit(userId: Long, threadId: Long, now: Long) {
        for ((limit, window) in RATE_LIMITS) {
            val count = DiscussionMessages.selectAll().where {
                (DiscussionMessages.authorUserId eq userId) and
                    (DiscussionMessages.threadId eq threadId) and
                    (DiscussionMessages.createdAtMillis greater (now - window))
            }.count()
            if (count >= limit) throw TooManyRequestsException("You're posting too quickly. Please wait a bit.")
        }
    }

    private fun validateParent(parentMessageId: Long?, threadId: Long) {
        if (parentMessageId == null) return
        val parent = DiscussionMessages.selectAll()
            .where { (DiscussionMessages.id eq parentMessageId) and (DiscussionMessages.threadId eq threadId) }
            .firstOrNull()
            ?: throw IllegalArgumentException("the message you're replying to doesn't exist")
        require(parent[DiscussionMessages.parentMessageId] == null) { "replies can only be one level deep" }
    }

    private fun authorDisplayName(userId: Long): String {
        val row = Users.selectAll().where { Users.id eq userId }.first()
        return AuthService.displayNameOrDefault(row[Users.displayName], row[Users.email])
    }

    private fun authorAvatarUrl(userId: Long): String? =
        Avatars.urlFor(Users.selectAll().where { Users.id eq userId }.first()[Users.avatarKey])

    /** Cursor packs `createdAtMillis:id` of the last item, so paging is stable across ties. */
    private fun decodeCursor(cursor: String): Pair<Long, Long> {
        val parts = Cursor.decode(cursor).split(":")
        val millis = parts.getOrNull(0)?.toLongOrNull()
        val id = parts.getOrNull(1)?.toLongOrNull()
        require(millis != null && id != null) { "Invalid pagination cursor" }
        return millis to id
    }

    private fun ResultRow.toThreadRow() = ThreadRow(
        id = this[DiscussionThreads.id].value,
        isLocked = this[DiscussionThreads.isLocked],
        messageCount = this[DiscussionThreads.messageCount],
    )

    /** Maps a DiscussionMessages⨝Users row to a DTO (author shown by display name, never email). A
     *  moderator-removed message reports `deleted = true` with blank content (FLA-118). */
    private fun ResultRow.toMessageDto(): DiscussionMessageDto {
        val deleted = this[DiscussionMessages.deletedAtMillis] != null
        return DiscussionMessageDto(
            id = this[DiscussionMessages.id].value,
            authorDisplayName = AuthService.displayNameOrDefault(this[Users.displayName], this[Users.email]),
            authorAvatarUrl = Avatars.urlFor(this[Users.avatarKey]),
            content = if (deleted) "" else this[DiscussionMessages.content],
            parentMessageId = this[DiscussionMessages.parentMessageId],
            createdAtMillis = this[DiscussionMessages.createdAtMillis],
            deleted = deleted,
        )
    }
}
