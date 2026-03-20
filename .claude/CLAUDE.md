# WearBridge (package: com.wearos.ancsbridge)

iPhone → Wear OS notification mirroring via BLE/ANCS. All local, no cloud relay.

**Display Name**: WearBridge (package names unchanged: `com.wearos.ancsbridge`)
**iOS Bundle ID**: `com.wearos.ancsbridge.companion`

## Commands

```bash
# Build Wear OS (no local Java — use Android Studio's JBR)
cd wearos-app
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug

# Deploy to watch
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.wearos.ancsbridge
adb shell am start -n com.wearos.ancsbridge/.ui.MainActivity

# Build iOS companion (optional — ANCS works without it)
cd ios-app && xcodegen generate
xcodebuild -project AncsBridge.xcodeproj -scheme AncsBridge -sdk iphoneos build

# Logs
adb logcat --pid=$(adb shell pidof com.wearos.ancsbridge)
```

## Hardware

- **Phone**: iPhone 17 Pro Max (iOS 26)
- **Primary Watch**: Google Pixel Watch 2 (Wear OS 5)
- **Secondary Watch**: Samsung Galaxy Watch 5 (Wear OS) — same APK, verified

## Critical Rules

- **Never use `setNotificationSilent()`** — kills heads-up overlays on Wear OS
- **Notification channels are versioned** (`_v7`) — bump version to change settings, Android caches permanently
- **Subscribe Data Source BEFORE Notification Source** — Apple ANCS requirement
- **Skip pre-existing notifications at NS event level** — prevents GATT writes which trigger system haptics
- **macOS**: avoid `find ~` — triggers privacy permission dialogs

## Rules

- [BLE & ANCS Protocol](/.claude/rules/ble-ancs-protocol.md) — connection flow, Telecom calls, channels, icon mapping
- [Gotchas & Past Bugs](/.claude/rules/gotchas.md) — 16 documented footguns
- [Future Work](/.claude/rules/future-work.md) — full-color icons, multi-device, health data

## Verification

After making changes:
- `cd wearos-app && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug` — Build Wear OS app
- `cd ios-app && xcodebuild -project AncsBridge.xcodeproj -scheme AncsBridge -sdk iphoneos build` — Build iOS app
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — Deploy and test on watch
