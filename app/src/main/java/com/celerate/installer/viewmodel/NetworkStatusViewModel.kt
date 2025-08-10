package com.celerate.installer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.celerate.installer.model.ConnectionStatus
import com.celerate.installer.orchestrator.ConnectivityOrchestrator
import com.celerate.installer.orchestrator.ProbeResult
import com.celerate.installer.orchestrator.probeHttp
import com.celerate.installer.orchestrator.probeTcp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

/**
 * NetworkStatusViewModel
 * ----------------------
 * Drives the four status indicators required for field visibility:
 *  • Controller (internet via cellular)
 *  • Installer-AP (AGLR Wi-Fi presence)
 *  • UBNT-Radio (via AGLR)
 *  • Mikrotik (via AGLR)
 *
 * It owns the orchestrator, periodically probes endpoints, and exposes a
 * `StateFlow<ConnectionStatus>` for each indicator to the UI.
 */
class NetworkStatusViewModel(app: Application) : AndroidViewModel(app) {
    private val orchestrator = ConnectivityOrchestrator(app)

    private val _controller = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Unknown)
    val controller: StateFlow<ConnectionStatus> = _controller.asStateFlow()

    private val _aglr = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Unknown)
    val aglr: StateFlow<ConnectionStatus> = _aglr.asStateFlow()

    private val _ubnt = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Unknown)
    val ubnt: StateFlow<ConnectionStatus> = _ubnt.asStateFlow()

    private val _mkt = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Unknown)
    val mkt: StateFlow<ConnectionStatus> = _mkt.asStateFlow()

    // --- Configuration (can be surfaced as editable settings in the UI) ---
    var controllerBaseUrl: String = "https://staging.controller.example" // cloud base URL
    var aglrSsid: String = "AGLR-Installer"                               // Installer-AP SSID
    var aglrPass: String? = null                                           // WPA2 pass if any
    var ubntIp: String = "192.168.1.20"                                   // UBNT default
    var mktIp: String = "192.168.88.1"                                    // Mikrotik default

    private var probeJob: Job? = null

    /** Initiate/hold AGLR Wi-Fi session. Safe to call multiple times. */
    fun connectAglr() {
        viewModelScope.launch {
            _aglr.value = ConnectionStatus.Connecting
            orchestrator.connectToAglr(aglrSsid, aglrPass).collect { net ->
                _aglr.value = if (net != null) ConnectionStatus.Connected() else ConnectionStatus.Disconnected()
            }
        }
    }

    /** Request/hold a cellular network (internet). */
    fun requestCellular() {
        viewModelScope.launch { orchestrator.requestCellular().collect { /* handled via currentHandles() */ } }
    }

    /**
     * Begin periodic probing of all endpoints. Each tick:
     *  1) Updates Installer-AP status from `aglrNetwork` presence
     *  2) Probes Controller over **cellular**
     *  3) Probes UBNT & Mikrotik via **AGLR** using TCP (SSH/HTTP/RouterOS)
     */
    fun startProbing(intervalMs: Long = 3000) {
        if (probeJob

