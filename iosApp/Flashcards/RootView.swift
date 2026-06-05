import Shared
import SwiftUI

/// Whether to show the auth flow or the main app, derived from token presence.
enum SessionState {
    case loading
    case signedOut
    case signedIn
}

/// App root: gates on auth (mirrors Android's `MainActivity` launch gating). It observes login
/// state from the shared `TokenStore` (Keychain-backed) and shows the auth flow when signed out
/// or the main tab shell when signed in.
struct RootView: View {
    @EnvironmentObject private var container: AppContainer
    @State private var session: SessionState = .loading

    var body: some View {
        Group {
            switch session {
            case .loading:
                ProgressView()
            case .signedOut:
                AuthView(authService: container.authService)
            case .signedIn:
                MainTabView()
            }
        }
        .animation(.default, value: session)
        .task {
            // Flow → AsyncStream: token presence drives the gate (seeded from the Keychain, then
            // updated on sign-in / logout).
            for await loggedIn in asyncStream(BridgingKt.loggedInAdapter(container.tokenStore)) {
                session = loggedIn?.boolValue == true ? .signedIn : .signedOut
            }
        }
    }
}
