package com.rrbrambley.flashcards.notifications.ui

import com.rrbrambley.flashcards.auth.FeatureFlagRepository
import com.rrbrambley.flashcards.auth.FeatureFlags
import com.rrbrambley.flashcards.shared.domain.Notification
import com.rrbrambley.flashcards.shared.domain.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repo: NotificationRepository, flags: Map<String, Boolean> = emptyMap()) =
        NotificationsViewModel(repo, FakeFeatureFlagRepository(flags))

    private fun notif(id: Long, isRead: Boolean, replier: String? = null) = Notification(
        id = id,
        type = "discussion_reply",
        data = replier?.let { mapOf("replierDisplayName" to it) } ?: emptyMap(),
        isRead = isRead,
        createdAtMillis = id,
    )

    @Test
    fun uiState_and_unreadCount_reflectTheRepository() = runTest {
        val repo =
            FakeNotificationRepository(listOf(notif(1, isRead = false, replier = "Ann"), notif(2, isRead = true)))
        val vm = viewModel(repo)
        // Active collectors so the WhileSubscribed StateFlows emit.
        backgroundScope.launch { vm.uiState.collect {} }
        backgroundScope.launch { vm.unreadCount.collect {} }
        advanceUntilIdle()

        assertEquals(NotificationsUiState.Loaded(repo.notifications.value), vm.uiState.value)
        assertEquals(1, vm.unreadCount.value)
    }

    @Test
    fun markRead_and_markAllRead_delegateToTheRepository() = runTest {
        val repo = FakeNotificationRepository(listOf(notif(5, isRead = false)))
        val vm = viewModel(repo)

        vm.markRead(5)
        advanceUntilIdle()
        assertEquals(listOf(5L), repo.readIds)

        vm.markAllRead()
        advanceUntilIdle()
        assertTrue(repo.markedAll)
    }

    @Test
    fun notificationsEnabled_failsOpen_unlessExplicitlyOff() = runTest {
        // Flag absent → visible (fail-open).
        val onByDefault = viewModel(FakeNotificationRepository(emptyList()))
        advanceUntilIdle()
        assertTrue(onByDefault.notificationsEnabled.value)

        // Explicitly off → hidden.
        val off = viewModel(FakeNotificationRepository(emptyList()), mapOf(FeatureFlags.NOTIFICATIONS to false))
        advanceUntilIdle()
        assertFalse(off.notificationsEnabled.value)
    }

    private class FakeFeatureFlagRepository(private val flags: Map<String, Boolean>) : FeatureFlagRepository {
        override suspend fun flags(): Map<String, Boolean> = flags
    }

    private class FakeNotificationRepository(initial: List<Notification>) : NotificationRepository {
        val notifications = MutableStateFlow(initial)
        val readIds = mutableListOf<Long>()
        var markedAll = false

        override fun observeNotifications(): Flow<List<Notification>> = notifications
        override fun observeUnreadCount(): Flow<Int> = notifications.map { list -> list.count { !it.isRead } }
        override suspend fun markRead(id: Long) {
            readIds += id
        }

        override suspend fun markAllRead() {
            markedAll = true
        }
    }
}
