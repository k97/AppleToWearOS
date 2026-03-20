package com.wearos.ancsbridge.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.util.Log

/**
 * BluetoothGattCallback implementation that routes ANCS characteristic changes
 * to the appropriate handlers.
 */
@SuppressLint("MissingPermission")
class GattCallback(
    private val onConnected: (BluetoothGatt) -> Unit,
    private val onDisconnected: (BluetoothGatt, Int) -> Unit,
    private val onServicesDiscoveredCallback: (BluetoothGatt, Int) -> Unit,
    private val onNotificationSourceChanged: (ByteArray) -> Unit,
    private val onDataSourceChanged: (ByteArray) -> Unit,
    private val onDescriptorWritten: (BluetoothGattDescriptor, Int) -> Unit,
    private val onCharacteristicWritten: (BluetoothGattCharacteristic, Int) -> Unit,
    private val onMtuChanged: (Int, Int) -> Unit,
    private val onCharacteristicReadCallback: ((BluetoothGattCharacteristic, ByteArray) -> Unit)? = null,
    private val onCallStateChanged: ((ByteArray) -> Unit)? = null
) : BluetoothGattCallback() {

    companion object {
        private const val TAG = "GattCallback"
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.d(TAG, "Connection state changed: status=$status newState=$newState")
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                Log.i(TAG, "Connected to ${gatt.device.address}")
                onConnected(gatt)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                Log.i(TAG, "Disconnected from ${gatt.device.address} status=$status")
                onDisconnected(gatt, status)
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.d(TAG, "Services discovered: status=$status count=${gatt.services.size}")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            gatt.services.forEach { service ->
                Log.d(TAG, "  Service: ${service.uuid}")
            }
        }
        onServicesDiscoveredCallback(gatt, status)
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val data = characteristic.value ?: return
        routeCharacteristicData(characteristic.uuid.toString().uppercase(), data)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        routeCharacteristicData(characteristic.uuid.toString().uppercase(), value)
    }

    private fun routeCharacteristicData(uuid: String, data: ByteArray) {
        when (uuid) {
            AncsConstants.NOTIFICATION_SOURCE_UUID.toString().uppercase() -> {
                Log.d(TAG, "Notification Source: ${data.toHexString()}")
                onNotificationSourceChanged(data)
            }
            AncsConstants.DATA_SOURCE_UUID.toString().uppercase() -> {
                Log.d(TAG, "Data Source: ${data.size} bytes")
                onDataSourceChanged(data)
            }
            AncsConstants.CALL_STATE_CHARACTERISTIC_UUID.toString().uppercase() -> {
                Log.d(TAG, "Call State: ${data.toHexString()}")
                onCallStateChanged?.invoke(data)
            }
            else -> {
                Log.d(TAG, "Unknown characteristic changed: $uuid")
            }
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        Log.d(TAG, "Descriptor written: ${descriptor.characteristic.uuid} status=$status")
        onDescriptorWritten(descriptor, status)
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        Log.d(TAG, "Characteristic written: ${characteristic.uuid} status=$status")
        onCharacteristicWritten(characteristic, status)
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val value = characteristic.value ?: return
            Log.d(TAG, "Characteristic read: ${characteristic.uuid} (${value.size} bytes)")
            onCharacteristicReadCallback?.invoke(characteristic, value)
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Log.d(TAG, "MTU changed: mtu=$mtu status=$status")
        onMtuChanged(mtu, status)
    }

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02X".format(it) }
}
