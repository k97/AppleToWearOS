# BLE & ANCS Protocol Details

## ANCS Characteristics

| Characteristic | UUID | Role |
|---|---|---|
| Notification Source | `9FBF120D-6301-42D9-8C58-25E699A21DBD` | Subscribe — 8-byte event notifications |
| Control Point | `69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9` | Write — request attributes / perform actions |
| Data Source | `22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB` | Subscribe — fragmented attribute responses |

**Subscribe to Data Source BEFORE Notification Source** (Apple requirement).

## BLE Connection Flow

1. Watch scans → finds iPhone (or uses bonded device)
2. `connectGatt()` → `onConnectionStateChange(CONNECTED)`
3. `requestMtu(512)` → `onMtuChanged` → `discoverServices()`
4. ANCS not visible (not bonded) → `createBond()` → iOS pairing dialog
5. `BOND_BONDED` → `discoverServices()` again → ANCS visible
6. Subscribe DS CCCD → Subscribe NS CCCD → session active (guard against double subscription)
7. NS events → fetch attributes via CP → parse DS → post Android notification

## Reconnection

- `autoConnect=true` in `connectGatt()` for bonded devices (chipset whitelist, battery efficient)
- Exponential backoff: 3s → 6s → 12s → 30s cap
- `START_STICKY` service + `BootReceiver` for persistence
- `getBondedIPhone()` matches device names containing "iPhone", "AncsBrid", "AncsBridge", "WearBrid", "WearBridge"
- GAP Device Name characteristic (0x2A00) read after ANCS connects for real iPhone name

## Silent/Pre-existing Notification Handling

- Pre-existing notifications (`EVENT_FLAG_PRE_EXISTING`) are skipped at NS event level — no GATT writes, no haptics
- 60-second reconnection grace period skips all non-call NS events to avoid vibration storms
- **Never use `setNotificationSilent()`** — it kills heads-up overlays on Wear OS

## Notification Channels

Channels are **versioned** (suffix `_v7` etc.) because Android permanently caches channel settings. Bump version in `AncsApplication.kt` to change settings.

Current channels: `service` (LOW), `incoming_call`, `messages`, `email`, `social`, `schedule`, `other` — all at `IMPORTANCE_HIGH` with system-default sound. Vibration handled via direct Vibrator API only.

## Telecom Framework (Incoming Calls)

1. `AncsConnectionService` registers a `PhoneAccount` with `CAPABILITY_SELF_MANAGED`
2. On cat=1 NS event, `TelecomManager.addNewIncomingCall()` is called immediately
3. System creates `AncsCallConnection` via `onCreateIncomingConnection()`
4. Caller name updated when attributes arrive (~1s later)
5. Answer/Decline sends ANCS action back to iPhone
6. Fallback: CallStyle notification + direct `startActivity()` if Telecom rejects

## Call State Dismissal (Three Layers)

When a call is answered on the iPhone, the watch overlay transitions from "Incoming Call" to "Active on iPhone" (auto-dismisses in 5s). Three detection layers:

1. **Companion BLE** (opt-in): `CXCallObserver` → call state characteristic `A0E3D5B4-...` → watch subscribes for notifications. Toggle: Settings → Live Call Status.
2. **ANCS MODIFIED**: NS event with EventID=1 for active call UID triggers overlay transition.
3. **Safety timeout**: 45-second auto-dismiss if no events arrive.

## Companion Service Characteristics

| Characteristic | UUID | Role |
|---|---|---|
| Connection Status | `A0E3D5B2-4C6F-4E8A-9B2D-7F1E3A5C8D0B` | Read/Notify — watch reads to signal connection |
| Quick Reply | `A0E3D5B3-4C6F-4E8A-9B2D-7F1E3A5C8D0B` | Write — watch sends `"callerName\|replyText"` |
| Call State | `A0E3D5B4-4C6F-4E8A-9B2D-7F1E3A5C8D0B` | Read/Notify — iPhone pushes call state (0x00=idle, 0x01=ringing, 0x02=active, 0x03=ended) |

## Quick Reply Flow

1. Watch: user taps quick reply → ANCS negative action + BLE write to quick reply characteristic
2. iPhone: `PeripheralManager` receives write → opens `shortcuts://run-shortcut?name=WearBridge%20Reply`
3. User creates iOS Shortcut "WearBridge Reply" to send SMS/iMessage with the input text

## iPhone Companion App Watch Detection

The iPhone can't detect ANCS connections (system-level). Instead, `CBCentralManager.retrieveConnectedPeripherals(withServices:)` probes for devices with standard BLE services (Device Info 0x180A, Battery 0x180F, etc.) and filters by known watch name patterns. Polls every 10 seconds.

## App Icon Mapping

75 iOS bundle IDs mapped in `AppIconMapper.kt`. 37 notification icons with 25% padding (viewport 32, translate 4). Call screen uses 3 Lucide stroke icons. Watch UI uses Material Icons Extended.
