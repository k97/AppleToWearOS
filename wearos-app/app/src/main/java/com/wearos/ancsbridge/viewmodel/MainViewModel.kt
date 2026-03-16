package com.wearos.ancsbridge.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearos.ancsbridge.ancs.AncsService
import com.wearos.ancsbridge.ble.BleScanner
import com.wearos.ancsbridge.model.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = BleScanner(application)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _scanStatus = MutableStateFlow("")
    val scanStatus: StateFlow<String> = _scanStatus.asStateFlow()

    private var scanJob: Job? = null

    val isBluetoothEnabled: Boolean get() = scanner.isBluetoothEnabled

    private val discoveredAppleDevices = mutableMapOf<String, ScanResult>()

    /**
     * Scan for Apple devices for 5 seconds, then connect to the strongest signal.
     * No device list is rendered — the scan runs silently with status updates.
     */
    fun scanAndConnect() {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanStatus.value = "Looking for AncsBridge app..."
        discoveredAppleDevices.clear()

        scanJob = viewModelScope.launch {
            // Phase 1: Scan for companion app (10 seconds)
            var found = false
            launch {
                delay(10_000)
                if (_isScanning.value && !found) {
                    _scanStatus.value = "AncsBridge app not found.\nOpen it on your iPhone."
                    stopScan()
                }
            }

            try {
                scanner.scanForCompanion().collect { result ->
                    found = true
                    val name = result.device.name ?: result.device.address
                    Log.i("MainViewModel", "Found companion: $name rssi=${result.rssi}")
                    _scanStatus.value = "Found iPhone! Connecting..."
                    stopScan()
                    connectToDevice(result.device.address)
                    return@collect
                }
            } catch (e: Exception) {
                _scanStatus.value = "Scan error: ${e.message}"
                _isScanning.value = false
            }
        }
    }

    private fun connectToBestDevice() {
        if (discoveredAppleDevices.isEmpty()) {
            _scanStatus.value = "No Apple devices found"
            return
        }

        // Pick the device with the strongest signal
        val best = discoveredAppleDevices.values
            .sortedByDescending { it.rssi }
            .first()

        val name = best.device.name ?: best.device.address
        Log.i("MainViewModel", "Connecting to best Apple device: $name rssi=${best.rssi}")
        _scanStatus.value = "Connecting to $name..."
        connectToDevice(best.device.address)
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    fun connectToDevice(address: String) {
        stopScan()
        _connectionState.value = ConnectionState.Connecting(null)

        val context = getApplication<Application>()
        val intent = Intent(context, AncsService::class.java).apply {
            action = AncsService.ACTION_CONNECT
            putExtra(AncsService.EXTRA_DEVICE_ADDRESS, address)
        }
        context.startForegroundService(intent)
    }

    /**
     * Connect directly to a device by its Bluetooth MAC address.
     * Used when we know the iPhone's classic BT address from Settings.
     */
    fun connectByAddress(address: String) {
        _connectionState.value = ConnectionState.Connecting(null)

        val context = getApplication<Application>()
        val intent = Intent(context, AncsService::class.java).apply {
            action = AncsService.ACTION_CONNECT
            putExtra(AncsService.EXTRA_DEVICE_ADDRESS, address)
        }
        context.startForegroundService(intent)
    }

    fun startService() {
        val context = getApplication<Application>()
        val intent = Intent(context, AncsService::class.java).apply {
            action = AncsService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun stopService() {
        val context = getApplication<Application>()
        val intent = Intent(context, AncsService::class.java).apply {
            action = AncsService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun disconnect() {
        val context = getApplication<Application>()
        val intent = Intent(context, AncsService::class.java).apply {
            action = AncsService.ACTION_DISCONNECT
        }
        context.startService(intent)
        _connectionState.value = ConnectionState.Idle
    }

    fun hasBondedIPhone(): Boolean {
        val context = getApplication<Application>()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: return false
        return adapter.bondedDevices.any { device ->
            val name = device.name ?: ""
            name.contains("iPhone", ignoreCase = true) ||
                name.contains("AncsBrid", ignoreCase = true) ||
                name.contains("AncsBridge", ignoreCase = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
