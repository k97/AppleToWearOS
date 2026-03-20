# Gotchas & Past Bugs

1. **GattCallback parameter naming**: The `onServicesDiscovered` lambda was named the same as the override method, causing infinite recursion (StackOverflowError). Renamed to `onServicesDiscoveredCallback`.

2. **Notification channel caching**: Android permanently caches channel settings. `deleteNotificationChannel()` + recreate with same ID does NOT reset vibration/importance. Must use new channel IDs (versioned).

3. **getBondedIPhone() device name**: The bonded iPhone may appear as "AncsBrid" (truncated) instead of "iPhone". Match multiple name patterns.

4. **iOS entitlements**: `com.apple.developer.bluetooth.peripheral` requires a paid Apple Developer account with proper provisioning. Can be removed — ANCS works without the companion app.

5. **Wear OS full-screen intent**: `setFullScreenIntent()` alone doesn't reliably launch activities on Wear OS. Also call `startActivity()` directly from the service.

6. **macOS `find` commands**: Avoid `find ~` on the home directory — triggers macOS privacy permission dialogs for Music, Desktop, Documents, etc.

7. **`setNotificationSilent()` kills heads-up overlays**: On Wear OS, calling `setNotificationSilent()` on a notification sets `sound=null` which prevents heads-up/overlay display even with IMPORTANCE_HIGH channels. Never use it — let channel settings handle sound, use direct Vibrator API for haptics.

8. **`USE_FULL_SCREEN_INTENT` permission**: On Android 14+ (API 34), this is a special app-op that must be explicitly granted: `adb shell appops set <pkg> USE_FULL_SCREEN_INTENT allow`. Without it, full-screen intents are silently rejected.

9. **Telecom self-managed ConnectionService**: Self-managed calls do NOT use the system's InCallService UI. The app must handle its own call UI. Only `CallProvider` connections (like the native dialer) get the system call screen. Our self-managed connection still gets priority over the charging screen though.

10. **GATT writes trigger system haptics**: During attribute fetching, GATT characteristic writes trigger system haptic feedback on Wear OS. This caused constant vibration on reconnect when pre-existing notifications triggered attribute fetches. Fixed by skipping pre-existing notifications at NS event level — no attribute fetch = no GATT writes = no haptics.

11. **Notification channel sound for overlays**: Channels must have a sound set (even the default `notification_sound`) for heads-up overlays to trigger. Channels with `setSound(null, null)` won't show overlays regardless of importance level.

12. **Double ANCS subscription on rediscovery**: When `discoverServices()` is called again (e.g., to find the companion service), the `onServicesDiscovered` callback must check if ANCS is already subscribed to avoid re-subscribing and resetting the session.

13. **Android auto-grouping silences notifications**: When multiple notifications are posted from the same app, Android auto-groups them and applies `GROUP_ALERT_SUMMARY`, adding the `SILENT` flag to all child notifications. This kills overlays, vibration, and sound. Fix: each notification must have a unique group key (`ancs_{uid}`) to prevent auto-grouping.

14. **Samsung One UI Watch overlay needs MessagingStyle**: Samsung's heads-up overlay only shows message body for `MessagingStyle` notifications. `BigTextStyle` shows content in the notification list but NOT in the overlay popup. Always use `MessagingStyle` for message-type notifications.

15. **Multi-watch ANCS prioritization**: When multiple watches are bonded to the same iPhone, ANCS uses last-in-first-get — the watch that subscribed to Notification Source most recently receives events. Only one watch active at a time.

16. **ANCS doesn't send REMOVE when call is answered**: When an incoming call is answered on iPhone, ANCS sends a MODIFIED event (EventID=1), not REMOVED. REMOVED only comes when the call fully ends. The call overlay must handle MODIFIED to transition to "Active on iPhone" state, otherwise it stays showing "Incoming Call" for the entire call duration.
