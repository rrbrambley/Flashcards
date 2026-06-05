import SwiftUI

/// SwiftUI app entry point. The body is a placeholder shell for now; the navigation shell with
/// auth gating arrives in a later milestone (FLA-37), consuming the shared `createIosFlashcardSdk`.
@main
struct FlashcardsApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
