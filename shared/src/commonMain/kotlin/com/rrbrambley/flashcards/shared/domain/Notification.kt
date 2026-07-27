package com.rrbrambley.flashcards.shared.domain

/**
 * An in-app notification (#321). [type] tags the kind (e.g. "discussion_reply") and [data] is a
 * type-specific payload the UI renders **localized** copy from + deep-links with. [isRead] backs the
 * unread badge. Produced server-side; the client only reads + marks read.
 */
data class Notification(
    val id: Long,
    val type: String,
    val data: Map<String, String> = emptyMap(),
    val isRead: Boolean = false,
    val createdAtMillis: Long = 0L,
)
