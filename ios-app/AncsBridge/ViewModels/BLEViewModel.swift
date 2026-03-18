import SwiftUI
import CoreBluetooth
import Combine

class BLEViewModel: ObservableObject {
    let peripheralManager = PeripheralManager()

    private var cancellables = Set<AnyCancellable>()

    init() {
        // Observe bluetooth state changes to update UI
        peripheralManager.$bluetoothState
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)

        peripheralManager.$discoveredWatches
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)

        peripheralManager.$isAdvertising
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }

    func toggleAdvertising() {
        if peripheralManager.isAdvertising {
            peripheralManager.stopAdvertising()
        } else {
            peripheralManager.startAdvertising()
        }
    }
}
