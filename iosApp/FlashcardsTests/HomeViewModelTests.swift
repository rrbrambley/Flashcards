import XCTest
import Shared
@testable import Flashcards

@MainActor
final class HomeViewModelTests: XCTestCase {
    private func makeVM(_ repo: FakeHomeRepository) -> HomeViewModel {
        HomeViewModel(repository: repo, apiClient: FlowTestSupportKt.stubApiClient())
    }

    private func loadedTitles(
        _ state: LoadState<[HomeData]>, _ file: StaticString = #file, _ line: UInt = #line
    ) -> [String] {
        guard case let .loaded(cards) = state else {
            XCTFail("expected .loaded, got \(state)", file: file, line: line)
            return []
        }
        return cards.map(\.title)
    }

    func test_observe_loadsTheFeed() async {
        let repo = FakeHomeRepository()
        repo.homeData = [makeHomeData("Practice Flags"), makeHomeData("Create a set")]
        let vm = makeVM(repo)

        await vm.observe()

        XCTAssertEqual(loadedTitles(vm.state), ["Practice Flags", "Create a set"])
        XCTAssertFalse(vm.refreshFailed)
    }

    func test_observe_refreshFailedFeed_keepsCardsAndRaisesBanner() async {
        let repo = FakeHomeRepository()
        repo.homeData = [makeHomeData("Cached item")]
        repo.refreshFailed = true
        let vm = makeVM(repo)

        await vm.observe()

        // FLA-210: a failed backend refresh keeps the cached feed on screen and just raises the banner flag.
        XCTAssertEqual(loadedTitles(vm.state), ["Cached item"])
        XCTAssertTrue(vm.refreshFailed)
    }

    func test_refresh_reflectsTheFeed() async {
        let repo = FakeHomeRepository()
        repo.homeData = [makeHomeData("Fresh item")]
        let vm = makeVM(repo)

        await vm.refresh()

        XCTAssertEqual(loadedTitles(vm.state), ["Fresh item"])
    }
}
