import SwiftUI

struct StatusView: View {
    @EnvironmentObject var viewModel: BLEViewModel
    @State private var showFeatureSheet = false
    @State private var showAddWatch = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {

                    // Status Banner
                    statusBanner
                        .padding(.horizontal)
                        .padding(.top, 8)

                    // Features Card
                    Button { showFeatureSheet = true } label: {
                        VStack(alignment: .leading, spacing: 14) {
                            Text("What WearBridge Does")
                                .font(.headline)
                                .foregroundColor(.primary)

                            LazyVGrid(columns: [
                                GridItem(.flexible()),
                                GridItem(.flexible()),
                                GridItem(.flexible())
                            ], spacing: 14) {
                                featureChip(icon: "phone.connection.fill", color: .green, label: "Calls")
                                featureChip(icon: "app.badge.fill", color: .blue, label: "Notifications")
                                featureChip(icon: "bubble.left.and.text.bubble.right.fill", color: .purple, label: "Quick Reply")
                                featureChip(icon: "bolt.horizontal.fill", color: .orange, label: "Auto Reconnect")
                                featureChip(icon: "hand.raised.circle.fill", color: .mint, label: "Privacy First")
                                featureChip(icon: "water.waves", color: .pink, label: "Haptics")
                            }

                            HStack {
                                Text("Tap to see supported devices")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(16)
                        .background(Color(.secondarySystemGroupedBackground))
                        .cornerRadius(14)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal)

                    // Your Watches
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Your Watches")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(.secondary)
                            .textCase(.uppercase)
                            .padding(.horizontal)

                        VStack(spacing: 10) {
                            if viewModel.peripheralManager.discoveredWatches.isEmpty {
                                HStack(spacing: 12) {
                                    if viewModel.peripheralManager.bluetoothState == .poweredOn {
                                        ProgressView()
                                            .frame(width: 40, height: 40)
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text("Scanning for watches")
                                                .font(.body)
                                            Text("Bonded watches will appear here automatically")
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                    } else {
                                        Image(systemName: "applewatch.slash")
                                            .font(.title3)
                                            .foregroundColor(.secondary)
                                            .frame(width: 40)
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text("No watches detected")
                                                .font(.body)
                                            Text("Turn on Bluetooth to connect")
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                    }
                                    Spacer()
                                }
                                .padding(16)
                                .background(Color(.secondarySystemGroupedBackground))
                                .cornerRadius(12)
                            } else {
                                ForEach(Array(viewModel.peripheralManager.discoveredWatches.enumerated()), id: \.element.id) { index, watch in
                                    let isActive = watch.isConnected
                                    Button {
                                        showBluetoothSettings()
                                    } label: {
                                        HStack(spacing: 14) {
                                            // Watch thumbnail
                                            watchThumbnail(name: watch.name, isActive: isActive)

                                            VStack(alignment: .leading, spacing: 3) {
                                                Text(watch.name)
                                                    .font(.body)
                                                    .fontWeight(.medium)
                                                    .foregroundColor(.primary)
                                                Text(isActive ? "Active — receiving notifications" : "Paired — not connected")
                                                    .font(.caption)
                                                    .foregroundColor(isActive ? .green : .secondary)
                                            }

                                            Spacer()

                                            if isActive {
                                                Circle()
                                                    .fill(Color.green)
                                                    .frame(width: 8, height: 8)
                                            }

                                            Image(systemName: "chevron.right")
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                        .padding(14)
                                        .background(isActive ? Color.green.opacity(0.06) : Color(.secondarySystemGroupedBackground))
                                        .cornerRadius(12)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(isActive ? Color.green.opacity(0.2) : Color.clear, lineWidth: 1)
                                        )
                                    }
                                }
                            }
                        }
                        .padding(.horizontal)

                        // Add New Watch button
                        Button { showAddWatch = true } label: {
                            HStack {
                                Spacer()
                                Label("Add New Watch", systemImage: "plus")
                                    .font(.body)
                                    .fontWeight(.medium)
                                Spacer()
                            }
                            .padding(.vertical, 14)
                            .background(Color.blue)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                        }
                        .padding(.horizontal)
                    }

                    Spacer(minLength: 20)
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("WearBridge")
            .sheet(isPresented: $showFeatureSheet) {
                FeatureDetailSheet()
            }
            .fullScreenCover(isPresented: $showAddWatch) {
                AddWatchView(viewModel: viewModel)
            }
        }
    }

    // MARK: - Status Banner

    private var statusBanner: some View {
        HStack(spacing: 14) {
            Circle()
                .fill(statusColor.opacity(0.15))
                .frame(width: 48, height: 48)
                .overlay(
                    Image(systemName: statusIcon)
                        .font(.title3)
                        .foregroundColor(statusColor)
                )

            VStack(alignment: .leading, spacing: 3) {
                Text(statusTitle)
                    .font(.headline)
                Text(statusSubtitle)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }

            Spacer()
        }
        .padding(16)
        .background(statusColor.opacity(0.08))
        .cornerRadius(14)
    }

    // MARK: - Watch Thumbnail

    private func watchThumbnail(name: String, isActive: Bool) -> some View {
        let isPixel = name.localizedCaseInsensitiveContains("pixel")
        let isSamsung = name.localizedCaseInsensitiveContains("galaxy") || name.localizedCaseInsensitiveContains("samsung")
        let brandColor: Color = isPixel ? .blue : isSamsung ? .purple : .gray
        let brandLetter = isPixel ? "G" : isSamsung ? "S" : "W"

        return ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(isActive ? brandColor.opacity(0.15) : Color(.tertiarySystemGroupedBackground))
                .frame(width: 48, height: 48)

            Image(systemName: "applewatch")
                .font(.system(size: 22))
                .foregroundColor(isActive ? brandColor : .secondary)

            // Brand badge
            Text(brandLetter)
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 16, height: 16)
                .background(brandColor)
                .clipShape(Circle())
                .offset(x: 14, y: -14)
        }
        .frame(width: 48, height: 48)
    }

    // MARK: - Feature Chip

    private func featureChip(icon: String, color: Color, label: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.body)
                .foregroundColor(color)
                .frame(width: 36, height: 36)
                .background(color.opacity(0.12))
                .cornerRadius(10)
            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)
                .lineLimit(1)
        }
    }

    // MARK: - Helpers

    private var statusIcon: String {
        switch viewModel.peripheralManager.bluetoothState {
        case .poweredOn:
            return viewModel.peripheralManager.discoveredWatches.isEmpty ? "antenna.radiowaves.left.and.right" : "checkmark.circle.fill"
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
        let count = viewModel.peripheralManager.discoveredWatches.count
        switch viewModel.peripheralManager.bluetoothState {
        case .poweredOn:
            return count > 0 ? "Connected" : "ANCS Active"
        case .poweredOff: return "Bluetooth Off"
        case .unauthorized: return "Bluetooth Denied"
        default: return "Unavailable"
        }
    }

    private var statusSubtitle: String {
        let count = viewModel.peripheralManager.discoveredWatches.count
        switch viewModel.peripheralManager.bluetoothState {
        case .poweredOn:
            return count > 0
                ? "Notifications flowing to \(count) watch\(count > 1 ? "es" : "")"
                : "Waiting for a watch to connect"
        case .poweredOff: return "Turn on Bluetooth to forward notifications"
        case .unauthorized: return "Allow Bluetooth access in Settings"
        default: return "Bluetooth is not available"
        }
    }

    private func showBluetoothSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}

// MARK: - Feature Detail Sheet

struct FeatureDetailSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {

                    // Features
                    VStack(alignment: .leading, spacing: 16) {
                        featureDetail(icon: "phone.arrow.down.left", color: .green, title: "Phone Calls",
                                      description: "Full-screen incoming call overlay with answer, decline, and quick reply. Works with regular calls, WhatsApp, and FaceTime.")
                        featureDetail(icon: "bell.badge", color: .blue, title: "Notifications",
                                      description: "Real-time notification mirroring with 75+ app icon mappings. Messages, emails, calendar alerts, and more.")
                        featureDetail(icon: "text.bubble", color: .purple, title: "Quick Reply",
                                      description: "Decline calls with a preset message sent via Siri Shortcuts. Customizable in Settings.")
                        featureDetail(icon: "arrow.triangle.2.circlepath", color: .orange, title: "Auto Reconnect",
                                      description: "Stays connected with exponential backoff. Auto-starts on watch reboot.")
                        featureDetail(icon: "lock.shield", color: .mint, title: "Fully Local",
                                      description: "No cloud, no server, no account. Direct Bluetooth Low Energy only. Your data never leaves your devices.")
                        featureDetail(icon: "waveform", color: .pink, title: "Haptic Feedback",
                                      description: "Category-aware vibration patterns. Gentle tap for messages, double-tap for calls.")
                    }
                    .padding(.horizontal)

                    // Supported Devices
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Supported Devices")
                            .font(.headline)
                            .padding(.horizontal)

                        VStack(spacing: 0) {
                            deviceRow("Google Pixel Watch 2", verified: true)
                            Divider().padding(.leading, 16)
                            deviceRow("Google Pixel Watch 3", verified: false)
                            Divider().padding(.leading, 16)
                            deviceRow("Samsung Galaxy Watch 4 / 4 Classic", verified: false)
                            Divider().padding(.leading, 16)
                            deviceRow("Samsung Galaxy Watch 5 / 5 Pro", verified: true)
                            Divider().padding(.leading, 16)
                            deviceRow("Samsung Galaxy Watch 6 / 6 Classic", verified: false)
                            Divider().padding(.leading, 16)
                            deviceRow("Samsung Galaxy Watch 7 / Ultra", verified: false)
                            Divider().padding(.leading, 16)
                            deviceRow("OnePlus Watch 2", verified: false)
                            Divider().padding(.leading, 16)
                            deviceRow("TicWatch Pro 3 / 5", verified: false)
                            Divider().padding(.leading, 16)
                            deviceRow("Fossil Gen 6", verified: false)
                            Divider().padding(.leading, 16)
                            deviceRow("Montblanc Summit 3", verified: false)
                        }
                        .background(Color(.secondarySystemGroupedBackground))
                        .cornerRadius(12)
                        .padding(.horizontal)

                        Text("Any Wear OS 3+ watch should work. More devices verified based on community testing and feedback.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 20)
                    }

                    Spacer(minLength: 20)
                }
                .padding(.top, 8)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("WearBridge")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func featureDetail(icon: String, color: Color, title: String, description: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: icon)
                .font(.body)
                .foregroundColor(color)
                .frame(width: 36, height: 36)
                .background(color.opacity(0.12))
                .cornerRadius(10)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.body)
                    .fontWeight(.medium)
                Text(description)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
    }

    private func deviceRow(_ name: String, verified: Bool) -> some View {
        HStack {
            Text(name)
                .font(.body)
            Spacer()
            if verified {
                Text("Verified")
                    .font(.caption)
                    .foregroundColor(.green)
            } else {
                Text("Compatible")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}
