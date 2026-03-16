import SwiftUI
import CoreBluetooth
import Combine

class BLEViewModel: ObservableObject {
    let peripheralManager = PeripheralManager()
    let settingsStore = SettingsStore()

    @Published var connectionState: AppConnectionState = .idle

    private var cancellables = Set<AnyCancellable>()

    init() {
        // Observe peripheral manager state changes
        peripheralManager.$bluetoothState
            .combineLatest(peripheralManager.$isAdvertising, peripheralManager.$connectedDevices)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] bluetoothState, isAdvertising, connectedDevices in
                self?.updateConnectionState(
                    bluetoothState: bluetoothState,
                    isAdvertising: isAdvertising,
                    connectedCount: connectedDevices.count
                )
            }
            .store(in: &cancellables)

        // Save paired devices when a central connects
        peripheralManager.onDeviceConnected = { [weak self] central in
            guard let self else { return }
            let device = PairedDevice(
                id: central.identifier.uuidString,
                name: "Pixel Watch",
                firstPairedDate: Date(),
                lastSeenDate: Date()
            )
            self.settingsStore.savePairedDevice(device)
        }
    }

    private func updateConnectionState(
        bluetoothState: CBManagerState,
        isAdvertising: Bool,
        connectedCount: Int
    ) {
        switch bluetoothState {
        case .poweredOff:
            connectionState = .bluetoothOff
        case .unauthorized:
            connectionState = .unauthorized
        case .poweredOn:
            if connectedCount > 0 {
                connectionState = .connected(deviceCount: connectedCount)
            } else if isAdvertising {
                connectionState = .advertising
            } else {
                connectionState = .idle
            }
        default:
            connectionState = .idle
        }
    }

    func toggleAdvertising() {
        if peripheralManager.isAdvertising {
            peripheralManager.stopAdvertising()
        } else {
            peripheralManager.startAdvertising()
        }
    }
}
