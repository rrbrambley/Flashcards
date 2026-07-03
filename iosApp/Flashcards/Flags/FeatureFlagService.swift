import Shared

/// A testing seam over the feature-flag read (FLA-178), mirroring `ProfileService`. The concrete
/// `FlashcardApiClient` is a final Kotlin class that can't be faked in Swift, so `FeatureFlagStore`
/// depends on this protocol and tests inject a fake.
protocol FeatureFlagService {
    /// The caller's resolved flags (flag key → enabled).
    func flags() async throws -> [String: Bool]
}

/// Backs `FeatureFlagService` with the shared `FlashcardApiClient`. `getFlags()` is
/// `@Throws(Exception::class)`, so a network failure bridges to a Swift `catch` (never a crash).
struct ApiFeatureFlagService: FeatureFlagService {
    let apiClient: FlashcardApiClient

    func flags() async throws -> [String: Bool] {
        // Kotlin `Map<String, Boolean>` bridges to `[String: KotlinBoolean]`; unwrap to Swift Bool.
        try await apiClient.getFlags().mapValues { $0.boolValue }
    }
}
