import Shared

/// The auth operations the auth view model needs, as a testing seam. The shared `AuthService`
/// already provides these exact (bridged) signatures, so it conforms directly.
protocol AuthenticationService {
    func login(email: String, password: String) async throws -> AuthResult
    func register(email: String, password: String) async throws -> AuthResult
    func signInWithGoogle(idToken: String) async throws -> AuthResult
}

extension AuthService: AuthenticationService {}
