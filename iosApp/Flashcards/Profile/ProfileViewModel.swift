import Shared
import SwiftUI

/// Drives the profile screen (FLA-167): loads the current profile + avatar catalog, and picks/clears
/// the avatar via `PATCH /auth/me` (a select saves immediately, mirroring web + Android). The catalog
/// fetch degrades to empty (picker hidden) if the CDN is unconfigured, without failing the screen.
@MainActor
final class ProfileViewModel: ObservableObject {
    @Published private(set) var isLoading = true
    @Published private(set) var loadFailed = false
    @Published private(set) var avatars: [AvatarDto] = []
    @Published private(set) var selectedAvatarKey: String?
    @Published private(set) var avatarUrl: String?
    @Published private(set) var displayName: String?
    @Published private(set) var email: String?
    @Published private(set) var isSaving = false
    @Published private(set) var avatarError = false

    private let service: ProfileService

    init(service: ProfileService) {
        self.service = service
    }

    /// The name used for the monogram fallback + image label: display name, else the email local-part.
    var monogramName: String? {
        if let name = displayName?.trimmingCharacters(in: .whitespaces), !name.isEmpty { return name }
        return email.map { String($0.prefix { $0 != "@" }) }
    }

    func load() async {
        isLoading = true
        loadFailed = false
        do {
            let me = try await service.me()
            // A missing/empty catalog (no CDN) must not fail the screen — just hides the picker.
            let catalog = (try? await service.avatars()) ?? []
            avatars = catalog
            selectedAvatarKey = me.avatarKey
            avatarUrl = me.avatarUrl
            displayName = me.displayName
            email = me.email
        } catch {
            loadFailed = true
        }
        isLoading = false
    }

    /// Select an avatar by `key`; a blank key clears it (backend merge semantics).
    func select(key: String) async {
        guard !isSaving else { return }
        isSaving = true
        avatarError = false
        do {
            let me = try await service.updateAvatar(key: key)
            selectedAvatarKey = me.avatarKey
            avatarUrl = me.avatarUrl
        } catch {
            avatarError = true
        }
        isSaving = false
    }

    /// Clear the avatar (falls back to the initials monogram).
    func clear() async {
        await select(key: "")
    }
}
