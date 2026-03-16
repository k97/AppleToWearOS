package com.wearos.ancsbridge.model

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Scanning : ConnectionState()
    data class Connecting(val deviceName: String?) : ConnectionState()
    data class Bonding(val deviceName: String?) : ConnectionState()
    data class Connected(val deviceName: String?) : ConnectionState()
    data class Disconnected(val reason: String? = null) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
