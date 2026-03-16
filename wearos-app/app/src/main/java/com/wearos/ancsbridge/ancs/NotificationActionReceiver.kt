package com.wearos.ancsbridge.ancs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives notification action button presses (positive/negative)
 * and forwards them to AncsService for Control Point writing.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotifActionReceiver"
        const val ACTION_POSITIVE = "com.wearos.ancsbridge.ACTION_POSITIVE"
        const val ACTION_NEGATIVE = "com.wearos.ancsbridge.ACTION_NEGATIVE"
        const val EXTRA_NOTIFICATION_UID = "notification_uid"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getLongExtra(EXTRA_NOTIFICATION_UID, -1)
        if (uid == -1L) {
            Log.w(TAG, "No notification UID in intent")
            return
        }

        val actionId = when (intent.action) {
            ACTION_POSITIVE -> 0
            ACTION_NEGATIVE -> 1
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
                return
            }
        }

        Log.d(TAG, "Notification action: uid=$uid actionId=$actionId")

        // Forward to service
        val serviceIntent = Intent(context, AncsService::class.java).apply {
            action = AncsService.ACTION_PERFORM_NOTIFICATION_ACTION
            putExtra(EXTRA_NOTIFICATION_UID, uid)
            putExtra(AncsService.EXTRA_ACTION_ID, actionId)
        }
        context.startService(serviceIntent)
    }
}
