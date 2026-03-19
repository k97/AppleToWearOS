package com.wearos.ancsbridge.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.wearos.ancsbridge.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedList

/**
 * Manages the BLE GATT connection to an iPhone and the ANCS subscription lifecycle.
 *
 * Flow: connect → bond → discover services → subscribe DS → subscribe NS → active
 */
@SuppressLint("MissingPermission")
class BleConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "BleConnectionManager"
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 3_000L
        private const val TARGET_MTU = 512
        private const val MAX_SERVICE_DISCOVERY_RETRIES = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _notificationSourceEvents = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val notificationSourceEvents: SharedFlow<ByteArray> = _notificationSourceEvents.asSharedFlow()

    private val _dataSourceEvents = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val dataSourceEvents: SharedFlow<ByteArray> = _dataSourceEvents.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var targetDevice: BluetoothDevice? = null
    private var reconnectJob: Job? = null
    private var reconnectDelay = INITIAL_RECONNECT_DELAY_MS
    private var autoReconnect = true

    // CCCD write queue — must serialize descriptor writes
    private val descriptorWriteQueue = LinkedList<Pair<BluetoothGattCharacteristic, BluetoothGattDescriptor>>()
    private var isWritingDescriptor = false

    private var serviceDiscoveryRetries = 0
    private var notificationSourceChar: BluetoothGattCharacteristic? = null
    private var controlPointChar: BluetoothGattCharacteristic? = null
    private var dataSourceChar: BluetoothGattCharacteristic? = null

    val bondStateReceiver = BondStateReceiver(
        onBonded = { device ->
            Log.i(TAG, "Bond completed, re-discovering services")
            // Small delay after bonding before service discovery
            scope.launch {
                delay(1000)
                gatt?.discoverServices()
            }
        },
        onBondFailed = { device ->
            Log.e(TAG, "Bond failed")
            _connectionState.value = ConnectionState.Error("Pairing failed")
            disconnect()
        }
    )

    private val gattCallback = GattCallback(
        onConnected = { gatt ->
            reconnectDelay = INITIAL_RECONNECT_DELAY_MS
            _connectionState.value = ConnectionState.Connecting(gatt.device.name)

            // Request higher MTU for less fragmentation
            gatt.requestMtu(TARGET_MTU)
        },
        onDisconnected = { gatt, status ->
            Log.i(TAG, "Disconnected, status=$status")
            _connectionState.value = ConnectionState.Disconnected()
            clearCharacteristics()
            gatt.close()
            this.gatt = null

            if (autoReconnect && targetDevice != null) {
                scheduleReconnect()
            }
        },
        onServicesDiscoveredCallback = { gatt, status ->
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                _connectionState.value = ConnectionState.Error("Service discovery failed")
                return@GattCallback
            }

            val ancsService = gatt.getService(AncsConstants.ANCS_SERVICE_UUID)
            if (ancsService == null) {
                // ANCS not visible — likely not bonded yet
                if (gatt.device.bondState != BluetoothDevice.BOND_BONDED) {
                    Log.i(TAG, "ANCS not found, initiating bonding")
                    _connectionState.value = ConnectionState.Bonding(gatt.device.name)
                    gatt.device.createBond()
                } else {
                    // Bonded but ANCS still not visible — retry once, then give up
                    // (this device may not be an iPhone)
                    serviceDiscoveryRetries++
                    if (serviceDiscoveryRetries <= MAX_SERVICE_DISCOVERY_RETRIES) {
                        Log.w(TAG, "Bonded but ANCS not found, retry $serviceDiscoveryRetries/$MAX_SERVICE_DISCOVERY_RETRIES")
                        scope.launch {
                            gatt.disconnect()
                            delay(2000)
                            connect(gatt.device, autoReconnect = false)
                        }
                    } else {
                        Log.e(TAG, "ANCS not found after $MAX_SERVICE_DISCOVERY_RETRIES retries — not an iPhone?")
                        _connectionState.value = ConnectionState.Error("Not an iPhone (no ANCS)")
                        scope.launch {
                            gatt.disconnect()
                            gatt.close()
                            this@BleConnectionManager.gatt = null
                        }
                    }
                }
                return@GattCallback
            }

            // ANCS service found — get characteristics
            Log.i(TAG, "ANCS service found!")
            serviceDiscoveryRetries = 0

            // If already subscribed (rediscovery for companion service), just read companion
            if (notificationSourceChar != null && dataSourceChar != null) {
                Log.i(TAG, "ANCS already subscribed, checking companion service")
                readCompanionStatus()
                return@GattCallback
            }

            notificationSourceChar = ancsService.getCharacteristic(AncsConstants.NOTIFICATION_SOURCE_UUID)
            controlPointChar = ancsService.getCharacteristic(AncsConstants.CONTROL_POINT_UUID)
            dataSourceChar = ancsService.getCharacteristic(AncsConstants.DATA_SOURCE_UUID)

            if (notificationSourceChar == null || dataSourceChar == null) {
                Log.e(TAG, "Missing ANCS characteristics")
                _connectionState.value = ConnectionState.Error("ANCS characteristics missing")
                return@GattCallback
            }

            // Subscribe to Data Source FIRST (Apple recommendation),
            // then Notification Source
            subscribeToCharacteristic(dataSourceChar!!)
        },
        onNotificationSourceChanged = { data ->
            _notificationSourceEvents.tryEmit(data)
        },
        onDataSourceChanged = { data ->
            _dataSourceEvents.tryEmit(data)
        },
        onDescriptorWritten = { descriptor, status ->
            isWritingDescriptor = false
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Descriptor write failed: $status for ${descriptor.characteristic.uuid}")
                return@GattCallback
            }

            val charUuid = descriptor.characteristic.uuid
            when (charUuid) {
                AncsConstants.DATA_SOURCE_UUID -> {
                    Log.i(TAG, "Subscribed to Data Source, now subscribing to Notification Source")
                    notificationSourceChar?.let { subscribeToCharacteristic(it) }
                }
                AncsConstants.NOTIFICATION_SOURCE_UUID -> {
                    Log.i(TAG, "Subscribed to Notification Source — ANCS session active!")
                    // Use the bonded device name (may have full iPhone name)
                    // or fall back to the GATT device name
                    val deviceName = resolveDeviceName()
                    _connectionState.value = ConnectionState.Connected(deviceName)
                    // Read the companion app's status characteristic to notify the
                    // iPhone app that the watch is connected
                    readCompanionStatus()
                }
            }

            // Process next queued descriptor write
            processDescriptorWriteQueue()
        },
        onCharacteristicWritten = { characteristic, status ->
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Characteristic write failed: $status for ${characteristic.uuid}")
            }
        },
        onMtuChanged = { mtu, status ->
            Log.i(TAG, "MTU negotiated: $mtu (status=$status)")
            // After MTU negotiation, discover services
            gatt?.discoverServices()
        }
    )

    /**
     * Connect to a BLE device (iPhone).
     */
    fun connect(device: BluetoothDevice, autoReconnect: Boolean = true) {
        reconnectJob?.cancel()
        this.autoReconnect = autoReconnect
        this.targetDevice = device
        this.serviceDiscoveryRetries = 0

        _connectionState.value = ConnectionState.Connecting(device.name)

        gatt?.close()
        gatt = device.connectGatt(
            context,
            false, // autoConnect=false for faster initial connection
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    /**
     * Reconnect to a previously bonded device using autoConnect.
     */
    fun reconnect(device: BluetoothDevice) {
        reconnectJob?.cancel()
        this.targetDevice = device
        this.autoReconnect = true
        this.serviceDiscoveryRetries = 0

        _connectionState.value = ConnectionState.Connecting(device.name)

        gatt?.close()
        gatt = device.connectGatt(
            context,
            true, // autoConnect=true uses chipset whitelist, battery efficient
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    /**
     * Write to the ANCS Control Point characteristic.
     */
    fun writeControlPoint(data: ByteArray): Boolean {
        val gatt = this.gatt ?: return false
        val char = controlPointChar ?: return false

        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        char.value = data
        return gatt.writeCharacteristic(char)
    }

    /**
     * Resolve a user-friendly device name from the bonded device list or GATT connection.
     * BLE advertised names are often truncated (e.g., "AncsBrid" instead of "iPhone").
     */
    private fun resolveDeviceName(): String {
        // Check bonded devices for a full name containing "iPhone"
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        val bondedName = adapter?.bondedDevices?.firstOrNull { device ->
            device.address == gatt?.device?.address
        }?.name

        // Prefer the bonded name, fall back to GATT device name
        val rawName = bondedName ?: gatt?.device?.name ?: "iPhone"

        // If the name is a truncated BLE name like "AncsBrid" or "AppleToWe", show "iPhone" instead
        return if (rawName.startsWith("AncsBrid", ignoreCase = true) ||
            rawName.startsWith("AppleToWe", ignoreCase = true)) {
            "iPhone"
        } else {
            rawName
        }
    }

    /**
     * Read the companion app's status characteristic to notify the iPhone app
     * that the watch has connected. This triggers didReceiveRead on the iOS side.
     */
    private fun readCompanionStatus() {
        val gatt = this.gatt ?: return
        val companionServiceUuid = java.util.UUID.fromString("A0E3D5B1-4C6F-4E8A-9B2D-7F1E3A5C8D0B")
        val statusCharUuid = java.util.UUID.fromString("A0E3D5B2-4C6F-4E8A-9B2D-7F1E3A5C8D0B")
        val service = gatt.getService(companionServiceUuid)
        if (service != null) {
            val char = service.getCharacteristic(statusCharUuid)
            if (char != null) {
                gatt.readCharacteristic(char)
                Log.i(TAG, "Reading companion status characteristic to notify iPhone app")
            } else {
                Log.d(TAG, "Companion status characteristic not found")
            }
        } else {
            Log.d(TAG, "Companion service not found — iPhone companion app may not be running")
        }
    }

    /**
     * Write quick reply payload to the iPhone companion app via BLE.
     * Format: "callerName|replyMessage"
     */
    fun writeQuickReply(payload: String) {
        val gatt = this.gatt ?: run {
            Log.e(TAG, "writeQuickReply: no GATT connection")
            return
        }
        val service = gatt.getService(AncsConstants.COMPANION_SERVICE_UUID) ?: run {
            Log.e(TAG, "writeQuickReply: companion service not found")
            return
        }
        val char = service.getCharacteristic(AncsConstants.QUICK_REPLY_CHARACTERISTIC_UUID) ?: run {
            Log.e(TAG, "writeQuickReply: quick reply characteristic not found")
            return
        }
        val data = payload.toByteArray(Charsets.UTF_8)
        char.value = data
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val success = gatt.writeCharacteristic(char)
        Log.i(TAG, "writeQuickReply: wrote ${data.size} bytes, success=$success")
    }

    fun disconnect() {
        autoReconnect = false
        reconnectJob?.cancel()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        clearCharacteristics()
        targetDevice = null
        _connectionState.value = ConnectionState.Idle
    }

    /**
     * Get the bonded iPhone device if one exists.
     */
    fun getBondedIPhone(): BluetoothDevice? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return null
        return adapter.bondedDevices?.firstOrNull { device ->
            val name = device.name ?: ""
            name.contains("iPhone", ignoreCase = true) ||
                name.contains("AncsBrid", ignoreCase = true) ||
                name.contains("AncsBridge", ignoreCase = true) ||
                name.contains("AppleToWe", ignoreCase = true) ||
                name.contains("AppleToWearOS", ignoreCase = true) ||
                name.contains("WearBridge", ignoreCase = true) ||
                name.contains("WearBrid", ignoreCase = true)
        }
    }

    private fun subscribeToCharacteristic(characteristic: BluetoothGattCharacteristic) {
        val gatt = this.gatt ?: return

        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(AncsConstants.CCCD_UUID)
        if (descriptor == null) {
            Log.e(TAG, "CCCD not found for ${characteristic.uuid}")
            return
        }

        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

        // Queue the write since BLE only allows one outstanding GATT operation at a time
        descriptorWriteQueue.add(characteristic to descriptor)
        if (!isWritingDescriptor) {
            processDescriptorWriteQueue()
        }
    }

    private fun processDescriptorWriteQueue() {
        if (descriptorWriteQueue.isEmpty()) return
        val (_, descriptor) = descriptorWriteQueue.poll() ?: return
        isWritingDescriptor = true
        gatt?.writeDescriptor(descriptor)
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.i(TAG, "Scheduling reconnect in ${reconnectDelay}ms")
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            targetDevice?.let { reconnect(it) }
        }
    }

    private fun clearCharacteristics() {
        notificationSourceChar = null
        controlPointChar = null
        dataSourceChar = null
        descriptorWriteQueue.clear()
        isWritingDescriptor = false
    }

    fun destroy() {
        autoReconnect = false
        reconnectJob?.cancel()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
