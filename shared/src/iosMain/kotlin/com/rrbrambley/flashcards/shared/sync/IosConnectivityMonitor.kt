package com.rrbrambley.flashcards.shared.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * iOS [ConnectivityMonitor] backed by `NWPathMonitor`. Emits the current reachability on start, then
 * on each path change. Used to flush offline practice sessions on reconnect (FLA-91).
 */
@OptIn(ExperimentalForeignApi::class)
class IosConnectivityMonitor : ConnectivityMonitor {
    override fun observe(): Flow<Boolean> = callbackFlow {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            trySend(nw_path_get_status(path) == nw_path_status_satisfied)
        }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("flashcards.connectivity", null))
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }
}
