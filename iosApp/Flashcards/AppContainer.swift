import Shared
import SwiftUI

/// The app's composition root: builds the shared stack once and hands the repositories to SwiftUI.
/// Injected via `.environmentObject` and resolved with `@EnvironmentObject` in any view.
///
/// `createIosFlashcardSdk` (shared iosMain) wires the Darwin HTTP client + transparent token
/// refresh + the offline-first Room repositories. Tokens are persisted in the Keychain
/// (`KeychainTokenStore`), so a signed-in session survives app restarts and the shared refresh
/// flow reads/writes them.
@MainActor
final class AppContainer: ObservableObject {
    let tokenStore: TokenStore
    let sdk: FlashcardSdk
    let authService: AuthService

    var flashcardRepository: FlashcardRepository { sdk.flashcardRepository }
    var practiceSessionRepository: PracticeSessionRepository { sdk.practiceSessionRepository }
    var homeRepository: HomeRepository { sdk.homeRepository }
    var apiClient: FlashcardApiClient { sdk.apiClient }
    var imageUploader: ImageUploader { ImageUploader(apiClient: sdk.apiClient) }

    init(baseURL: String = AppConfig.backendBaseURL, tokenStore: TokenStore = KeychainTokenStore()) {
        self.tokenStore = tokenStore
        // Kotlin default args don't bridge — pass the default home-feed strings explicitly.
        let sdk = IosFlashcardSdkKt.createIosFlashcardSdk(
            baseUrl: baseURL,
            tokenStore: tokenStore,
            homeFeedStrings: DefaultHomeFeedStrings.shared
        )
        self.sdk = sdk
        self.authService = AuthService(
            apiClient: sdk.apiClient,
            tokenStore: tokenStore,
            localDataStore: sdk.localDataStore
        )
    }
}
