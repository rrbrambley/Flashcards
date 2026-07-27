package com.rrbrambley.flashcards.notifications.data

import com.rrbrambley.flashcards.shared.api.NotificationDto
import com.rrbrambley.flashcards.shared.domain.Notification
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * Backend DTO → Room entity. [isReadOverride] preserves a locally-read row across a re-sync — read is
 * monotonic, so the cached flag is OR-ed with the backend's (an in-flight/offline mark-read isn't
 * reverted when the server still reports it unread).
 */
fun NotificationDto.toEntity(isReadOverride: Boolean = false): NotificationEntity = NotificationEntity(
    id = id,
    type = type,
    data = json.encodeToString(data),
    isRead = isRead || isReadOverride,
    createdAtMillis = createdAtMillis,
)

/** Room entity → domain model (decodes the JSON [NotificationEntity.data] payload). */
fun NotificationEntity.toDomain(): Notification = Notification(
    id = id,
    type = type,
    data = json.decodeFromString<Map<String, String>>(data),
    isRead = isRead,
    createdAtMillis = createdAtMillis,
)
