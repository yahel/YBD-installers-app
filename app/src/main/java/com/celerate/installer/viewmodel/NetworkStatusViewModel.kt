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
    var controllerBaseUrl: String = "https://staging.controller.example"
    var aglrSsid: String = "AGLR-Installer"
    var aglrPass: String? = null
    var ubntIp: String = "192.168.1.20"
    var mktIp: String = "192.168.88.1"
    private var probeJob: Job? = null
    fun connectAglr() {
        viewModelScope.launch {
            _aglr.value = ConnectionStatus.Connecting
            orchestrator.connectToAglr(aglrSsid, aglrPass).collect { net ->
                _aglr.value = if (net != null) ConnectionStatus.Connected() else ConnectionStatus.Disconnected()
            }
        }
    }
    fun requestCellular() {
        viewModelScope.launch { orchestrator.requestCellular().collect { } }
    }
    fun startProbing(intervalMs: Long = 3000) {
        if (probeJob != null) return
        requestCellular()
        if (_aglr.value !is ConnectionStatus.Connected) connectAglr()
        probeJob = viewModelScope.launch {
            val cloudClient = OkHttpClient.Builder()
                .connectTimeout(1500, TimeUnit.MILLISECONDS)
                .readTimeout(1500, TimeUnit.MILLISECONDS)
                .build()
            while (true) {
                val handles = orchestrator.currentHandles()
                _aglr.value = if (handles.aglr != null) ConnectionStatus.Connected() else ConnectionStatus.Disconnected("not connected")
                _controller.value = ConnectionStatus.Connecting
                val ctlUrl = runCatching { controllerBaseUrl.toHttpUrl() }.getOrNull()
                if (ctlUrl != null && handles.cellular != null) {
                    val pinned = cloudClient.newBuilder()
                        .socketFactory(handles.cellular.socketFactory)
                        .dns(Dns { hostname -> handles.cellular.getAllByName(hostname).toList() })
                        .build()
                    when (val r = probeHttp(ctlUrl, pinned)) {
                        is ProbeResult.Up -> _controller.value = ConnectionStatus.Connected(r.latencyMs)
                        is ProbeResult.Down -> _controller.value = ConnectionStatus.Disconnected(r.reason)
                    }
                } else {
                    _controller.value = ConnectionStatus.Disconnected("no cellular or bad URL")
                }
                if (handles.aglr != null) {
                    val r = probeTcp(ubntIp, 22, handles.aglr)
                    _ubnt.value = when (r) {
                        is ProbeResult.Up -> ConnectionStatus.Connected(r.latencyMs)
                        is ProbeResult.Down -> {
                            val r2 = probeTcp(ubntIp, 80, handles.aglr)
                            when (r2) {
                                is ProbeResult.Up -> ConnectionStatus.Connected(r2.latencyMs)
                                is ProbeResult.Down -> ConnectionStatus.Disconnected(r2.reason)
                            }
                        }
                    }
                } else {
                    _ubnt.value = ConnectionStatus.Disconnected("no AGLR")
                }
                if (handles.aglr != null) {
                    val r = probeTcp(mktIp, 22, handles.aglr)
                    _mkt.value = when (r) {
                        is ProbeResult.Up -> ConnectionStatus.Connected(r.latencyMs)
                        is ProbeResult.Down -> {
                            val r2 = probeTcp(mktIp, 8728, handles.aglr)
                            when (r2) {
                                is ProbeResult.Up -> ConnectionStatus.Connected(r2.latencyMs)
                                is ProbeResult.Down -> ConnectionStatus.Disconnected(r2.reason)
                            }
                        }
                    }
                } else {
                    _mkt.value = ConnectionStatus.Disconnected("no AGLR")
                }
                kotlinx.coroutines.delay(intervalMs)
            }
        }
    }
    fun stopProbing() { probeJob?.cancel(); probeJob = null }
}
