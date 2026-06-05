import GoogleSignIn
import UIKit

/// Native Google Sign-In: presents Google's flow and returns the Google **ID token** to hand to the
/// shared `AuthService.signInWithGoogle` (→ `POST /auth/google`). Mirrors Android's `GoogleSignIn`.
///
/// The iOS SDK issues an ID token whose audience is the **iOS** OAuth client ID, so the backend must
/// accept that client ID as a valid audience (it does — see `GoogleTokenVerifier`). Gated on
/// `AppConfig.googleIOSClientID`; when blank, the caller hides the button.
enum GoogleSignInHelper {
    enum SignInError: Error {
        /// The user dismissed the Google flow — not a real error; the caller stays silent.
        case cancelled
        case notConfigured
        case noPresenter
        case noIDToken
    }

    static var isConfigured: Bool { !AppConfig.googleIOSClientID.isEmpty }

    /// Presents the Google flow and returns the ID token. Throws `.cancelled` if the user backs out.
    @MainActor
    static func signIn() async throws -> String {
        let clientID = AppConfig.googleIOSClientID
        guard !clientID.isEmpty else { throw SignInError.notConfigured }
        guard let presenter = topViewController() else { throw SignInError.noPresenter }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
            guard let idToken = result.user.idToken?.tokenString else { throw SignInError.noIDToken }
            return idToken
        } catch let error as GIDSignInError where error.code == .canceled {
            throw SignInError.cancelled
        }
    }

    @MainActor
    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .first { $0.activationState == .foregroundActive } as? UIWindowScene
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
