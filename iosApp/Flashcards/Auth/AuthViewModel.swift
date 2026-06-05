import Shared
import SwiftUI

/// Drives the login/register form. Calls the shared `AuthService` (which persists tokens on
/// success — that flips `RootView` to the main tabs) and surfaces validation/error messages.
@MainActor
final class AuthViewModel: ObservableObject {
    enum Mode {
        case login, register
    }

    @Published var mode: Mode = .login
    @Published var email = ""
    @Published var password = ""
    @Published private(set) var isSubmitting = false
    @Published var errorMessage: String?

    private let authService: AuthService

    init(authService: AuthService) {
        self.authService = authService
    }

    var title: String { mode == .login ? "Welcome back" : "Create your account" }
    var submitTitle: String { mode == .login ? "Log in" : "Create account" }
    var googleTitle: String { mode == .login ? "Sign in with Google" : "Sign up with Google" }
    var switchPrompt: String {
        mode == .login ? "Don't have an account? Register" : "Already have an account? Log in"
    }

    func toggleMode() {
        mode = mode == .login ? .register : .login
        errorMessage = nil
    }

    func submit() async {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedEmail.isEmpty, !password.isEmpty else {
            errorMessage = "Enter your email and password."
            return
        }
        guard !isSubmitting else { return }
        isSubmitting = true
        errorMessage = nil

        let result: AuthResult?
        switch mode {
        case .login:
            result = try? await authService.login(email: trimmedEmail, password: password)
        case .register:
            result = try? await authService.register(email: trimmedEmail, password: password)
        }

        isSubmitting = false
        // On success the shared TokenStore is updated, which flips RootView to the main tabs —
        // nothing to do here. A nil result means the task was cancelled.
        if let failure = result as? AuthResult.Failure {
            errorMessage = failure.message
        }
    }
}
