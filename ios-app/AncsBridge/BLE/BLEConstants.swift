import CoreBluetooth

/// BLE constants for the WearBridge companion app.
/// Note: ANCS itself is system-managed. These are for our custom companion service.
enum BLEConstants {
    /// Custom service UUID for the companion app (not ANCS — that's system-managed)
    static let companionServiceUUID = CBUUID(string: "A0E3D5B1-4C6F-4E8A-9B2D-7F1E3A5C8D0B")

    /// Characteristic: connection status (readable, notifiable)
    static let connectionStatusUUID = CBUUID(string: "A0E3D5B2-4C6F-4E8A-9B2D-7F1E3A5C8D0B")

    /// Characteristic: quick reply (writable by watch)
    static let quickReplyCharacteristicUUID = CBUUID(string: "A0E3D5B3-4C6F-4E8A-9B2D-7F1E3A5C8D0B")

    /// Characteristic: call state (readable, notifiable — pushes call state to watch)
    static let callStateCharacteristicUUID = CBUUID(string: "A0E3D5B4-4C6F-4E8A-9B2D-7F1E3A5C8D0B")

    /// Call state values sent over BLE
    static let callStateIdle: UInt8 = 0x00
    static let callStateRinging: UInt8 = 0x01
    static let callStateActive: UInt8 = 0x02
    static let callStateEnded: UInt8 = 0x03

    /// State restoration identifier
    static let restoreIdentifier = "com.wearos.ancsbridge.peripheral"

    /// Local name advertised to the watch
    static let advertisingLocalName = "WearBridge"
}
