import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var viewModel: BLEViewModel

    var body: some View {
        NavigationStack {
            List {
                Section("Connection") {
                    Toggle("Auto-Advertise on Launch",
                           isOn: Binding(
                               get: { viewModel.settingsStore.autoAdvertise },
                               set: { viewModel.settingsStore.autoAdvertise = $0 }
                           ))

                    HStack {
                        Text("Bluetooth")
                        Spacer()
                        Text(bluetoothStatusText)
                            .foregroundColor(.secondary)
                    }
                }

                Section("Paired Devices") {
                    let devices = viewModel.settingsStore.loadPairedDevices()
                    if devices.isEmpty {
                        Text("No paired devices")
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(devices) { device in
                            VStack(alignment: .leading) {
                                Text(device.name ?? "Unknown Watch")
                                    .font(.body)
                                Text("Paired: \(device.firstPairedDate, style: .date)")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .onDelete { indexSet in
                            let devices = viewModel.settingsStore.loadPairedDevices()
                            for index in indexSet {
                                viewModel.settingsStore.removePairedDevice(id: devices[index].id)
                            }
                        }
                    }
                }

                Section("About") {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                            .foregroundColor(.secondary)
                    }

                    HStack {
                        Text("How it works")
                        Spacer()
                    }
                }

                Section(footer: Text("ANCS Bridge uses Apple's Notification Center Service (ANCS) to forward notifications from your iPhone to your Pixel Watch over Bluetooth Low Energy. No data is sent to the cloud — everything stays between your devices.")) {
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
