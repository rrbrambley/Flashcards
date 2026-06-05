import Shared
import SwiftUI

/// The app's composition root: builds the shared stack once and hands the repositories to SwiftUI.
/// Injected via `.environmentObject` and resolved with `@EnvironmentObject` in any view.
///
/// `createIosFlashcardSdk` (shared iosMain) wires the Darwin HTTP client + transparent token
/// refresh + the offline-first Room repositories. The `TokenStore` is the placeholder
/// `InMemoryTokenStore` for now — FLA-36 swaps in a Keychain-backed implementation.
@MainActor
final class AppContainer: ObservableObject {
    let tokenStore: TokenStore
    let sdk: FlashcardSdk

    var flashcardRepository: FlashcardRepository { sdk.flashcardRepository }
    var practiceSessionRepository: PracticeSessionRepository { sdk.practiceSessionRepository }
    var homeRepository: HomeRepository { sdk.homeRepository }

    init(baseURL: String = AppConfig.backendBaseURL, tokenStore: TokenStore = InMemoryTokenStore()) {
        self.tokenStore = tokenStore
        // Kotlin default args don't bridge — pass the default home-feed strings explicitly.
        self.sdk = IosFlashcardSdkKt.createIosFlashcardSdk(
            baseUrl: baseURL,
            tokenStore: tokenStore,
            homeFeedStrings: DefaultHomeFeedStrings.shared
        )
    }
}
