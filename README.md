# AppleToWearOS

Mirror iPhone notifications to Wear OS watches over Bluetooth Low Energy using Apple's ANCS (Apple Notification Center Service) protocol. Fully local — no cloud relay, no companion phone app required.

```
iPhone (iOS 26)                       Wear OS Watch
┌─────────────────────┐               ┌──────────────────────────┐
│                     │               │                          │
│  System ANCS        │◄──BLE bond──►│  BLE Central / GATT      │
│  (built-in)         │               │  ANCS Protocol Parser    │
│                     │               │  Notification Manager    │
│                     │               │  Foreground Service      │
│                     │               │                          │
└─────────────────────┘               └──────────────────────────┘
```

## Features

- **Real-time notification mirroring** — messages, emails, calls, calendar alerts, and more
- **75+ app icon mappings** — WhatsApp, Slack, Gmail, Instagram, Telegram, Discord, and many more show their own icons
- **Incoming call screen** — full-screen UI with Answer, Decline, and Quick Reply options
- **Smart notification filtering** — skips pre-existing notifications on reconnect, respects iOS silent flags
- **Auto-reconnection** — exponential backoff with `autoConnect` for bonded devices
- **Boot persistence** — service auto-starts on watch reboot
- **Haptic feedback** — category-aware vibration patterns (gentle for emails, stronger for calls)
- **No cloud dependency** — everything runs over local BLE

## Tested Hardware

| Device | Role |
|--------|------|
| iPhone 17 Pro Max (iOS 26) | Notification source |
| Google Pixel Watch 2 (Wear OS 5) | Primary watch |
| Samsung Galaxy Watch (Wear OS) | Secondary — same APK |

## How It Works

ANCS is a system-level BLE service built into iOS. Any bonded BLE accessory can subscribe to iPhone notifications without needing an iOS app. The watch acts as a BLE central device and GATT client:

1. Watch scans and bonds with iPhone
2. Subscribes to ANCS characteristics (Data Source, then Notification Source)
3. Receives 8-byte notification events in real-time
4. Fetches notification attributes (title, message, app ID) via Control Point
5. Reassembles fragmented responses and posts native Android notifications

## Project Structure

```
├── wearos-app/          # Wear OS app (Kotlin, Jetpack Compose)
│   └── app/src/main/java/com/wearos/ancsbridge/
│       ├── ble/         # BLE scanning, connection, GATT callbacks
│       ├── ancs/        # ANCS protocol: parsing, assembly, service
│       ├── model/       # Data classes
│       ├── ui/          # Compose UI (main screen, call screen)
│       └── viewmodel/   # ViewModel
│
├── ios-app/             # iOS companion app (Swift, optional)
│   └── AncsBridge/
│       ├── BLE/         # Peripheral manager
│       ├── Views/       # SwiftUI views
│       └── ViewModels/  # BLE view model
│
└── master-docs/         # Research notes and ANCS spec references
```

## Build

### Wear OS App

```bash
cd wearos-app
./gradlew assembleDebug
```

Install on watch via ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### iOS Companion App (Optional)

Open `ios-app/AncsBridge.xcodeproj` in Xcode and build. The companion app provides pairing status UI but is **not required** — ANCS works without it once the watch is bonded.

## Setup

1. Install the APK on your Wear OS watch
2. Open the app and tap **Scan**
3. Select your iPhone from the device list
4. Accept the Bluetooth pairing dialog on the iPhone
5. Notifications will start mirroring automatically

## Supported Apps

75+ iOS apps are mapped with custom icons, including:

WhatsApp, iMessage, Gmail, Outlook, Slack, Discord, Telegram, Signal, Instagram, Threads, Facebook Messenger, Google Maps, Apple Music, YouTube, Uber, Uber Eats, Notion, GitHub, LinkedIn, Twitter/X, Apple Calendar, Apple Reminders, Apple Wallet, and many more.

Unknown apps fall back to category-based icons (phone for calls, envelope for email, etc.).

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
