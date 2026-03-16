import Foundation

/// UserDefaults-backed settings storage.
class SettingsStore: ObservableObject {
    private let defaults = UserDefaults.standard

    private enum Keys {
        static let autoAdvertise = "autoAdvertise"
        static let pairedDevices = "pairedDevices"
    }

    @Published var autoAdvertise: Bool {
        didSet { defaults.set(autoAdvertise, forKey: Keys.autoAdvertise) }
    }

    init() {
        self.autoAdvertise = defaults.bool(forKey: Keys.autoAdvertise)
        // Default to true on first launch
        if !defaults.contains(key: Keys.autoAdvertise) {
            self.autoAdvertise = true
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
