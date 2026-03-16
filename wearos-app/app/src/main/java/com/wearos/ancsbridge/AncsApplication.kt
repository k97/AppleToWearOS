package com.wearos.ancsbridge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.wearos.ancsbridge.ble.AncsConstants

class AncsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Delete ALL old channel versions so settings take effect.
        // Android caches channel settings permanently once created.
        val oldVersions = listOf("", "_v2", "_v3", "_v4")
        val channelNames = listOf(
            "ancs_service", "ancs_incoming_call", "ancs_messages",
            "ancs_email", "ancs_social", "ancs_schedule", "ancs_other"
        )
        for (v in oldVersions) {
            for (name in channelNames) {
                manager.deleteNotificationChannel("$name$v")
            }
        }

        // All channels have vibration DISABLED — we handle vibration directly
        // via the Vibrator API in AncsService for reliable haptics on Wear OS.
        val channels = listOf(
            NotificationChannel(
                CHANNEL_SERVICE,
                "ANCS Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background connection to iPhone" },

            NotificationChannel(
                CHANNEL_INCOMING_CALL,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming phone calls from iPhone"
                setBypassDnd(true)
                enableVibration(false)
                setSound(null, null)
            },

            NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "iMessage, WhatsApp, and other messaging apps"
                enableVibration(false)
                setSound(null, null)
            },

            NotificationChannel(
                CHANNEL_EMAIL,
                "Email",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Email notifications"
                enableVibration(false)
                setSound(null, null)
            },

            NotificationChannel(
                CHANNEL_SOCIAL,
                "Social",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Social media notifications"
                enableVibration(false)
                setSound(null, null)
            },

            NotificationChannel(
                CHANNEL_SCHEDULE,
                "Schedule & Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Calendar reminders, alarms, and timers from iPhone"
                setBypassDnd(true)
                enableVibration(false)
                setSound(null, null)
            },

            NotificationChannel(
                CHANNEL_OTHER,
                "Other",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Other notifications"
                enableVibration(false)
                setSound(null, null)
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        // Channel IDs are versioned — bump the suffix when changing channel settings
        // because Android caches channel settings permanently once created.
        private const val CHANNEL_VERSION = "v5"
        const val CHANNEL_SERVICE = "ancs_service_$CHANNEL_VERSION"
        const val CHANNEL_INCOMING_CALL = "ancs_incoming_call_$CHANNEL_VERSION"
        const val CHANNEL_MESSAGES = "ancs_messages_$CHANNEL_VERSION"
        const val CHANNEL_EMAIL = "ancs_email_$CHANNEL_VERSION"
        const val CHANNEL_SOCIAL = "ancs_social_$CHANNEL_VERSION"
        const val CHANNEL_SCHEDULE = "ancs_schedule_$CHANNEL_VERSION"
        const val CHANNEL_OTHER = "ancs_other_$CHANNEL_VERSION"

        fun channelForCategory(categoryId: Int): String = when (categoryId) {
            AncsConstants.CATEGORY_INCOMING_CALL -> CHANNEL_INCOMING_CALL
            AncsConstants.CATEGORY_MISSED_CALL -> CHANNEL_MESSAGES
            AncsConstants.CATEGORY_VOICEMAIL -> CHANNEL_MESSAGES
            AncsConstants.CATEGORY_SOCIAL -> CHANNEL_SOCIAL
            AncsConstants.CATEGORY_SCHEDULE -> CHANNEL_SCHEDULE
            AncsConstants.CATEGORY_EMAIL -> CHANNEL_EMAIL
            AncsConstants.CATEGORY_NEWS -> CHANNEL_OTHER
            AncsConstants.CATEGORY_HEALTH_FITNESS -> CHANNEL_OTHER
            AncsConstants.CATEGORY_BUSINESS_FINANCE -> CHANNEL_OTHER
            AncsConstants.CATEGORY_LOCATION -> CHANNEL_OTHER
            AncsConstants.CATEGORY_ENTERTAINMENT -> CHANNEL_OTHER
            else -> CHANNEL_OTHER
        }
    }
}
