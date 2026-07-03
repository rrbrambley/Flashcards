import XCTest
import Shared
@testable import Flashcards

@MainActor
final class FeatureFlagStoreTests: XCTestCase {
    func test_load_reflectsFlags_andIsEnabledDefaultsFalseForUnknown() async {
        let store = FeatureFlagStore(service: FakeFeatureFlagService(flags: [FeatureFlag.streakCalendar: true]))

        await store.load()

        XCTAssertTrue(store.isEnabled(FeatureFlag.streakCalendar))
        XCTAssertFalse(store.isEnabled("not_a_flag")) // absent → false
    }

    func test_isEnabled_isFalseBeforeLoad() {
        let store = FeatureFlagStore(service: FakeFeatureFlagService(flags: [FeatureFlag.streakCalendar: true]))
        XCTAssertFalse(store.isEnabled(FeatureFlag.streakCalendar))
    }

    func test_load_failure_leavesFlagsEmpty() async {
        let service = FakeFeatureFlagService(flags: [FeatureFlag.streakCalendar: true])
        service.error = NSError(domain: "test", code: 1)
        let store = FeatureFlagStore(service: service)

        await store.load()

        XCTAssertFalse(store.isEnabled(FeatureFlag.streakCalendar))
    }

    func test_clear_resetsFlags() async {
        let store = FeatureFlagStore(service: FakeFeatureFlagService(flags: [FeatureFlag.streakCalendar: true]))
        await store.load()
        XCTAssertTrue(store.isEnabled(FeatureFlag.streakCalendar))

        store.clear()

        XCTAssertFalse(store.isEnabled(FeatureFlag.streakCalendar))
    }

    // MARK: - Kill switches (FLA-185 / FLA-189)

    func test_avatarSelection_reflectsFlag() async {
        let store = FeatureFlagStore(service: FakeFeatureFlagService(flags: [FeatureFlag.avatarSelection: false]))
        await store.load()
        XCTAssertFalse(store.isEnabled(FeatureFlag.avatarSelection))
    }

    func test_discussionsVisible_gatingRules() async {
        // Signed-in user, discussions flag OFF.
        let off = FeatureFlagStore(service: FakeFeatureFlagService(flags: [FeatureFlag.discussions: false]))
        await off.load()
        XCTAssertFalse(off.discussionsVisible(deckEnabled: true, isGuest: false), "flag off hides for signed-in")
        XCTAssertTrue(off.discussionsVisible(deckEnabled: true, isGuest: true), "guests bypass the flag")
        XCTAssertFalse(off.discussionsVisible(deckEnabled: false, isGuest: true), "deck opt-out always wins")

        // Signed-in user, discussions flag ON.
        let on = FeatureFlagStore(service: FakeFeatureFlagService(flags: [FeatureFlag.discussions: true]))
        await on.load()
        XCTAssertTrue(on.discussionsVisible(deckEnabled: true, isGuest: false), "flag on shows for signed-in")
        XCTAssertFalse(on.discussionsVisible(deckEnabled: false, isGuest: false), "deck opt-out always wins")
    }
}
