import CoreBluetooth
import Combine
import os.log

/// Manages BLE for the AppleToWearOS companion app.
///
/// Acts as both:
/// - CBPeripheralManager: advertises for initial watch pairing
/// - CBCentralManager: detects connected watches via system BLE connections
///
/// ANCS itself is system-managed — this class provides discoverability and
/// connection status tracking.
class PeripheralManager: NSObject, ObservableObject {

    private let logger = Logger(subsystem: "com.wearos.ancsbridge", category: "PeripheralManager")

    private var peripheralManager: CBPeripheralManager!
    private var centralManager: CBCentralManager!

    @Published var isAdvertising = false
    @Published var bluetoothState: CBManagerState = .unknown
    @Published var discoveredWatches: [DiscoveredWatch] = []

    let settingsStore = SettingsStore()

    private var companionService: CBMutableService?
    private var statusCharacteristic: CBMutableCharacteristic?
    private var serviceAdded = false
    private var scanTimer: Timer?

    /// Standard BLE service UUIDs to probe for connected devices.
    /// Wear OS watches expose these standard GATT services.
    private static let probeServiceUUIDs: [CBUUID] = [
        CBUUID(string: "180A"),  // Device Information
        CBUUID(string: "180F"),  // Battery Service
        CBUUID(string: "1800"),  // Generic Access
        CBUUID(string: "1805"),  // Current Time Service
    ]

    /// Known Wear OS watch name patterns
    private static let watchNamePatterns = [
        "pixel watch", "galaxy watch", "ticwatch", "fossil",
        "oppo watch", "oneplus watch", "mobvoi", "wear os",
        "ancs"  // Our own app's advertised name during pairing
    ]

    override init() {
        super.init()
        peripheralManager = CBPeripheralManager(
            delegate: self,
            queue: nil,
            options: [
                CBPeripheralManagerOptionRestoreIdentifierKey: BLEConstants.restoreIdentifier
            ]
        )
        centralManager = CBCentralManager(
            delegate: self,
            queue: nil,
            options: [
                CBCentralManagerOptionShowPowerAlertKey: false
            ]
        )
    }

    // MARK: - Connected Watch Discovery

    /// Query the system for BLE peripherals currently connected that look like watches.
    func refreshConnectedWatches() {
        guard centralManager.state == .poweredOn else { return }

        var found: [UUID: CBPeripheral] = [:]

        // Retrieve peripherals connected to the system with known services
        for serviceUUID in Self.probeServiceUUIDs {
            let peripherals = centralManager.retrieveConnectedPeripherals(withServices: [serviceUUID])
            for peripheral in peripherals {
                found[peripheral.identifier] = peripheral
            }
        }

        // Filter to likely watches by name
        let watches = found.values.compactMap { peripheral -> DiscoveredWatch? in
            guard let name = peripheral.name, !name.isEmpty else { return nil }
            let lower = name.lowercased()

            // Check if name matches known watch patterns
            let isWatch = Self.watchNamePatterns.contains { lower.contains($0) }

            // Also include any device we've seen before (saved identifiers)
            let savedIds = UserDefaults.standard.stringArray(forKey: "knownWatchIds") ?? []
            let wasPreviouslySeen = savedIds.contains(peripheral.identifier.uuidString)

            guard isWatch || wasPreviouslySeen else { return nil }

            // Save this identifier for future recognition
            var ids = Set(savedIds)
            ids.insert(peripheral.identifier.uuidString)
            UserDefaults.standard.set(Array(ids), forKey: "knownWatchIds")

            return DiscoveredWatch(
                id: peripheral.identifier,
                name: name,
                peripheral: peripheral
            )
        }

        DispatchQueue.main.async {
            self.discoveredWatches = watches
            self.logger.info("Found \(watches.count) connected watch(es): \(watches.map { $0.name }.joined(separator: ", "))")
        }
    }

    /// Start periodic scanning for connected watches
    func startWatchRefresh() {
        refreshConnectedWatches()
        scanTimer?.invalidate()
        scanTimer = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in
            self?.refreshConnectedWatches()
        }
    }

    func stopWatchRefresh() {
        scanTimer?.invalidate()
        scanTimer = nil
    }

    // MARK: - Advertising

    func startAdvertising() {
        guard peripheralManager.state == .poweredOn else {
            logger.warning("Cannot advertise: Bluetooth not powered on")
            return
        }

        if peripheralManager.isAdvertising {
            isAdvertising = true
            return
        }

        peripheralManager.startAdvertising([
            CBAdvertisementDataLocalNameKey: BLEConstants.advertisingLocalName,
            CBAdvertisementDataServiceUUIDsKey: [BLEConstants.companionServiceUUID]
        ])

        logger.info("Started advertising as '\(BLEConstants.advertisingLocalName)'")
    }

    func stopAdvertising() {
        peripheralManager.stopAdvertising()
        isAdvertising = false
        logger.info("Stopped advertising")
    }

    private func setupServices() {
        guard !serviceAdded else { return }

        statusCharacteristic = CBMutableCharacteristic(
            type: BLEConstants.connectionStatusUUID,
            properties: [.read, .notify],
            value: nil,
            permissions: [.readable]
        )

        companionService = CBMutableService(
            type: BLEConstants.companionServiceUUID,
            primary: true
        )
        companionService?.characteristics = [statusCharacteristic!]

        peripheralManager.add(companionService!)
        serviceAdded = true
        logger.info("Companion service added")
    }
}

// MARK: - CBPeripheralManagerDelegate
extension PeripheralManager: CBPeripheralManagerDelegate {

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        bluetoothState = peripheral.state
        logger.info("Peripheral Bluetooth state: \(peripheral.state.rawValue)")

        switch peripheral.state {
        case .poweredOn:
            setupServices()
            if settingsStore.autoAdvertise {
                startAdvertising()
            }
        case .poweredOff:
            isAdvertising = false
            discoveredWatches = []
        default:
            break
        }
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            logger.error("Failed to start advertising: \(error.localizedDescription)")
            isAdvertising = false
        } else {
            isAdvertising = true
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        logger.info("Central subscribed: \(central.identifier)")
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        logger.info("Central unsubscribed: \(central.identifier)")
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        if request.characteristic.uuid == BLEConstants.connectionStatusUUID {
            request.value = Data([0x01])
            peripheral.respond(to: request, withResult: .success)
        } else {
            peripheral.respond(to: request, withResult: .attributeNotFound)
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, willRestoreState dict: [String: Any]) {
        logger.info("Restoring peripheral manager state")
        if let services = dict[CBPeripheralManagerRestoredStateServicesKey] as? [CBMutableService] {
            serviceAdded = !services.isEmpty
        }
        if peripheral.isAdvertising {
            isAdvertising = true
        }
    }
}

// MARK: - CBCentralManagerDelegate
extension PeripheralManager: CBCentralManagerDelegate {

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        logger.info("Central Bluetooth state: \(central.state.rawValue)")

        if central.state == .poweredOn {
            startWatchRefresh()
        } else {
            stopWatchRefresh()
            DispatchQueue.main.async {
                self.discoveredWatches = []
            }
        }
    }
}

// MARK: - DiscoveredWatch Model
struct DiscoveredWatch: Identifiable {
    let id: UUID
    let name: String
    let peripheral: CBPeripheral
}
