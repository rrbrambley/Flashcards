import Shared
import SwiftUI

/// Placeholder launch screen that also exercises the composition root + the Kotlin↔Swift bridging
/// the rest of the app builds on: it resolves the shared stack from the environment, calls a
/// suspend function (`suspend` → `async`), and observes a Kotlin Flow (`FlowAdapter` →
/// `AsyncStream`). Replaced by the auth-gated navigation shell (TabView) in FLA-37.
struct ContentView: View {
    @EnvironmentObject private var container: AppContainer

    @State private var greeting = Greeting().greet()
    @State private var sessionState = "checking…"
    @State private var authState = "…"

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "rectangle.on.rectangle.angled")
                .font(.system(size: 56))
                .foregroundStyle(.tint)
            Text("Flashcards")
                .font(.largeTitle.bold())
            Text(greeting)
                .font(.footnote)
                .foregroundStyle(.secondary)
            Text("Auth: \(authState) · token: \(sessionState)")
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
        .padding()
        .task {
            // suspend → async: reads the current access token off the shared TokenStore.
            let token = try? await container.tokenStore.currentToken()
            sessionState = token ?? "none"
        }
        .task {
            // Flow → AsyncStream: observe login state (the launch auth-gating primitive for FLA-37).
            for await loggedIn in asyncStream(BridgingKt.loggedInAdapter(container.tokenStore)) {
                authState = loggedIn?.boolValue == true ? "signed in" : "signed out"
            }
        }
    }
}
