import XCTest
import Shared
@testable import Flashcards

/// The timed-run urgency tiers (#443) as seen from Swift.
///
/// The tier arithmetic itself is covered by `PresentationHelpersTest` in shared `commonTest`, which
/// runs on Native too. What's pinned here is the **bridge**: every call site converts `Int` → `Int32`,
/// and a wrong conversion is exactly the kind of thing that compiles fine and misbehaves at runtime.
final class UrgencyTests: XCTestCase {

    func test_tiers_crossAtTenAndFive() {
        XCTAssertFalse(TimedUrgency.shared.isUrgent(remainingSeconds: 11))
        XCTAssertTrue(TimedUrgency.shared.isUrgent(remainingSeconds: 10))
        XCTAssertFalse(TimedUrgency.shared.isCritical(remainingSeconds: 6))
        XCTAssertTrue(TimedUrgency.shared.isCritical(remainingSeconds: 5))
    }

    func test_intensity_rampsAcrossTheCriticalTier() {
        XCTAssertEqual(urgencyIntensity(6), 0, accuracy: 0.001)
        XCTAssertEqual(urgencyIntensity(5), 0.2, accuracy: 0.001)
        XCTAssertEqual(urgencyIntensity(4), 0.4, accuracy: 0.001)
        // The final three seconds cross the line where the tick switches to the heavier haptic.
        XCTAssertEqual(urgencyIntensity(3), 0.6, accuracy: 0.001)
        XCTAssertEqual(urgencyIntensity(2), 0.8, accuracy: 0.001)
        XCTAssertEqual(urgencyIntensity(1), 1.0, accuracy: 0.001)
    }

    func test_criticalStopsShortOfZero() {
        // At 0 the run is already leaving for the time-up reveal, so a final flash or haptic would
        // land on a view that's going away (#443).
        XCTAssertTrue(isCriticalSecond(1))
        XCTAssertFalse(isCriticalSecond(0))
        XCTAssertEqual(urgencyIntensity(0), 0, accuracy: 0.001)
    }

    func test_untimedRunHasNoUrgency() {
        // The view models map the shared `-1` sentinel back to nil before it reaches any of this,
        // but nil must be inert regardless — it's what an untimed run passes.
        XCTAssertFalse(isCriticalSecond(nil))
        XCTAssertEqual(urgencyIntensity(nil), 0, accuracy: 0.001)
    }

    func test_negativeSecondsAreNotCritical() {
        // Belt and braces for the `-1` sentinel: were it ever to reach here unmapped, it must not
        // read as maximum urgency.
        XCTAssertFalse(isCriticalSecond(-1))
        XCTAssertEqual(urgencyIntensity(-1), 0, accuracy: 0.001)
    }
}
