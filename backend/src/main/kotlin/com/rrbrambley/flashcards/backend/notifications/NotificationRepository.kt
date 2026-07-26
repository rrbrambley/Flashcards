package com.rrbrambley.flashcards.backend.notifications

import com.rrbrambley.flashcards.backend.db.Notifications
import com.rrbrambley.flashcards.backend.db.dbQuery
import com.rrbrambley.flashcards.backend.error.NotFoundException
import com.rrbrambley.flashcards.backend.routes.Cursor
import com.rrbrambley.flashcards.shared.api.NotificationDto
import com.rrbrambley.flashcards.shared.api.Page
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * In-app notifications (#321). Records are produced internally by features (via [insert], within the
 * producing action's transaction) and read only by their recipient — there's no public create endpoint.
 * The feed is cursor-paginated newest-first; [Notifications.data] holds a JSON payload the client
 * renders per type.
 */
object NotificationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Inserts a notification for [recipientUserId]. Runs in the CALLER's transaction (no `dbQuery`) so
     * it commits atomically with the producing action (e.g. a discussion reply) — call inside a
     * `dbQuery` block. Returns the new id.
     */
    fun insert(recipientUserId: Long, type: NotificationType, data: Map<String, String>, now: Long): Long =
        Notifications.insertAndGetId {
            it[Notifications.recipientUserId] = recipientUserId
            it[Notifications.type] = type.key
            it[Notifications.data] = json.encodeToString(data)
            it[createdAtMillis] = now
        }.value

    /**
     * One page of the user's notifications, newest first. The cursor packs (createdAtMillis, id); id is
     * the tiebreaker so rows sharing a millis can't straddle a page boundary.
     */
    suspend fun list(userId: Long, limit: Int, cursor: String?): Page<NotificationDto> = dbQuery {
        val after = cursor?.let { decodeCursor(it) }
        val query = Notifications.selectAll().where { Notifications.recipientUserId eq userId }
        if (after != null) {
            val (millis, id) = after
            query.andWhere {
                (Notifications.createdAtMillis less millis) or
                    ((Notifications.createdAtMillis eq millis) and (Notifications.id less id))
            }
        }
        val rows = query
            .orderBy(Notifications.createdAtMillis to SortOrder.DESC, Notifications.id to SortOrder.DESC)
            .limit(limit + 1)
            .toList()

        val pageRows = rows.take(limit)
        val nextCursor = if (rows.size > limit) {
            val last = pageRows.last()
            Cursor.encode("${last[Notifications.createdAtMillis]}:${last[Notifications.id].value}")
        } else {
            null
        }
        Page(items = pageRows.map { it.toDto() }, nextCursor = nextCursor)
    }

    /** The count of the user's unread notifications, for the badge. */
    suspend fun unreadCount(userId: Long): Int = dbQuery {
        Notifications.selectAll()
            .where { (Notifications.recipientUserId eq userId) and (Notifications.isRead eq false) }
            .count()
            .toInt()
    }

    /** Marks one of the user's notifications read (idempotent); 404 if it isn't theirs (hides others'). */
    suspend fun markRead(userId: Long, id: Long): Unit = dbQuery {
        val updated = Notifications.update({
            (Notifications.id eq id) and (Notifications.recipientUserId eq userId)
        }) {
            it[isRead] = true
        }
        if (updated == 0) throw NotFoundException("Notification $id not found")
    }

    /** Marks all the user's notifications read. */
    suspend fun markAllRead(userId: Long): Unit = dbQuery {
        Notifications.update({
            (Notifications.recipientUserId eq userId) and (Notifications.isRead eq false)
        }) {
            it[isRead] = true
        }
    }

    private fun decodeCursor(token: String): Pair<Long, Long> {
        val parts = Cursor.decode(token).split(":")
        val millis = parts.getOrNull(0)?.toLongOrNull()
        val id = parts.getOrNull(1)?.toLongOrNull()
        require(parts.size == 2 && millis != null && id != null) { "Invalid pagination cursor" }
        return millis to id
    }

    private fun ResultRow.toDto(): NotificationDto = NotificationDto(
        id = this[Notifications.id].value,
        type = this[Notifications.type],
        data = json.decodeFromString<Map<String, String>>(this[Notifications.data]),
        isRead = this[Notifications.isRead],
        createdAtMillis = this[Notifications.createdAtMillis],
    )
}
