import SwiftUI

struct HowItWorksView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {

                // Header
                VStack(spacing: 8) {
                    Image(systemName: "antenna.radiowaves.left.and.right.circle.fill")
                        .font(.system(size: 50))
                        .foregroundColor(.blue)
                    Text("How AppleToWearOS Works")
                        .font(.title2)
                        .fontWeight(.bold)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.bottom, 8)

                // Steps
                stepCard(
                    number: 1,
                    icon: "link",
                    title: "One-Time Pairing",
                    description: "Your iPhone and Wear OS watch pair over Bluetooth Low Energy (BLE). This creates a secure bond between the two devices. You only need to do this once."
                )

                stepCard(
                    number: 2,
                    icon: "bell.badge",
                    title: "ANCS Takes Over",
                    description: "iOS has a built-in service called ANCS (Apple Notification Center Service). Once bonded, your iPhone automatically shares all notifications with the watch over BLE — no cloud, no relay server."
                )

                stepCard(
                    number: 3,
                    icon: "arrow.right.circle",
                    title: "Watch Receives & Displays",
                    description: "The watch app listens for ANCS events, fetches notification details (title, message, app name), and posts them as native Android notifications with the correct app icon."
                )

                stepCard(
                    number: 4,
                    icon: "phone.circle",
                    title: "Incoming Calls",
                    description: "When a phone call arrives, the watch detects it instantly from the ANCS event and shows a full-screen call overlay with Answer, Decline, and Quick Reply options."
                )

                stepCard(
                    number: 5,
                    icon: "hand.tap",
                    title: "Actions Go Back",
                    description: "When you tap an action on the watch (answer a call, dismiss a notification), the command is sent back to your iPhone over BLE. The iPhone executes the action natively."
                )

                // Privacy section
                VStack(alignment: .leading, spacing: 8) {
                    Label("Privacy First", systemImage: "lock.shield.fill")
                        .font(.headline)
                        .foregroundColor(.green)

                    Text("Everything stays between your iPhone and watch over a direct Bluetooth connection. No data is sent to any server, cloud, or third party. The companion app is optional — ANCS works at the iOS system level once devices are bonded.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .padding()
                .background(Color.green.opacity(0.08))
                .cornerRadius(12)

                // Technical details
                VStack(alignment: .leading, spacing: 8) {
                    Label("Technical Details", systemImage: "wrench.and.screwdriver")
                        .font(.headline)
                        .foregroundColor(.orange)

                    VStack(alignment: .leading, spacing: 6) {
                        detailRow("Protocol", "Apple ANCS over BLE GATT")
                        detailRow("Connection", "BLE 5.0+ with bonding")
                        detailRow("MTU", "512 bytes (negotiated)")
                        detailRow("Reconnection", "Automatic with exponential backoff")
                        detailRow("Watch Role", "BLE Central / GATT Client")
                        detailRow("iPhone Role", "BLE Peripheral (system-managed)")
                    }
                }
                .padding()
                .background(Color.orange.opacity(0.08))
                .cornerRadius(12)
            }
            .padding()
        }
        .navigationTitle("How it Works")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func stepCard(number: Int, icon: String, title: String, description: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                Circle()
                    .fill(Color.blue.opacity(0.15))
                    .frame(width: 40, height: 40)
                Image(systemName: icon)
                    .font(.system(size: 18))
                    .foregroundColor(.blue)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Step \(number): \(title)")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                Text(description)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .font(.caption)
                .fontWeight(.medium)
        }
    }
}
