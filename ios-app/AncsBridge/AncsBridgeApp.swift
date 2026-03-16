import SwiftUI

@main
struct AncsBridgeApp: App {
    @StateObject private var bleViewModel = BLEViewModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(bleViewModel)
        }
    }
}
