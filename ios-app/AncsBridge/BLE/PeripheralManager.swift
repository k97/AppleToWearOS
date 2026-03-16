import CoreBluetooth
import Combine
import os.log

/// Manages the CBPeripheralManager for BLE advertising.
///
/// The iOS companion app advertises a custom service UUID so the Wear OS watch
/// can discover the iPhone during initial pairing. ANCS itself is handled by iOS
/// at the system level — no custom GATT server is needed for notification forwarding.
///
/// This class handles:
/// - BLE peripheral advertising for discoverability
/// - Background mode with state restoration
/// - Connection tracking for paired watches
class PeripheralManager: NSObject, ObservableObject {

    private let logger = Logger(subsystem: "com.wearos.ancsbridge", category: "PeripheralManager")

    private var peripheralManager: CBPeripheralManager!

    @Published var isAdvertising = false
    @Published var bluetoothState: CBManagerState = .unknown
    @Published var connectedDevices: [CBCentral] = []
    @Published var lastConnectedDate: Date?

    var onDeviceConnected: ((CBCentral) -> Void)?

    private var companionService: CBMutableService?
    private var statusCharacteristic: CBMutableCharacteristic?
    private var serviceAdded = false

    override init() {
        super.init()
        peripheralManager = CBPeripheralManager(
            delegate: self,
            queue: nil,
            options: [
                CBPeripheralManagerOptionRestoreIdentifierKey: BLEConstants.restoreIdentifier
            ]
        )
    }

    func startAdvertising() {
        guard peripheralManager.state == .poweredOn else {
            logger.warning("Cannot advertise: Bluetooth not powered on (state: \(self.peripheralManager.state.rawValue))")
            return
        }

        if peripheralManager.isAdvertising {
            logger.info("Already advertising")
            isAdvertising = true  // Sync our state with the actual state
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
        guard !serviceAdded else {
            logger.info("Services already set up, skipping")
            return
        }

        // Connection status characteristic (readable by watch)
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
        logger.info("Bluetooth state: \(peripheral.state.rawValue)")

        switch peripheral.state {
        case .poweredOn:
            setupServices()
            startAdvertising()
        case .poweredOff:
            isAdvertising = false
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
            logger.info("Advertising started successfully")
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        logger.info("Central subscribed: \(central.identifier)")
        trackConnectedCentral(central)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        logger.info("Central unsubscribed: \(central.identifier)")
        connectedDevices.removeAll { $0.identifier == central.identifier }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        logger.info("Read request from central: \(request.central.identifier)")
        trackConnectedCentral(request.central)

        if request.characteristic.uuid == BLEConstants.connectionStatusUUID {
            request.value = Data([0x01])
            peripheral.respond(to: request, withResult: .success)
        } else {
            peripheral.respond(to: request, withResult: .attributeNotFound)
        }
    }

    private func trackConnectedCentral(_ central: CBCentral) {
        if !connectedDevices.contains(where: { $0.identifier == central.identifier }) {
            connectedDevices.append(central)
        }
        lastConnectedDate = Date()
        onDeviceConnected?(central)

        // Stop advertising once we have a connection — saves battery
        if isAdvertising {
            logger.info("Device connected, stopping advertising")
            stopAdvertising()
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, willRestoreState dict: [String: Any]) {
        logger.info("Restoring peripheral manager state")
        if let services = dict[CBPeripheralManagerRestoredStateServicesKey] as? [CBMutableService] {
            for service in services {
                logger.info("Restored service: \(service.uuid)")
            }
            serviceAdded = !services.isEmpty
        }
        // Sync advertising state
        if peripheral.isAdvertising {
            isAdvertising = true
        }
    }
}
