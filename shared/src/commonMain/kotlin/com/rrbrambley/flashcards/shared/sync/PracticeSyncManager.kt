package com.rrbrambley.flashcards.shared.sync

import com.rrbrambley.flashcards.shared.domain.PracticeSessionSyncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Drives offline-session reconciliation (FLA-91): whenever connectivity is (re)gained it asks the
 * [PracticeSessionSyncer] to flush locally-pending sessions. The monitor emits the current state on
 * subscription, so launching online flushes immediately. The syncer single-flights internally, so
 * connectivity flapping is harmless. [scope] is owned by the caller (app/SDK lifetime).
 */
class PracticeSyncManager(
    private val connectivity: ConnectivityMonitor,
    private val syncer: PracticeSessionSyncer,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            connectivity.observe()
                .distinctUntilChanged()
                .filter { online -> online }
                .collect { runCatching { syncer.syncPendingSessions() } }
        }
    }
}
