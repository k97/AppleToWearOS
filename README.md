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
- **75+ app icon mappings** — WhatsApp, Slack, Gmail, Instagram, Telegram, Discord, and many more show their own icons (Material Design, 25% padded for Wear OS)
- **Incoming call overlay** — integrated with Android Telecom framework (ConnectionService) for system-level call UI that shows over charging screen and lock screen, with Answer, Decline, and Quick Reply
- **WhatsApp/FaceTime call detection** — detects VoIP calls via message content heuristics
- **Smart notification filtering** — skips pre-existing notifications on reconnect, 60-second grace period to avoid vibration storms
- **Heads-up overlays** — PRIORITY_HIGH + IMPORTANCE_HIGH channels for reliable notification popups
- **Auto-reconnection** — exponential backoff with `autoConnect` for bonded devices
- **Boot persistence** — service auto-starts on watch reboot
- **Haptic feedback** — category-aware vibration patterns via direct Vibrator API (50ms for messages, double-tap for calls)
- **No cloud dependency** — everything runs over local BLE
- **iOS companion app** — single-screen status UI with auto-detection of connected watches, "How it Works" explainer, and iOS 26 icon theming

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
│       ├── ancs/        # ANCS protocol, service, Telecom ConnectionService
│       ├── model/       # Data classes
│       ├── ui/          # Compose UI + Material Icons (main, call screen)
│       └── viewmodel/   # ViewModel
│
├── ios-app/             # iOS companion app (Swift, optional)
│   └── AncsBridge/
│       ├── BLE/         # Peripheral + Central manager, watch detection
│       ├── Views/       # Single-screen status UI, How it Works
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

## Roadmap

- [x] **Incoming Call Overlay** — Telecom ConnectionService integration for system-level call UI
- [x] **Quick Reply UI** — 3 preset messages on decline (UI ready, BLE transport pending)
- [x] **Vibration Tuning** — Category-aware haptics via direct Vibrator API (50ms messages, double-tap calls)
- [x] **Notification Icons** — 37 padded Material Design icons + Lucide call icons
- [x] **WhatsApp Call Detection** — Heuristic detection of VoIP calls from WhatsApp/FaceTime
- [ ] **Quick Reply BLE Transport** — Wire decline-with-message text to iOS companion app via custom BLE characteristic
- [ ] **App Icon Support** — Expand to full-color Play Store PNGs as `largeIcon`
- [ ] **Multi-Device Pairing** — Support switching between multiple Wear OS watches
- [ ] **Health Data Bridge** — Transfer Wear OS health/fitness data to iPhone via BLE + HealthKit

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests.

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
