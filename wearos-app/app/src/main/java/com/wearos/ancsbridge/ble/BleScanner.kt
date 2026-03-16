package com.wearos.ancsbridge.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@SuppressLint("MissingPermission")
class BleScanner(private val context: Context) {

    companion object {
        private const val TAG = "BleScanner"
    }

    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    private val scanner by lazy { bluetoothAdapter.bluetoothLeScanner }

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    /**
     * Scan for the AncsBridge iOS companion app (by its custom service UUID).
     * This is the primary scan mode — finds the iPhone reliably.
     */
    fun scanForCompanion(): Flow<ScanResult> = callbackFlow {
        if (!isBluetoothEnabled) {
            close(IllegalStateException("Bluetooth is not enabled"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Log.i(TAG, "Found companion: ${result.device.name ?: result.device.address} rssi=${result.rssi}")
                trySend(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Companion scan failed: $errorCode")
                close(RuntimeException("BLE scan failed: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Scan specifically for the companion app's service UUID
        val companionFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(AncsConstants.COMPANION_SERVICE_UUID))
            .build()

        Log.d(TAG, "Starting companion scan (UUID: ${AncsConstants.COMPANION_SERVICE_UUID})")
        scanner.startScan(listOf(companionFilter), settings, callback)

        awaitClose {
            Log.d(TAG, "Stopping companion scan")
            scanner.stopScan(callback)
        }
    }

    /**
     * Fallback: Scan for any Apple devices using manufacturer data filter.
     */
    fun scan(): Flow<ScanResult> = callbackFlow {
        if (!isBluetoothEnabled) {
            close(IllegalStateException("Bluetooth is not enabled"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Log.d(TAG, "Found device: ${result.device.name ?: result.device.address}")
                trySend(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error: $errorCode")
                close(RuntimeException("BLE scan failed: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        val appleFilter = ScanFilter.Builder()
            .setManufacturerData(0x004C, byteArrayOf())
            .build()

        Log.d(TAG, "Starting Apple device scan")
        scanner.startScan(listOf(appleFilter), settings, callback)

        awaitClose {
            Log.d(TAG, "Stopping Apple device scan")
            scanner.stopScan(callback)
        }
    }
}
