import Shared
import SwiftUI

/// Known feature-flag keys, mirrored from the backend catalog (`flags/FeatureFlags.kt`).
enum FeatureFlag {
    static let streakCalendar = "streak_calendar"
    // Kill switches for shipped features — default ON, flipped off (or targeted) to hide/experiment.
    static let discussions = "discussions"
    static let avatarSelection = "avatar_selection"
    static let practiceModeClassic = "practice_mode_classic"
    static let practiceModeTest = "practice_mode_test"
    static let practiceModeMultipleChoice = "practice_mode_multiple_choice"
    static let practiceQuestionCount = "practice_question_count"
    static let practiceGradeAtEnd = "practice_grade_at_end"
    static let practiceTimer = "practice_timer"
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

    /// Whether the discussion affordance should show for a card (FLA-185). Guests keep read-only
    /// discussions — they carry no flags — so the `discussions` kill switch only gates signed-in
    /// users, matching web/Android (`isGuest || isEnabled(...)`).
    func discussionsVisible(deckEnabled: Bool, isGuest: Bool) -> Bool {
        deckEnabled && (isGuest || isEnabled(FeatureFlag.discussions))
    }
}
