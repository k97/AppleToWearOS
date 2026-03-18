package com.wearos.ancsbridge.ancs

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.IBinder
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wearos.ancsbridge.AncsApplication
import com.wearos.ancsbridge.R
import com.wearos.ancsbridge.ble.AncsConstants
import com.wearos.ancsbridge.ble.BleConnectionManager
import com.wearos.ancsbridge.ble.BleScanner
import com.wearos.ancsbridge.ble.BondStateReceiver
import com.wearos.ancsbridge.model.AncsEvent
import com.wearos.ancsbridge.model.AncsNotification
import com.wearos.ancsbridge.model.ConnectionState
import com.wearos.ancsbridge.ui.IncomingCallActivity
import com.wearos.ancsbridge.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that manages the BLE connection to iPhone and ANCS notification pipeline.
 *
 * Pipeline: BLE scan → connect → bond → subscribe → receive NS events →
 *           fetch attributes via CP → parse DS response → post Android notification
 */
@SuppressLint("MissingPermission")
class AncsService : Service() {

    companion object {
        private const val TAG = "AncsService"
        private const val SERVICE_NOTIFICATION_ID = 1
        private const val NOTIFICATION_ID_BASE = 1000

        const val ACTION_START = "com.wearos.ancsbridge.START"
        const val ACTION_STOP = "com.wearos.ancsbridge.STOP"
        const val ACTION_CONNECT = "com.wearos.ancsbridge.CONNECT"
        const val ACTION_DISCONNECT = "com.wearos.ancsbridge.DISCONNECT"
        const val ACTION_RECONNECT = "com.wearos.ancsbridge.RECONNECT"
        const val ACTION_PERFORM_NOTIFICATION_ACTION = "com.wearos.ancsbridge.PERFORM_ACTION"
        const val ACTION_SEND_QUICK_REPLY = "com.wearos.ancsbridge.QUICK_REPLY"
        const val ACTION_CLEAR_ALL = "com.wearos.ancsbridge.CLEAR_ALL"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_ACTION_ID = "action_id"
        const val EXTRA_QUICK_REPLY_TEXT = "quick_reply_text"
        const val NOTIFICATION_ID_CALL = 999
        private const val MAX_ACTIVE_NOTIFICATIONS = 20 // Android limit is 25; keep headroom

        /** Shared connection state observable by the ViewModel */
        private val _sharedConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
        val sharedConnectionState: StateFlow<ConnectionState> = _sharedConnectionState.asStateFlow()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var connectionManager: BleConnectionManager
    private lateinit var scanner: BleScanner
    private lateinit var notificationManager: NotificationManager
    private lateinit var vibrator: Vibrator

    private val dataSourceAssembler = DataSourceAssembler()
    private val appNameCache = mutableMapOf<String, String>()

    // Track active notification IDs in posting order (oldest first) for auto-cleanup
    private val activeNotificationIds = ArrayDeque<Int>()

    // Vibration cooldown — don't vibrate more than once every 3 seconds

    // Reconnection grace period — suppress vibration for notifications arriving
    // in the first 60 seconds after subscribing (they're backlog, not truly new)
    private var subscriptionTime = 0L
    private var activeCallUid: Long = -1
    private val reconnectionGracePeriodMs = 60_000L

    // Serial queue for Control Point requests
    private val attributeRequestQueue = Channel<AttributeRequest>(Channel.BUFFERED)
    private var requestProcessorJob: Job? = null

    private data class AttributeRequest(
        val event: AncsEvent,
        val requestedAttributes: List<Int>
    )

    override fun onCreate() {
        super.onCreate()
        connectionManager = BleConnectionManager(this)
        scanner = BleScanner(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        vibrator = getSystemService(Vibrator::class.java)

        // Register bond state receiver
        registerReceiver(connectionManager.bondStateReceiver, BondStateReceiver.intentFilter)

        // Register PhoneAccount for Telecom framework integration
        // This lets us show incoming calls via the system call UI
        AncsConnectionService.registerPhoneAccount(this)

        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Starting..."))

        // Listen for Notification Source events
        scope.launch {
            connectionManager.notificationSourceEvents.collect { data ->
                handleNotificationSourceEvent(data)
            }
        }

        // Listen for Data Source events
        scope.launch {
            connectionManager.dataSourceEvents.collect { data ->
                handleDataSourceEvent(data)
            }
        }

        // Update service notification and shared state based on connection state
        scope.launch {
            connectionManager.connectionState.collectLatest { state ->
                // Publish to shared flow so ViewModel can observe
                _sharedConnectionState.value = state

                val text = when (state) {
                    is ConnectionState.Idle -> "Idle"
                    is ConnectionState.Scanning -> "Scanning..."
                    is ConnectionState.Connecting -> "Connecting to ${state.deviceName ?: "iPhone"}..."
                    is ConnectionState.Bonding -> "Pairing with ${state.deviceName ?: "iPhone"}..."
                    is ConnectionState.Connected -> {
                        subscriptionTime = System.currentTimeMillis()
                        "Connected to ${state.deviceName ?: "iPhone"}"
                    }
                    is ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.Error -> "Error: ${state.message}"
                }
                notificationManager.notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(text))
            }
        }

        // Start the attribute request processor
        startRequestProcessor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> {
                // Try to reconnect to bonded iPhone
                tryReconnectBonded()
            }
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (address != null) {
                    val device = scanner.let {
                        val manager = getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                        manager.adapter.getRemoteDevice(address)
                    }
                    connectionManager.connect(device)
                }
            }
            ACTION_DISCONNECT -> {
                connectionManager.disconnect()
            }
            ACTION_RECONNECT -> {
                tryReconnectBonded()
            }
            ACTION_PERFORM_NOTIFICATION_ACTION -> {
                val uid = intent.getLongExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, -1)
                val actionId = intent.getIntExtra(EXTRA_ACTION_ID, -1)
                if (uid != -1L && actionId != -1) {
                    performNotificationAction(uid, actionId)
                }
            }
            ACTION_CLEAR_ALL -> {
                Log.i(TAG, "Clearing all notifications (${activeNotificationIds.size} tracked)")
                activeNotificationIds.forEach { id ->
                    notificationManager.cancel(id)
                }
                activeNotificationIds.clear()
                notificationManager.cancel(NOTIFICATION_ID_CALL)
            }
            ACTION_STOP -> {
                connectionManager.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(connectionManager.bondStateReceiver)
        } catch (_: IllegalArgumentException) { }
        connectionManager.destroy()
        attributeRequestQueue.close()
        scope.cancel()
    }

    private fun tryReconnectBonded() {
        val device = connectionManager.getBondedIPhone()
        if (device != null) {
            Log.i(TAG, "Found bonded device: ${device.name ?: device.address}")
            connectionManager.reconnect(device)
        } else {
            Log.i(TAG, "No bonded iPhone found")
        }
    }

    private fun handleNotificationSourceEvent(data: ByteArray) {
        val event = NotificationSourceParser.parse(data) ?: run {
            Log.w(TAG, "Failed to parse NS event")
            return
        }

        Log.d(TAG, "NS event: id=${event.eventId} cat=${event.categoryId} uid=${event.notificationUid}")

        when {
            event.isAdded || event.isModified -> {
                // Skip pre-existing notifications at the NS level — don't even
                // fetch attributes. This avoids GATT writes that trigger system haptics.
                if (event.isPreExisting) {
                    Log.d(TAG, "Skipping pre-existing NS event uid=${event.notificationUid}")
                    return
                }

                // Also skip during grace period (non-flagged backlog)
                val now = System.currentTimeMillis()
                if ((now - subscriptionTime) < reconnectionGracePeriodMs && !event.isIncomingCall) {
                    Log.d(TAG, "Skipping NS event during grace period uid=${event.notificationUid}")
                    return
                }

                if (event.isIncomingCall) {
                    // IMMEDIATELY show call screen — don't wait for attribute fetch.
                    // The NS payload already tells us it's a call. We'll update
                    // the caller name later if attributes arrive in time.
                    Log.i(TAG, "Incoming call NS event (cat=1) uid=${event.notificationUid} — showing call screen NOW")
                    showIncomingCall(AncsNotification(
                        uid = event.notificationUid,
                        categoryId = event.categoryId,
                        eventFlags = event.eventFlags,
                        appIdentifier = "com.apple.mobilephone",
                        title = "Incoming Call",
                        subtitle = null,
                        message = "",
                        date = null,
                        appDisplayName = "Phone",
                        positiveActionLabel = "Answer",
                        negativeActionLabel = "Decline"
                    ))
                }

                // Build list of attributes to request
                val attrs = mutableListOf(
                    AncsConstants.ATTR_APP_IDENTIFIER,
                    AncsConstants.ATTR_TITLE,
                    AncsConstants.ATTR_SUBTITLE,
                    AncsConstants.ATTR_MESSAGE,
                    AncsConstants.ATTR_DATE
                )
                if (event.hasPositiveAction) attrs.add(AncsConstants.ATTR_POSITIVE_ACTION_LABEL)
                if (event.hasNegativeAction) attrs.add(AncsConstants.ATTR_NEGATIVE_ACTION_LABEL)

                // Enqueue attribute fetch (for all notifications including calls)
                scope.launch {
                    attributeRequestQueue.send(AttributeRequest(event, attrs))
                }
            }
            event.isRemoved -> {
                // Cancel the Android notification and remove from tracking
                val removedId = notificationIdForUid(event.notificationUid)
                notificationManager.cancel(removedId)
                activeNotificationIds.remove(removedId)

                // If this was an incoming call, dismiss everything
                if (event.isIncomingCall || activeCallUid == event.notificationUid) {
                    Log.i(TAG, "Call ended (REMOVE) uid=${event.notificationUid}")
                    activeCallUid = -1
                    vibrator.cancel()
                    notificationManager.cancel(NOTIFICATION_ID_CALL)
                    AncsConnectionService.endActiveCall()
                    sendBroadcast(Intent(IncomingCallActivity.ACTION_CALL_ENDED).apply {
                        setPackage(packageName)
                    })
                }
            }
        }
    }

    private fun handleDataSourceEvent(data: ByteArray) {
        val notification = dataSourceAssembler.onDataReceived(data)
        if (notification != null) {
            Log.i(TAG, "Notification complete: ${notification.title} - ${notification.message}")
            scope.launch {
                postNotification(notification)
            }
        }
    }

    private fun startRequestProcessor() {
        requestProcessorJob = scope.launch {
            for (request in attributeRequestQueue) {
                processAttributeRequest(request)
            }
        }
    }

    private suspend fun processAttributeRequest(request: AttributeRequest) {
        val event = request.event

        // Prepare the assembler to expect this response
        dataSourceAssembler.expectNotificationAttributes(
            uid = event.notificationUid,
            requestedAttributes = request.requestedAttributes,
            category = event.categoryId,
            flags = event.eventFlags
        )

        // Write the Control Point request
        val cpData = ControlPointWriter.buildGetNotificationAttributes(
            uid = event.notificationUid,
            hasPositiveAction = event.hasPositiveAction,
            hasNegativeAction = event.hasNegativeAction
        )

        val success = connectionManager.writeControlPoint(cpData)
        if (!success) {
            Log.e(TAG, "Failed to write Control Point for uid=${event.notificationUid}")
            dataSourceAssembler.reset()
        }

        // The response will come asynchronously via dataSourceEvents -> handleDataSourceEvent
        // We add a small delay before processing the next request to allow the DS response to complete
        kotlinx.coroutines.delay(500)
    }

    private suspend fun postNotification(notification: AncsNotification) {
        val isPreExisting = notification.eventFlags and AncsConstants.EVENT_FLAG_PRE_EXISTING != 0

        Log.d(TAG, "postNotification: uid=${notification.uid} cat=${notification.categoryId} " +
            "flags=0x${notification.eventFlags.toString(16)} app=${notification.appIdentifier} " +
            "title=${notification.title} preExisting=$isPreExisting")

        // Resolve app display name if not cached
        val appName = resolveAppName(notification.appIdentifier)
        val updatedNotification = notification.copy(appDisplayName = appName)

        // Detect incoming calls by category OR by message content
        // WhatsApp sends calls as cat=4 (Social) with "☎" or "Incoming" in message
        // Regular phone may send as cat=1 (IncomingCall) or cat=2 with "Incoming" content
        val callApps = setOf(
            "net.whatsapp.WhatsApp", "net.whatsapp.WhatsAppSMB",
            "com.apple.mobilephone", "com.apple.facetime"
        )
        val messageAndTitle = "${notification.message} ${notification.title}"
        val hasCallIndicator = messageAndTitle.contains("Incoming Call", ignoreCase = true) ||
            messageAndTitle.contains("Incoming Voice", ignoreCase = true) ||
            messageAndTitle.contains("Incoming Video", ignoreCase = true) ||
            messageAndTitle.contains("Incoming Audio", ignoreCase = true) ||
            messageAndTitle.contains("☎") ||
            messageAndTitle.contains("Audio Call", ignoreCase = true) ||
            messageAndTitle.contains("Video Call", ignoreCase = true) ||
            messageAndTitle.contains("Voice Call", ignoreCase = true) ||
            messageAndTitle.contains("is calling", ignoreCase = true) ||
            messageAndTitle.contains("Ringing", ignoreCase = true)
        val isIncomingCall = notification.categoryId == AncsConstants.CATEGORY_INCOMING_CALL ||
            (notification.positiveActionLabel != null && hasCallIndicator) ||
            (notification.appIdentifier in callApps && hasCallIndicator)

        if (isIncomingCall) {
            Log.i(TAG, "Showing incoming call screen for ${notification.title}")
            showIncomingCall(updatedNotification)
            return
        }

        // Skip pre-existing notifications — they're already on the phone.
        if (isPreExisting) {
            Log.d(TAG, "Skipping pre-existing notification uid=${notification.uid}")
            return
        }

        // Grace period and pre-existing checks are now done at the NS event level
        // (handleNotificationSourceEvent) so we never even fetch attributes for those.

        // Route alarm/timer apps to the schedule channel regardless of ANCS category
        val alarmApps = setOf("com.apple.mobiletimer", "com.apple.reminders", "com.apple.mobilecal")
        val channelId = if (notification.appIdentifier in alarmApps) {
            AncsApplication.CHANNEL_SCHEDULE
        } else {
            AncsApplication.channelForCategory(notification.categoryId)
        }
        val iconResId = AppIconMapper.getIconResId(notification.appIdentifier, notification.categoryId)
        val notifId = notificationIdForUid(notification.uid)

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconResId)
            .setContentTitle(appName ?: notification.appIdentifier)
            .setContentText(buildNotificationText(updatedNotification))
            .setSubText(notification.title)
            .setWhen(parseAncsDate(notification.date))
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidCategory(notification.categoryId))

        // Add full message in expanded view
        if (notification.message.isNotEmpty()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${notification.title}\n${notification.message}")
                    .setSummaryText(appName)
            )
        }

        // Add action buttons if available
        if (notification.positiveActionLabel != null) {
            val positiveIntent = PendingIntent.getBroadcast(
                this, notifId,
                Intent(NotificationActionReceiver.ACTION_POSITIVE).apply {
                    setPackage(packageName)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, notification.uid)
                },
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_check, notification.positiveActionLabel, positiveIntent)
        }

        if (notification.negativeActionLabel != null) {
            val negativeIntent = PendingIntent.getBroadcast(
                this, notifId + 500_000,
                Intent(NotificationActionReceiver.ACTION_NEGATIVE).apply {
                    setPackage(packageName)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, notification.uid)
                },
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_close, notification.negativeActionLabel, negativeIntent)
        }

        // Never call setNotificationSilent() — it suppresses the heads-up overlay
        // on Wear OS. Let the channel handle sound/vibration. We handle vibration
        // manually via the Vibrator API with cooldown, so no double-buzz.

        // Auto-cleanup: evict oldest notifications if approaching the system cap
        evictOldestIfNeeded()

        notificationManager.notify(notifId, builder.build())
        activeNotificationIds.addLast(notifId)
    }

    /**
     * Evict the oldest notifications to stay under the system limit.
     * Keeps MAX_ACTIVE_NOTIFICATIONS slots, leaving room for service + call notifications.
     */
    private fun evictOldestIfNeeded() {
        while (activeNotificationIds.size >= MAX_ACTIVE_NOTIFICATIONS) {
            val oldId = activeNotificationIds.removeFirst()
            notificationManager.cancel(oldId)
            Log.d(TAG, "Auto-evicted oldest notification id=$oldId (${activeNotificationIds.size} active)")
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun showIncomingCall(notification: AncsNotification) {
        val callerName = notification.title.ifEmpty { "Incoming Call" }
        val appName = notification.appDisplayName ?: "Phone"

        // Track active call UID to avoid duplicate showIncomingCall calls
        if (activeCallUid == notification.uid) {
            // Already showing — just update caller name if it changed
            if (callerName != "Incoming Call") {
                AncsConnectionService.activeConnection?.updateCallerName(callerName)
                // Also update the IncomingCallActivity via broadcast
                sendBroadcast(Intent(IncomingCallActivity.ACTION_CALLER_NAME_UPDATED).apply {
                    setPackage(packageName)
                    putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
                })
                Log.i(TAG, "Updated caller name to '$callerName' for uid=${notification.uid}")
            }
            return
        }
        activeCallUid = notification.uid

        // 1. Report to Telecom framework — self-managed connection allows
        //    our foreground service to launch activities while ringing
        AncsConnectionService.reportIncomingCall(
            this, notification.uid, callerName, appName
        )

        // 2. Directly launch call activity — the Telecom self-managed connection
        //    grants us the background activity start exception
        val callIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
            putExtra(IncomingCallActivity.EXTRA_NOTIFICATION_UID, notification.uid)
            putExtra(IncomingCallActivity.EXTRA_APP_NAME, appName)
        }
        try {
            startActivity(callIntent)
            Log.i(TAG, "Started IncomingCallActivity directly for '$callerName'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start IncomingCallActivity: ${e.message}")
        }

        // 3. Post CallStyle notification as fallback
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val answerPendingIntent = PendingIntent.getActivity(
            this, 1, callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declinePendingIntent = PendingIntent.getBroadcast(
            this, 2,
            Intent(NotificationActionReceiver.ACTION_NEGATIVE).apply {
                setPackage(packageName)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, notification.uid)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val caller = androidx.core.app.Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()
        val callNotification = NotificationCompat.Builder(this, AncsApplication.CHANNEL_INCOMING_CALL)
            .setSmallIcon(R.drawable.ic_phone)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller, declinePendingIntent, answerPendingIntent
                )
            )
            .build()
        notificationManager.notify(NOTIFICATION_ID_CALL, callNotification)
        Log.i(TAG, "Posted CallStyle notification for '$callerName'")
    }


    private fun performNotificationAction(uid: Long, actionId: Int) {
        val data = ControlPointWriter.buildPerformNotificationAction(uid, actionId)
        connectionManager.writeControlPoint(data)
        // Cancel vibration (in case this is a call answer/decline)
        vibrator.cancel()
        // Cancel the Android notification and remove from tracking
        val notifId = notificationIdForUid(uid)
        notificationManager.cancel(notifId)
        activeNotificationIds.remove(notifId)
    }

    private fun resolveAppName(appIdentifier: String): String? {
        if (appIdentifier.isEmpty()) return null
        appNameCache[appIdentifier]?.let { return it }

        // For now, derive a readable name from the bundle ID
        // A full implementation would use GetAppAttributes, but that requires
        // another CP/DS round trip. We use a simple mapping instead.
        val name = knownAppNames[appIdentifier] ?: run {
            // Extract last component: "com.apple.MobileSMS" -> "MobileSMS"
            appIdentifier.substringAfterLast(".")
        }
        appNameCache[appIdentifier] = name
        return name
    }

    private fun buildNotificationText(notification: AncsNotification): String {
        return buildString {
            if (notification.title.isNotEmpty()) {
                append(notification.title)
            }
            if (!notification.subtitle.isNullOrEmpty()) {
                if (isNotEmpty()) append(": ")
                append(notification.subtitle)
            }
            if (notification.message.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(notification.message)
            }
        }
    }

    private fun parseAncsDate(date: String?): Long {
        if (date == null) return System.currentTimeMillis()
        return try {
            val sdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.US)
            sdf.parse(date)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun androidCategory(categoryId: Int): String = when (categoryId) {
        AncsConstants.CATEGORY_INCOMING_CALL -> Notification.CATEGORY_CALL
        AncsConstants.CATEGORY_MISSED_CALL -> Notification.CATEGORY_MISSED_CALL
        AncsConstants.CATEGORY_VOICEMAIL -> Notification.CATEGORY_VOICEMAIL
        AncsConstants.CATEGORY_SOCIAL -> Notification.CATEGORY_SOCIAL
        AncsConstants.CATEGORY_SCHEDULE -> Notification.CATEGORY_EVENT
        AncsConstants.CATEGORY_EMAIL -> Notification.CATEGORY_EMAIL
        else -> Notification.CATEGORY_MESSAGE
    }

    private fun notificationIdForUid(uid: Long): Int =
        (NOTIFICATION_ID_BASE + (uid % 500_000)).toInt()

    private fun buildServiceNotification(text: String): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AncsApplication.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification_default)
            .setContentTitle("AppleToWearOS")
            .setContentText(text)
            .setContentIntent(intent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private val knownAppNames = mapOf(
        // Apple Apps
        "com.apple.MobileSMS" to "Messages",
        "com.apple.mobilephone" to "Phone",
        "com.apple.mobilemail" to "Mail",
        "com.apple.mobilecal" to "Calendar",
        "com.apple.facetime" to "FaceTime",
        "com.apple.reminders" to "Reminders",
        "com.apple.news" to "News",
        "com.apple.mobilenotes" to "Notes",
        "com.apple.Health" to "Health",
        "com.apple.Fitness" to "Fitness",
        "com.apple.weather" to "Weather",
        "com.apple.Maps" to "Maps",
        "com.apple.Music" to "Music",
        "com.apple.stocks" to "Stocks",
        "com.apple.camera" to "Camera",
        "com.apple.Passbook" to "Wallet",
        "com.apple.Preferences" to "Settings",
        "com.apple.mobiletimer" to "Clock",
        "com.apple.podcasts" to "Podcasts",
        "com.apple.findmy" to "Find My",
        "com.apple.Photos" to "Photos",
        "com.apple.TestFlight" to "TestFlight",
        "com.apple.AppStore" to "App Store",
        "com.apple.Passwords" to "Passwords",
        "com.apple.tips" to "Tips",
        "com.apple.shortcuts" to "Shortcuts",
        "com.apple.Home" to "Home",
        "com.apple.iBooks" to "Books",
        "com.apple.tv" to "TV",
        "com.apple.ScreenTimeNotifications" to "Screen Time",

        // Messaging
        "net.whatsapp.WhatsApp" to "WhatsApp",
        "net.whatsapp.WhatsAppSMB" to "WhatsApp Business",
        "org.telegram.Telegram" to "Telegram",
        "com.facebook.Messenger" to "Messenger",
        "org.whispersystems.signal" to "Signal",
        "com.slack.Slack" to "Slack",
        "com.hammerandchisel.discord" to "Discord",

        // Social
        "com.atebits.Tweetie2" to "X",
        "com.burbn.instagram" to "Instagram",
        "com.facebook.Facebook" to "Facebook",
        "com.linkedin.LinkedIn" to "LinkedIn",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.toyopagroup.picaboo" to "Snapchat",
        "com.burbn.barcelona" to "Threads",
        "com.reddit.Reddit" to "Reddit",

        // Email & Productivity
        "com.google.Gmail" to "Gmail",
        "com.microsoft.Office.Outlook" to "Outlook",
        "com.google.calendar" to "Google Calendar",
        "notion.id" to "Notion",
        "com.grammarly.keyboard" to "Grammarly",

        // Google Apps
        "com.google.GoogleMobile" to "Google",
        "com.google.Maps" to "Google Maps",
        "com.google.Drive" to "Google Drive",
        "com.google.Photos" to "Google Photos",
        "com.google.photos" to "Google Photos",
        "com.google.Home" to "Google Home",
        "com.google.chrome.ios" to "Chrome",
        "com.google.ios.youtube" to "YouTube",

        // Entertainment & Media
        "com.spotify.client" to "Spotify",
        "com.shazam.Shazam" to "Shazam",

        // Ride-sharing & Delivery
        "com.ubercab.UberClient" to "Uber",
        "com.ubercab.UberEats" to "Uber Eats",

        // Finance & Banking
        "au.com.westpac.ConsultWPC" to "Westpac",
        "au.com.westpac.banking" to "Westpac",
        "com.stake.stake" to "Stake",
        "au.com.hellostake" to "Stake",
        "com.afterpay.afterpay-consumer" to "Afterpay",

        // Health & Fitness
        "com.anytimefitness.club" to "Anytime Fitness",
        "com.anytimefitness.atfmobile" to "Anytime Fitness",
        "au.com.hotdoc.app" to "HotDoc",
        "com.hotdoc.patient" to "HotDoc",

        // Telecom
        "com.jio.myjio" to "Jio",
        "com.ril.ajio" to "Jio",

        // Shopping & Services
        "au.com.auspost" to "AusPost",
        "com.auspost.MyPost" to "AusPost",

        // Smart Home
        "com.philips.hue.gen4" to "Hue",
        "com.signify.hue.blue" to "Hue",

        // Gaming
        "com.microsoft.smartglass" to "Xbox",
        "com.microsoft.xboxapp" to "Xbox",

        // VPN
        "com.surfshark.vpnclient" to "Surfshark",

        // Other
        "com.producthunt.ProductHuntApp" to "Product Hunt",
        "com.github.stormcrow" to "GitHub",
    )
}
