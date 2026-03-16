package com.wearos.ancsbridge.ble

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Receives bond state changes. After bonding completes, ANCS service becomes accessible.
 */
class BondStateReceiver(
    private val onBonded: (BluetoothDevice) -> Unit,
    private val onBondFailed: (BluetoothDevice) -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "BondStateReceiver"

        val intentFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            ?: return
        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
        val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)

        Log.d(TAG, "Bond state changed: ${bondStateName(prevState)} -> ${bondStateName(bondState)} for ${device.address}")

        when (bondState) {
            BluetoothDevice.BOND_BONDED -> {
                Log.i(TAG, "Device bonded: ${device.address}")
                onBonded(device)
            }
            BluetoothDevice.BOND_NONE -> {
                if (prevState == BluetoothDevice.BOND_BONDING) {
                    Log.w(TAG, "Bond failed for: ${device.address}")
                    onBondFailed(device)
                }
            }
        }
    }

    private fun bondStateName(state: Int): String = when (state) {
        BluetoothDevice.BOND_NONE -> "NONE"
        BluetoothDevice.BOND_BONDING -> "BONDING"
        BluetoothDevice.BOND_BONDED -> "BONDED"
        else -> "UNKNOWN($state)"
    }
}
