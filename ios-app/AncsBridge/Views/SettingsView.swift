import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var viewModel: BLEViewModel

    var body: some View {
        NavigationStack {
            List {
                Section("Connection") {
                    Toggle("Auto-Advertise on Launch",
                           isOn: Binding(
                               get: { viewModel.peripheralManager.settingsStore.autoAdvertise },
                               set: { viewModel.peripheralManager.settingsStore.autoAdvertise = $0 }
                           ))

                    HStack {
                        Text("Bluetooth")
                        Spacer()
                        Text(bluetoothStatusText)
                            .foregroundColor(.secondary)
                    }
                }

                Section("About") {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                            .foregroundColor(.secondary)
                    }
                }

                Section(footer: Text("WearBridge uses Apple's Notification Center Service (ANCS) to forward notifications from your iPhone to your Pixel Watch over Bluetooth Low Energy. No data is sent to the cloud — everything stays between your devices.")) {
                    EmptyView()
                }
            }
            .navigationTitle("Settings")
        }
    }

    private var bluetoothStatusText: String {
        switch viewModel.peripheralManager.bluetoothState {
        case .poweredOn: return "On"
        case .poweredOff: return "Off"
        case .unauthorized: return "Unauthorized"
        case .unsupported: return "Unsupported"
        default: return "Unknown"
        }
    }
}
