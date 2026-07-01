import Shared

/// A testing seam over the profile/avatar endpoints (FLA-167), mirroring `AuthenticationService`.
/// The concrete `FlashcardApiClient` is a final Kotlin class that can't be faked in Swift, so the
/// view model depends on this protocol and tests inject a fake.
protocol ProfileService {
    func me() async throws -> MeResponse
    func avatars() async throws -> [AvatarDto]
    /// Sets the caller's avatar to `key`, or clears it when `key` is blank (backend merge semantics).
    func updateAvatar(key: String) async throws -> MeResponse
}

/// Backs `ProfileService` with the shared `FlashcardApiClient`. The client's methods are
/// `@Throws(Exception::class)`, so a network failure bridges to a Swift `catch` (never a crash).
struct ApiProfileService: ProfileService {
    let apiClient: FlashcardApiClient

    func me() async throws -> MeResponse {
        try await apiClient.getMe()
    }

    func avatars() async throws -> [AvatarDto] {
        try await apiClient.getAvatars()
    }

    func updateAvatar(key: String) async throws -> MeResponse {
        // nil displayName leaves the name unchanged; a blank key clears the avatar. Kotlin default
        // args don't bridge, so pass both explicitly.
        try await apiClient.updateProfile(request: UpdateProfileRequest(displayName: nil, avatarKey: key))
    }
}
