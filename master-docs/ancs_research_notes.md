# ANCS Research Notes

## Key Findings

### ANCS is System-Level
- ANCS (Apple Notification Center Service) is built into iOS at the OS level
- No iOS app is required for basic notification forwarding
- The iPhone exposes ANCS automatically to any bonded BLE accessory
- All characteristics require authorization (pairing/bonding)

### Proven Implementations
- **Merge App** (commercial) — connects Wear OS to iPhone via BLE/ANCS
- **Aerlink for Android** (open-source, Kotlin) — GitHub: GuiyeC/Aerlink-for-Android
- **iOS-Wear-Connect** (open-source, Java fork of Aerlink)

### ANCS Protocol Reference

**Service UUID**: `7905F431-B5CE-4E99-A40F-4B1E122D00D0`

| Characteristic | UUID | Properties |
|---|---|---|
| Notification Source | `9FBF120D-6301-42D9-8C58-25E699A21DBD` | Notifiable |
| Control Point | `69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9` | Writable with response |
| Data Source | `22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB` | Notifiable |

**Notification Source payload (8 bytes)**:
- Byte 0: EventID (0=Added, 1=Modified, 2=Removed)
- Byte 1: EventFlags (bitmask: Silent=0x01, Important=0x02, PreExisting=0x04, PositiveAction=0x08, NegativeAction=0x10)
- Byte 2: CategoryID (0-11)
- Byte 3: CategoryCount
- Bytes 4-7: NotificationUID (uint32 LE)

**Category IDs**: Other(0), IncomingCall(1), MissedCall(2), Voicemail(3), Social(4), Schedule(5), Email(6), News(7), HealthAndFitness(8), BusinessAndFinance(9), Location(10), Entertainment(11)

**Notification Attribute IDs**: AppIdentifier(0), Title(1), Subtitle(2), Message(3), MessageSize(4), Date(5), PositiveActionLabel(6), NegativeActionLabel(7)

**Important**: Variable-length attributes (Title, Subtitle, Message, action labels) require a 2-byte LE max-length parameter in the Control Point request. Fixed attributes (AppIdentifier, Date, MessageSize) do NOT.

**Subscribe to Data Source BEFORE Notification Source** (Apple recommendation).

### BLE Connection Flow
1. Watch scans for iPhone
2. connectGatt() with TRANSPORT_LE
3. discoverServices() — ANCS not visible (not bonded yet)
4. createBond() — iOS pairing dialog
5. BOND_BONDED — discoverServices() again — ANCS visible
6. Subscribe DS CCCD → Subscribe NS CCCD → session active

### Known Limitations
- ANCS does not provide app icons (must be bundled on watch)
- No audio forwarding (calls can be answered/declined but audio stays on iPhone)
- Data Source responses can be fragmented across multiple GATT notifications
- NotificationUIDs are session-scoped (invalid after disconnect)
- Cannot hook into native Wear OS phone app (must build custom call UI)

### iOS 26.3+ Changes
- Apple added explicit "Notification Forwarding" option for third-party wearables
- EU regulations pushing Apple to open notification access

## Sources
- [Apple ANCS Specification](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Specification/Specification.html)
- [Aerlink for Android](https://github.com/GuiyeC/Aerlink-for-Android)
- [Merge App](https://www.merge.watch/)
- [Android BLE Guide](https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server)
- [XDA Forums - Merge Thread](https://xdaforums.com/t/app-wear-os-merge-connecting-wear-os-to-ios.4616337/)
