package com.rrbrambley.flashcards.notifications.data

import com.rrbrambley.flashcards.shared.api.FlashcardApiClient
import com.rrbrambley.flashcards.shared.domain.Notification
import com.rrbrambley.flashcards.shared.domain.NotificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Offline-first notifications (#321): reads emit from the Room cache and best-effort re-sync from the
 * backend on subscribe; mark-read updates the cache immediately and informs the backend best-effort.
 * Notifications are server-produced (no local creation), so there's no pending-sync machinery — only
 * the read flag, kept **monotonic** so an in-flight/offline mark-read isn't reverted by a re-sync.
 */
class NotificationRepositoryImpl(
    private val apiClient: FlashcardApiClient,
    private val notificationDao: NotificationDao,
) : NotificationRepository {

    override fun observeNotifications(): Flow<List<Notification>> = flow {
        coroutineScope {
            launch { sync() }
            emitAll(notificationDao.observeNotifications().map { rows -> rows.map { it.toDomain() } })
        }
    }

    override fun observeUnreadCount(): Flow<Int> = flow {
        coroutineScope {
            launch { sync() }
            emitAll(notificationDao.observeUnreadCount())
        }
    }

    override suspend fun markRead(id: Long) {
        notificationDao.markRead(id)
        bestEffort { apiClient.markNotificationRead(id) }
    }

    override suspend fun markAllRead() {
        notificationDao.markAllRead()
        bestEffort { apiClient.markAllNotificationsRead() }
    }

    /** Pulls the full notification list and caches it, preserving locally-read rows (read is monotonic). */
    private suspend fun sync() = bestEffort {
        val readIds = notificationDao.readIds().toSet()
        val server = apiClient.getAllNotifications()
        notificationDao.upsertAll(server.map { it.toEntity(isReadOverride = it.id in readIds) })
    }

    /** Runs [block], swallowing failures (offline / server error) but never cancellation. */
    private inline fun bestEffort(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort: keep serving the cache; the local read/state stands until a later sync.
        }
    }
}
