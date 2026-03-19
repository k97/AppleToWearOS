import SwiftUI

struct AddWatchView: View {
    @ObservedObject var viewModel: BLEViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 28) {

                    // Watch illustration
                    Image(systemName: "applewatch.and.arrow.forward")
                        .font(.system(size: 64))
                        .foregroundColor(.blue)
                        .padding(.top, 20)

                    // Header
                    VStack(spacing: 8) {
                        Text("Connect Your Watch")
                            .font(.title)
                            .fontWeight(.bold)

                        Text("Follow these steps to pair your Wear OS watch with this iPhone.")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                    }

                    // Discoverability status
                    HStack(spacing: 10) {
                        Circle()
                            .fill(viewModel.peripheralManager.isAdvertising ? Color.green : Color.gray)
                            .frame(width: 10, height: 10)
                        Text(viewModel.peripheralManager.isAdvertising ? "iPhone is discoverable" : "Making iPhone discoverable…")
                            .font(.subheadline)
                            .foregroundColor(viewModel.peripheralManager.isAdvertising ? .green : .secondary)
                    }
                    .padding(.vertical, 8)
                    .padding(.horizontal, 20)
                    .background(Color(.secondarySystemGroupedBackground))
                    .cornerRadius(20)

                    // Steps
                    VStack(spacing: 0) {
                        stepCard(
                            number: 1,
                            icon: "arrow.down.app",
                            title: "Install WearBridge on your watch",
                            description: "Download the APK from GitHub Releases and install via ADB, or search for WearBridge on Google Play."
                        ) {
                            if let url = URL(string: "https://github.com/k97/AppleToWearOS/releases") {
                                UIApplication.shared.open(url)
                            }
                        }

                        Divider().padding(.leading, 56)

                        stepCard(
                            number: 2,
                            icon: "magnifyingglass",
                            title: "Open WearBridge on the watch",
                            description: "Launch the app and tap Scan. Your iPhone will appear in the device list."
                        )

                        Divider().padding(.leading, 56)

                        stepCard(
                            number: 3,
                            icon: "checkmark.shield",
                            title: "Accept the pairing dialog",
                            description: "A Bluetooth pairing request will appear on your iPhone. Tap Pair to connect."
                        )
                    }
                    .background(Color(.secondarySystemGroupedBackground))
                    .cornerRadius(14)
                    .padding(.horizontal)

                    // Bluetooth Settings button
                    Button {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        HStack {
                            Spacer()
                            Label("Go to Bluetooth Settings", systemImage: "gear")
                                .font(.body)
                                .fontWeight(.medium)
                            Spacer()
                        }
                        .padding(.vertical, 14)
                        .background(Color(.secondarySystemGroupedBackground))
                        .foregroundColor(.blue)
                        .cornerRadius(12)
                    }
                    .padding(.horizontal)

                    Spacer(minLength: 20)
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("Back")
                        }
                    }
                }
            }
            .onAppear {
                if !viewModel.peripheralManager.isAdvertising {
                    viewModel.toggleAdvertising()
                }
            }
            .onDisappear {
                if viewModel.peripheralManager.isAdvertising {
                    viewModel.toggleAdvertising()
                }
            }
        }
    }

    private func stepCard(number: Int, icon: String, title: String, description: String, action: (() -> Void)? = nil) -> some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color.blue.opacity(0.12))
                    .frame(width: 36, height: 36)
                Image(systemName: icon)
                    .font(.body)
                    .foregroundColor(.blue)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.body)
                    .fontWeight(.medium)
                    .foregroundColor(.primary)
                Text(description)
                    .font(.caption)
                    .foregroundColor(.secondary)

                if let action = action {
                    Button {
                        action()
                    } label: {
                        Text("Download from GitHub")
                            .font(.caption)
                            .fontWeight(.medium)
                            .foregroundColor(.blue)
                    }
                    .padding(.top, 2)
                }
            }

            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}
