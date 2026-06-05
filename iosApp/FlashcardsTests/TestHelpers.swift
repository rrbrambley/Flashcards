import XCTest

extension XCTestCase {
    /// Polls until `condition` holds (or the timeout elapses), yielding so fire-and-forget `Task`s
    /// — e.g. the practice view model's progress persistence / completion — get a chance to run.
    func waitUntil(timeout: TimeInterval = 1, _ condition: @escaping () -> Bool) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() && Date() < deadline {
            await Task.yield()
        }
    }
}
