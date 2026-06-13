package com.rrbrambley.flashcards.shared.sync

import com.rrbrambley.flashcards.shared.domain.PracticeSessionSyncer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeSyncManagerTest {

    private class CountingSyncer : PracticeSessionSyncer {
        var syncs = 0
        override suspend fun syncPendingSessions() {
            syncs++
        }
    }

    // UnconfinedTestDispatcher so the manager's hot-flow collector runs eagerly on each emission.
    @Test
    fun flushesWhenConnectivityBecomesOnline() = runTest(UnconfinedTestDispatcher()) {
        val online = MutableStateFlow(false)
        val syncer = CountingSyncer()
        PracticeSyncManager(ConnectivityMonitor { online }, syncer, backgroundScope).start()

        // Offline initially → no flush.
        assertEquals(0, syncer.syncs)

        online.value = true
        assertEquals(1, syncer.syncs)
    }

    @Test
    fun ignoresOfflineEmissions() = runTest(UnconfinedTestDispatcher()) {
        val online = MutableStateFlow(true) // online from the start (emitted on subscribe)
        val syncer = CountingSyncer()
        PracticeSyncManager(ConnectivityMonitor { online }, syncer, backgroundScope).start()
        assertEquals(1, syncer.syncs)

        online.value = false // going offline must not trigger a sync
        assertEquals(1, syncer.syncs)
    }
}
