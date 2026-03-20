import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var viewModel: BLEViewModel
    @State private var liveCallStatusEnabled: Bool = false

    var body: some View {
        NavigationStack {
            List {
                // Features
                Section {
                    NavigationLink {
                        QuickReplySettingsView()
                    } label: {
                        Label {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Quick Reply Messages")
                                Text("Decline calls with a preset message")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        } icon: {
                            Image(systemName: "text.bubble")
                                .foregroundColor(.blue)
                        }
                    }

                    Toggle(isOn: $liveCallStatusEnabled) {
                        Label {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Live Call Status")
                                Text("Push real-time call state to watch via BLE. Requires this app to be running.")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        } icon: {
                            Image(systemName: "phone.connection")
                                .foregroundColor(.green)
                        }
                    }
                    .onChange(of: liveCallStatusEnabled) { newValue in
                        viewModel.peripheralManager.settingsStore.liveCallStatus = newValue
                        viewModel.peripheralManager.updateCallStateObserving()
                    }
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
                } header: {
                    Text("About")
                } footer: {
                    Text("WearBridge uses Apple's ANCS to forward notifications over Bluetooth. All data stays local.")
                }
            }
            .navigationTitle("Settings")
            .onAppear {
                liveCallStatusEnabled = viewModel.peripheralManager.settingsStore.liveCallStatus
            }
        }
    }
}
