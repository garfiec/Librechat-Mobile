package com.garfiec.librechat.core.common.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the device's DEFAULT network — the one the app's requests actually use — offers internet.
 *
 * Tracks only the default network (`registerDefaultNetworkCallback`). A plain `NetworkRequest`
 * callback reports every matching network and each callback emitted its own verdict, so after an
 * airplane-mode cycle — Wi-Fi and cellular both come up, then the system tears one down — the
 * dropped network's `onLost` arrived last and left `false` standing while the default network was
 * fine: a "No internet connection" banner for half an hour over working requests.
 *
 * Deliberately INTERNET, not VALIDATED: this app talks to the user's own server, which may sit on a
 * LAN with no internet uplink (or behind a network that blocks the validation probe). Such a network
 * never validates, and every consumer here waits on this flow — the SSE client blocks its retries on
 * it — so requiring VALIDATED would stall them forever on a network that reaches the server.
 */
class AndroidConnectivityObserver(
    private val context: Context,
) : ConnectivityObserver {
    // Permission is declared in app module's AndroidManifest.xml
    @SuppressLint("MissingPermission")
    override val isConnected: Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

        // Emit current state
        val currentNetwork = connectivityManager.activeNetwork
        trySend(connectivityManager.getNetworkCapabilities(currentNetwork).hasInternet())

        val callback = object : ConnectivityManager.NetworkCallback() {
            // No onAvailable: for a default-network callback it is always followed by
            // onCapabilitiesChanged, which carries the verdict.
            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(networkCapabilities.hasInternet())
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    private fun NetworkCapabilities?.hasInternet(): Boolean {
        return this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
