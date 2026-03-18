import SwiftUI

struct StatusView: View {
    @EnvironmentObject var viewModel: BLEViewModel

    var body: some View {
        NavigationStack {
            List {
                // ANCS Status
                Section {
                    HStack(spacing: 14) {
                        Image(systemName: statusIcon)
                            .font(.title2)
                            .foregroundColor(statusColor)
                            .frame(width: 30)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(statusTitle)
                                .font(.headline)
                            Text(statusSubtitle)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                } header: {
                    Text("Notification Forwarding")
                }

                // Connected Watches
                Section {
                    if viewModel.peripheralManager.discoveredWatches.isEmpty {
                        HStack {
                            Spacer()
                            VStack(spacing: 8) {
                                if viewModel.peripheralManager.bluetoothState == .poweredOn {
                                    ProgressView()
                                        .padding(.bottom, 2)
                                    Text("Scanning for watches…")
                                        .font(.subheadline)
                                        .foregroundColor(.secondary)
                                } else {
                                    Image(systemName: "applewatch.slash")
                                        .font(.title2)
                                        .foregroundColor(.secondary)
                                    Text("No watches detected")
                                        .font(.subheadline)
                                        .foregroundColor(.secondary)
                                }
                            }
                            .padding(.vertical, 12)
                            Spacer()
                        }
                    } else {
                        ForEach(viewModel.peripheralManager.discoveredWatches) { watch in
                            Button {
                                showBluetoothSettings()
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "applewatch")
                                        .font(.title3)
                                        .foregroundColor(.green)
                                        .frame(width: 30)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(watch.name)
                                            .font(.body)
                                            .foregroundColor(.primary)
                                        Text("Connected via ANCS")
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }

                                    Spacer()

                                    Image(systemName: "chevron.right")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                            .padding(.vertical, 2)
                        }
                    }
                } header: {
                    Text("Watches")
                } footer: {
                    if !viewModel.peripheralManager.discoveredWatches.isEmpty {
                        Text("Watches connected via Bluetooth are shown automatically.")
                    }
                }

                // Connection Settings
                Section {
                    HStack {
                        Label("Bluetooth", systemImage: "antenna.radiowaves.left.and.right")
                        Spacer()
                        Text(bluetoothStatusText)
                            .foregroundColor(viewModel.peripheralManager.bluetoothState == .poweredOn ? .green : .red)
                    }

                    if viewModel.peripheralManager.bluetoothState == .poweredOn {
                        Toggle(isOn: Binding(
                            get: { viewModel.peripheralManager.isAdvertising },
                            set: { _ in viewModel.toggleAdvertising() }
                        )) {
                            Label("Discoverable", systemImage: "eye")
                        }
                    }
                } header: {
                    Text("Connection")
                } footer: {
                    Text("Enable Discoverable to let new watches find this iPhone during initial pairing.")
                }

                // About
                Section {
                    NavigationLink {
                        HowItWorksView()
                    } label: {
                        Label("How it Works", systemImage: "questionmark.circle")
                    }

                    HStack {
                        Text("Version")
                        Spacer()
                        Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                            .foregroundColor(.secondary)
                    }
                } footer: {
                    Text("WearBridge uses Apple's ANCS to forward notifications over Bluetooth. All data stays local.")
                }
            }
            .navigationTitle("WearBridge")
        }
    }

    private var statusIcon: String {
        switch viewModel.peripheralManager.bluetoothState {
        case .poweredOn: return "checkmark.circle.fill"
        case .poweredOff: return "xmark.circle.fill"
        case .unauthorized: return "lock.circle.fill"
        default: return "circle.dashed"
        }
    }

    private var statusColor: Color {
        switch viewModel.peripheralManager.bluetoothState {
        case .poweredOn: return .green
        case .poweredOff: return .red
        case .unauthorized: return .orange
        default: return .gray
        }
    }

    private var statusTitle: String {
        let watchCount = viewModel.peripheralManager.discoveredWatches.count
        switch viewModel.peripheralManager.bluetoothState {
        case .poweredOn:
            return watchCount > 0 ? "ANCS Active — \(watchCount) Watch\(watchCount > 1 ? "es" : "")" : "ANCS Active"
        case .poweredOff: return "Bluetooth Off"
        case .unauthorized: return "Bluetooth Denied"
        default: return "Unavailable"
        }
    }

    private var statusSubtitle: String {
        switch viewModel.peripheralManager.bluetoothState {
        case .poweredOn: return "Notifications are being forwarded to bonded watches."
        case .poweredOff: return "Turn on Bluetooth to forward notifications."
        case .unauthorized: return "Allow Bluetooth access in Settings."
        default: return "Bluetooth is not available."
        }
    }

    private func showBluetoothSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    private var bluetoothStatusText: String {
        switch viewModel.peripheralManager.bluetoothState {
        case .poweredOn: return "On"
        case .poweredOff: return "Off"
        case .unauthorized: return "Denied"
        case .unsupported: return "Unsupported"
        default: return "Unknown"
        }
    }
}
