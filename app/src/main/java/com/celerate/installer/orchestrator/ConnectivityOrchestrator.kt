package com.celerate.installer.orchestrator

import android.net.*
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

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
            override fun onAvailable(network: Network) { _aglrNetwork.value = network; trySend(network) }
            override fun onLost(network: Network) { if (_aglrNetwork.value == network) _aglrNetwork.value = null; trySend(null) }
        }
        aglrCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        aglrCallback = cb
        try {
            cm.requestNetwork(req, cb)
        } catch (e: SecurityException) {
            trySend(null); close(e); return@callbackFlow
        } catch (e: IllegalArgumentException) {
            trySend(null); close(e); return@callbackFlow
        }
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
            override fun onAvailable(network: Network) { _cellNetwork.value = network; trySend(network) }
            override fun onLost(network: Network) { if (_cellNetwork.value == network) _cellNetwork.value = null; trySend(null) }
        }
        cellCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        cellCallback = cb
        try {
            cm.requestNetwork(req, cb)
        } catch (e: SecurityException) {
            trySend(null); close(e); return@callbackFlow
        } catch (e: IllegalArgumentException) {
            trySend(null); close(e); return@callbackFlow
        }
        awaitClose {
            runCatching { cm.unregisterNetworkCallback(cb) }
            if (cellCallback == cb) cellCallback = null
            _cellNetwork.value = null
        }
    }

    fun currentHandles(): Handles = Handles(aglr = _aglrNetwork.value, cellular = _cellNetwork.value)
}
