package com.rrbrambley.flashcards.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.rrbrambley.flashcards.shared.sync.ConnectivityMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android [ConnectivityMonitor] backed by [ConnectivityManager]. Emits the current reachability on
 * subscription, then on each network change. Used to flush offline practice sessions on reconnect
 * (FLA-91). Requires the `ACCESS_NETWORK_STATE` permission.
 */
class AndroidConnectivityMonitor(private val context: Context) : ConnectivityMonitor {
    override fun observe(): Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(manager.isOnline())
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }
        trySend(manager.isOnline()) // current state up front; PracticeSyncManager dedups duplicates
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }

    private fun ConnectivityManager.isOnline(): Boolean {
        val caps = getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
