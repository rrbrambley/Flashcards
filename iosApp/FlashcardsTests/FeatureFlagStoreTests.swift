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
}
