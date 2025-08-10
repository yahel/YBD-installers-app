package com.celerate.installer.model

sealed interface ConnectionStatus {
    data object Unknown : ConnectionStatus
    data object Connecting : ConnectionStatus
    data class Connected(val latencyMs: Long? = null) : ConnectionStatus
    data class Disconnected(val reason: String? = null) : ConnectionStatus
}
