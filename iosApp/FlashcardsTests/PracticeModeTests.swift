import XCTest
import Shared
@testable import Flashcards

/// The per-mode feature-flag gating of the practice-mode chooser (FLA-213). Fail-open, matching
/// Android: a mode shows unless its flag is explicitly `false`.
final class PracticeModeTests: XCTestCase {
    func test_available_failsOpen_whenNoFlagsLoaded() {
        // Empty flags (offline / guest / failed fetch) → every mode still offered.
        let modes = PracticeMode.available(flags: [:])
        XCTAssertEqual(modes.map { $0.key }, PracticeMode.entries.map { $0.key })
    }

    func test_available_excludesModeWithFlagExplicitlyFalse() {
        let modes = PracticeMode.available(flags: [FeatureFlag.practiceModeTest: false])
        XCTAssertFalse(modes.contains { $0.flagKey == FeatureFlag.practiceModeTest })
        XCTAssertTrue(modes.contains { $0.flagKey == FeatureFlag.practiceModeClassic })
        XCTAssertTrue(modes.contains { $0.flagKey == FeatureFlag.practiceModeMultipleChoice })
    }

    func test_available_isEmpty_whenAllModesDisabled() {
        let modes = PracticeMode.available(flags: [
            FeatureFlag.practiceModeClassic: false,
            FeatureFlag.practiceModeTest: false,
            FeatureFlag.practiceModeMultipleChoice: false,
        ])
        XCTAssertTrue(modes.isEmpty)
    }
}
