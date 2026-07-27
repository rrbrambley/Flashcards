package com.rrbrambley.flashcards.notifications.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room-cached notification (#321), keyed by the **backend id** (no autoGenerate — the server owns ids).
 * [data] is the JSON payload string, decoded to a map at the mapper boundary. Server-produced, so there
 * is no local-mint / pending-sync machinery — only [isRead], which is monotonic (mark-read never
 * reverts on a re-sync).
 */
@Entity(
    tableName = "notifications",
    indices = [Index(value = ["createdAtMillis"])],
)
data class NotificationEntity(
    @PrimaryKey
    val id: Long,
    val type: String,
    val data: String,
    val isRead: Boolean = false,
    val createdAtMillis: Long,
)
