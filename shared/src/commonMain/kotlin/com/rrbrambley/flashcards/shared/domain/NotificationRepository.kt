package com.rrbrambley.flashcards.shared.domain

import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to the user's in-app notifications (#321). Reads emit from the Room cache
 * (best-effort re-synced from the backend on subscribe); mark-read updates the cache optimistically
 * and informs the backend best-effort. Notifications are server-produced (no local creation).
 */
interface NotificationRepository {
    /** The user's notifications, newest first, from the cache (re-syncs from the backend on subscribe). */
    fun observeNotifications(): Flow<List<Notification>>

    /** The unread count, for the badge (from the cache). */
    fun observeUnreadCount(): Flow<Int>

    /** Marks one notification read — optimistic local update + best-effort backend. */
    @Throws(Exception::class)
    suspend fun markRead(id: Long) {}

    /** Marks all the user's notifications read — optimistic local update + best-effort backend. */
    @Throws(Exception::class)
    suspend fun markAllRead() {}
}
