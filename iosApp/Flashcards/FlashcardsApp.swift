import GoogleSignIn
import SDWebImageSVGCoder
import SwiftUI

/// SwiftUI app entry point. Builds the composition root once, injects it into the environment, and
/// shows the auth-gated root (`RootView`).
@main
struct FlashcardsApp: App {
    @StateObject private var container = AppContainer()

    init() {
        // Enable SVG decoding for remote card images (the Country Flags deck).
        SDImageCodersManager.shared.addCoder(SDImageSVGCoder.shared)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(container)
                // Completes the Google Sign-In OAuth callback (no-op when Google isn't configured).
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
        }
    }
}
