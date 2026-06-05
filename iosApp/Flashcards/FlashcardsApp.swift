import SwiftUI

/// SwiftUI app entry point. Builds the composition root once, injects it into the environment, and
/// shows the auth-gated root (`RootView`).
@main
struct FlashcardsApp: App {
    @StateObject private var container = AppContainer()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(container)
        }
    }
}
