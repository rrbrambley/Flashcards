import SwiftUI

/// SwiftUI app entry point. Builds the composition root once and injects it into the environment;
/// the auth-gated navigation shell (TabView) arrives in FLA-37.
@main
struct FlashcardsApp: App {
    @StateObject private var container = AppContainer()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(container)
        }
    }
}
