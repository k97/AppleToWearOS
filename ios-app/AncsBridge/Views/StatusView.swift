import SwiftUI

struct StatusView: View {
    @EnvironmentObject var viewModel: BLEViewModel

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()

                // Connection status indicator
                statusIndicator

                // Status text
                Text(viewModel.connectionState.displayText)
                    .font(.title2)
                    .fontWeight(.semibold)

                // Subtitle
                subtitleText

                Spacer()

                // Advertise toggle button
                Button(action: {
                    viewModel.toggleAdvertising()
                }) {
                    HStack {
                        Image(systemName: viewModel.peripheralManager.isAdvertising
                              ? "stop.circle.fill" : "play.circle.fill")
                        Text(viewModel.peripheralManager.isAdvertising
                             ? "Stop Advertising" : "Start Advertising")
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(viewModel.peripheralManager.isAdvertising
                                ? Color.red.opacity(0.15) : Color.blue.opacity(0.15))
                    .foregroundColor(viewModel.peripheralManager.isAdvertising
                                    ? .red : .blue)
                    .cornerRadius(12)
                }
                .padding(.horizontal)

                // Last connected info
                if let lastDate = viewModel.peripheralManager.lastConnectedDate {
                    Text("Last connected: \(lastDate, style: .relative) ago")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()
            }
            .navigationTitle("ANCS Bridge")
        }
    }

    @ViewBuilder
    private var statusIndicator: some View {
        ZStack {
            Circle()
                .fill(statusColor.opacity(0.15))
                .frame(width: 100, height: 100)

            Circle()
                .fill(statusColor.opacity(0.3))
                .frame(width: 70, height: 70)

            Image(systemName: statusIcon)
                .font(.system(size: 30))
                .foregroundColor(statusColor)
        }
    }

    @ViewBuilder
    private var subtitleText: some View {
        switch viewModel.connectionState {
        case .advertising:
            Text("Your watch can now discover this iPhone.\nOpen the ANCS Bridge app on your watch to pair.")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        case .connected:
            Text("Notifications are being forwarded to your watch via ANCS.")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        case .bluetoothOff:
            Text("Enable Bluetooth in Settings to use ANCS Bridge.")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        default:
            EmptyView()
        }
    }

    private var statusColor: Color {
        switch viewModel.connectionState {
        case .connected: return .green
        case .advertising: return .blue
        case .bluetoothOff, .unauthorized: return .red
        case .idle: return .gray
        }
    }

    private var statusIcon: String {
        switch viewModel.connectionState {
        case .connected: return "checkmark.circle.fill"
        case .advertising: return "antenna.radiowaves.left.and.right"
        case .bluetoothOff: return "bluetooth.slash"
        case .unauthorized: return "lock.circle"
        case .idle: return "circle.dashed"
        }
    }
}
