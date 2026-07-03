import Shared
import SwiftUI

/// Known feature-flag keys, mirrored from the backend catalog (`flags/FeatureFlags.kt`).
enum FeatureFlag {
    static let streakCalendar = "streak_calendar"
}

/// Holds the caller's resolved feature flags (FLA-174) so SwiftUI can hide/reveal features. iOS has
/// no central identity store, so this introduces one for flags: `RootView` loads it after sign-in
/// and clears it on sign-out, and it's injected via `.environmentObject`. `isEnabled` defaults false
/// for unknown flags or before the first load.
@MainActor
final class FeatureFlagStore: ObservableObject {
    @Published private(set) var flags: [String: Bool] = [:]

    private let service: FeatureFlagService

    init(service: FeatureFlagService) {
        self.service = service
    }

    /// Resolves the caller's flags; on failure (or signed out) leaves them empty.
    func load() async {
        flags = (try? await service.flags()) ?? [:]
    }

    /// Clears the cached flags (on sign-out).
    func clear() {
        flags = [:]
    }

    func isEnabled(_ key: String) -> Bool {
        flags[key] == true
    }
}
