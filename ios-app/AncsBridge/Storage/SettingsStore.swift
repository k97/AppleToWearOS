import Foundation

/// UserDefaults-backed settings storage.
class SettingsStore: ObservableObject {
    private let defaults = UserDefaults.standard

    private enum Keys {
        static let autoAdvertise = "autoAdvertise"
        static let pairedDevices = "pairedDevices"
        static let liveCallStatus = "liveCallStatus"
    }

    @Published var autoAdvertise: Bool {
        didSet { defaults.set(autoAdvertise, forKey: Keys.autoAdvertise) }
    }

    /// When enabled, uses CallKit to monitor phone call state and pushes
    /// real-time updates to the watch over BLE. Off by default — requires
    /// the companion app to be running during calls.
    @Published var liveCallStatus: Bool {
        didSet { defaults.set(liveCallStatus, forKey: Keys.liveCallStatus) }
    }

    init() {
        self.autoAdvertise = defaults.bool(forKey: Keys.autoAdvertise)
        self.liveCallStatus = defaults.bool(forKey: Keys.liveCallStatus)
        // Default to false — discoverability only via Add New Watch flow
        if !defaults.contains(key: Keys.autoAdvertise) {
            self.autoAdvertise = false
        }
        // Default to false — opt-in for live call status
        if !defaults.contains(key: Keys.liveCallStatus) {
            self.liveCallStatus = false
        }
    }

    func savePairedDevice(_ device: PairedDevice) {
        var devices = loadPairedDevices()
        if let index = devices.firstIndex(where: { $0.id == device.id }) {
            devices[index] = device
        } else {
            devices.append(device)
        }
        if let data = try? JSONEncoder().encode(devices) {
            defaults.set(data, forKey: Keys.pairedDevices)
        }
    }

    func loadPairedDevices() -> [PairedDevice] {
        guard let data = defaults.data(forKey: Keys.pairedDevices),
              let devices = try? JSONDecoder().decode([PairedDevice].self, from: data) else {
            return []
        }
        return devices
    }

    func removePairedDevice(id: String) {
        var devices = loadPairedDevices()
        devices.removeAll { $0.id == id }
        if let data = try? JSONEncoder().encode(devices) {
            defaults.set(data, forKey: Keys.pairedDevices)
        }
    }
}

private extension UserDefaults {
    func contains(key: String) -> Bool {
        return object(forKey: key) != nil
    }
}
