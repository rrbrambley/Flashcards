package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

/**
 * Notification DTOs (#321). A generic, extensible in-app notification: a [type] tag plus a flexible
 * [data] payload of type-specific fields (so a new type needs no schema/DTO change), an [isRead] flag
 * backing the unread badge, and a timestamp. Clients render **localized** copy per [type] from [data]
 * (never a server-rendered string). Promoted into the shared client so Android + iOS reuse the typed
 * contract; the web mirrors it in TypeScript.
 */
@Serializable
data class NotificationDto(
    val id: Long,
    val type: String,
    val data: Map<String, String> = emptyMap(),
    val isRead: Boolean = false,
    val createdAtMillis: Long,
)

/** Response for `GET /notifications/unread-count` — the count backing the unread badge. */
@Serializable
data class UnreadCountDto(val count: Int)
