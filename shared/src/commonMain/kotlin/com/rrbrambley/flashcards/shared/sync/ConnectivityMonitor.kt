package com.rrbrambley.flashcards.shared.sync

import kotlinx.coroutines.flow.Flow

/**
 * Reports network reachability over time. Implemented per platform (Android `ConnectivityManager`,
 * iOS `NWPathMonitor`). The flow emits the current state on subscription, then on each change.
 */
fun interface ConnectivityMonitor {
    fun observe(): Flow<Boolean>
}
