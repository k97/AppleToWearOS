package com.wearos.ancsbridge.ancs

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

/**
 * ConnectionService that integrates ANCS incoming calls with Android's Telecom framework.
 *
 * By registering as a self-managed ConnectionService, the system treats our
 * calls like native VoIP calls — the call UI shows over the charging screen,
 * lock screen, and gets full system priority.
 */
@SuppressLint("MissingPermission")
class AncsConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "AncsConnectionService"
        const val EXTRA_NOTIFICATION_UID = "notification_uid"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_APP_NAME = "app_name"

        // Track the active call connection so AncsService can update/end it
        var activeConnection: AncsCallConnection? = null
            private set

        fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
            return PhoneAccountHandle(
                ComponentName(context, AncsConnectionService::class.java),
                "ancs_bridge_account"
            )
        }

        /**
         * Register the PhoneAccount with TelecomManager. Must be called once
         * (e.g., on app startup or service start).
         */
        fun registerPhoneAccount(context: Context) {
            val handle = getPhoneAccountHandle(context)
            val account = PhoneAccount.builder(handle, "WearBridge")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .build()

            val telecomManager = context.getSystemService(TelecomManager::class.java)
            telecomManager.registerPhoneAccount(account)
            Log.i(TAG, "Registered PhoneAccount for WearBridge")
        }

        /**
         * Report an incoming call to the Telecom framework.
         * The system will show its native call UI (works over charging screen).
         */
        fun reportIncomingCall(
            context: Context,
            notificationUid: Long,
            callerName: String,
            appName: String
        ) {
            val handle = getPhoneAccountHandle(context)
            val extras = Bundle().apply {
                putLong(EXTRA_NOTIFICATION_UID, notificationUid)
                putString(EXTRA_CALLER_NAME, callerName)
                putString(EXTRA_APP_NAME, appName)
            }

            val telecomManager = context.getSystemService(TelecomManager::class.java)
            try {
                telecomManager.addNewIncomingCall(handle, extras)
                Log.i(TAG, "Reported incoming call to Telecom: '$callerName' uid=$notificationUid")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report incoming call to Telecom: ${e.message}")
            }
        }

        /**
         * End the active call (e.g., when ANCS sends REMOVE event).
         */
        fun endActiveCall() {
            activeConnection?.endCall()
            activeConnection = null
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val extras = request?.extras ?: Bundle()
        val uid = extras.getLong(EXTRA_NOTIFICATION_UID, -1)
        val callerName = extras.getString(EXTRA_CALLER_NAME, "Incoming Call")
        val appName = extras.getString(EXTRA_APP_NAME, "Phone")

        Log.i(TAG, "onCreateIncomingConnection: caller='$callerName' uid=$uid app='$appName'")

        val connection = AncsCallConnection(
            notificationUid = uid,
            onAnswer = { answerUid ->
                // Send ANCS positive action via AncsService
                val intent = Intent(this, AncsService::class.java).apply {
                    action = AncsService.ACTION_PERFORM_NOTIFICATION_ACTION
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, answerUid)
                    putExtra(AncsService.EXTRA_ACTION_ID, com.wearos.ancsbridge.ble.AncsConstants.ACTION_POSITIVE)
                }
                startService(intent)
                activeConnection = null
            },
            onReject = { rejectUid ->
                // Send ANCS negative action via AncsService
                val intent = Intent(this, AncsService::class.java).apply {
                    action = AncsService.ACTION_PERFORM_NOTIFICATION_ACTION
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, rejectUid)
                    putExtra(AncsService.EXTRA_ACTION_ID, com.wearos.ancsbridge.ble.AncsConstants.ACTION_NEGATIVE)
                }
                startService(intent)
                activeConnection = null
            }
        )

        connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED)
        connection.setAddress(
            Uri.fromParts("tel", "ANCS", null),
            TelecomManager.PRESENTATION_ALLOWED
        )
        connection.setRinging()

        activeConnection = connection
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "onCreateIncomingConnectionFailed — falling back to notification")
        // If Telecom rejects our call, the CallStyle notification from
        // AncsService will still be posted as a fallback.
    }
}
