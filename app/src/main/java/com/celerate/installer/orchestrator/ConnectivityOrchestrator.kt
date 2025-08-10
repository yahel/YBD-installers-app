package com.celerate.installer.orchestrator

import android.net.*
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/**
 * ConnectivityOrchestrator
 * ------------------------
 * Owns and exposes two concurrent network paths used by the Installer App:
 *  1) **AGLR Wi-Fi (no internet)** – an *app-scoped* Wi-Fi connection to the Installer-AP
 *     used to reach on-site devices (UBNT/MikroTik) even when that Wi-Fi has **no internet**.
 *  2) **Cellular (internet)** – a mobile data network used exclusively for calls to the
 *     cloud controller.
 *
 * Key design choices:
 *  • Uses `WifiNetworkSpecifier` (API 29+) so the system does not "validate" the Wi-Fi
 *    for internet and will not auto-disconnect it when captive/offline.
 *  • We never bind the entire process to a network. Instead, we expose the raw `Network`
 *    handles so callers can create **per-network** sockets/HTTP clients (pinning via
 *    `Network.socketFactory`). This gives true split routing.
 *  • Exposes `StateFlow<Network?>` for both paths, and a convenience snapshot via
 *    `currentHandles()`.
 *
 * Lifecycle:
 *  • Call `connectToAglr(ssid, pass)` to initiate/hold the AGLR Wi-Fi session. Keep the
 *    Flow collected (or keep a reference to the returned `NetworkCallback`) for as long
 *    as you need the connection. Cancelling the Flow releases the request.
 *  • Call `requestCellular()` to obtain/hold a cellular network for controller traffic.
 *
 * Notes:
 *  • Permissions required at runtime on modern Android: `NEARBY_WIFI_DEVICES` (13+),
 *    `ACCESS_FINE_LOCATION` (for Wi-Fi), `INTERNET`, `ACCESS_NETWORK_STATE`.
 *  • For long-running aiming/config, hold a foreground service + Wi-Fi lock (not shown
 *    here; can be added later).
 */
class ConnectivityOrchestrator(private val context: android.content.Context) {
    data class Handles(val aglr: Network?, val cellular: Network?)

    private val cm: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    private var aglrCallback: ConnectivityManager.NetworkCallback? = null
    private var cellCallback: ConnectivityManager.NetworkCallback? = null

    private val _aglrNetwork = MutableStateFlow<Network?>(null)
    val aglrNetwork: StateFlow<Network?> = _aglrNetwork.asStateFlow()

    private val _cellNetwork = MutableStateFlow<Network?>(null)
    val cellNetwork: StateFlow<Network?> = _cellNetwork.asStateFlow()

    fun connectToAglr(ssid: String, passphrase: String?): Flow<Network?> = callbackFlow {
        val specBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (!passphrase.isNullOrBlank()) specBuilder.setWpa2Passphrase(passphrase)
        val spec = specBuilder.build()

        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(spec)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _aglrNetwork.value = network
                trySend(network)
            }
            override fun onLost(network: Network) {
                if (_aglrNetwork.value == network) _aglrNetwork.value = null
                trySend(null)
            }
        }
        aglrCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        aglrCallback = cb
        cm.requestNetwork(req, cb)

        awaitClose {
            runCatching { cm.unregisterNetworkCallback(cb) }
            if (aglrCallback == cb) aglrCallback = null
            _aglrNetwork.value = null
        }
    }

    fun requestCellular(): Flow<Network?> = callbackFlow {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _cellNetwork.value = network
                trySend(network)
            }
            override fun onLost(network: Network) {
                if (_cellNetwork.value == network) _cellNetwork.value = null
                trySend(null)
            }
        }
        cellCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        cellCallback = cb
        cm.requestNetwork(req, cb)

        awaitClose {
            runCatching { cm.unregisterNetworkCallback(cb) }
            if (cellCallback == cb) cellCallback = null
            _cellNetwork.value = null
        }
    }

    fun currentHandles(): Handles = Handles(aglr = _aglrNetwork.value, cellular = _cellNetwork.value)
}
