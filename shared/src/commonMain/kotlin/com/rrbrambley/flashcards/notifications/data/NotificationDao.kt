package com.rrbrambley.flashcards.notifications.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    /** All cached notifications, newest first. */
    @Query("SELECT * FROM notifications ORDER BY createdAtMillis DESC, id DESC")
    fun observeNotifications(): Flow<List<NotificationEntity>>

    /** The unread count, for the badge. */
    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Upsert
    suspend fun upsertAll(notifications: List<NotificationEntity>)

    /** Ids already read locally — so a re-sync from the backend can't downgrade them (read is monotonic). */
    @Query("SELECT id FROM notifications WHERE isRead = 1")
    suspend fun readIds(): List<Long>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    /** Clears the cache (e.g. on logout). */
    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}
