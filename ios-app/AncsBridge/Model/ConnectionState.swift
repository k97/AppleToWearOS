import Foundation

enum AppConnectionState: Equatable {
    case idle
    case advertising
    case connected(deviceCount: Int)
    case ancsActive          // Bonded watch exists, ANCS forwarding at system level
    case bluetoothOff
    case unauthorized

    var displayText: String {
        switch self {
        case .idle:
            return "Not Advertising"
        case .advertising:
            return "Waiting for Watch..."
        case .connected(let count):
            return "Connected (\(count) device\(count == 1 ? "" : "s"))"
        case .ancsActive:
            return "ANCS Active"
        case .bluetoothOff:
            return "Bluetooth Off"
        case .unauthorized:
            return "Bluetooth Unauthorized"
        }
    }

    var isActive: Bool {
        switch self {
        case .advertising, .connected, .ancsActive:
            return true
        default:
            return false
        }
    }
}
