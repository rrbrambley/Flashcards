import XCTest
import Shared
@testable import Flashcards

@MainActor
final class LibraryViewModelTests: XCTestCase {
    private func makeVM(
        _ repo: FakeFlashcardRepository,
        _ sessions: FakePracticeSessionRepository = FakePracticeSessionRepository()
    ) -> LibraryViewModel {
        LibraryViewModel(flashcardRepository: repo, sessionRepository: sessions)
    }

    /// Extracts the deck titles from a `.loaded` state (fails the test otherwise).
    private func loadedTitles(_ state: LoadState<[FlashcardDeck]>, _ file: StaticString = #file, _ line: UInt = #line) -> [String] {
        guard case let .loaded(decks) = state else {
            XCTFail("expected .loaded, got \(state)", file: file, line: line)
            return []
        }
        return decks.map(\.title)
    }

    func test_alphabeticalSort_isCaseInsensitive() async {
        let repo = FakeFlashcardRepository()
        repo.decks = [makeDeck(id: 1, "Zebra"), makeDeck(id: 2, "apple"), makeDeck(id: 3, "Mango")]
        let vm = makeVM(repo)

        await vm.observeDecks()

        XCTAssertEqual(loadedTitles(vm.state), ["apple", "Mango", "Zebra"])
        XCTAssertTrue(vm.hasAnyDecks)
    }

    func test_search_filtersByTitleCaseInsensitively() async {
        let repo = FakeFlashcardRepository()
        repo.decks = [makeDeck(id: 1, "French"), makeDeck(id: 2, "Spanish"), makeDeck(id: 3, "Frenemies")]
        let vm = makeVM(repo)
        await vm.observeDecks()

        vm.searchQuery = "fren"

        // Filtered to the two matches, A–Z (default): "French" < "Frenemies" ('c' < 'e').
        XCTAssertEqual(loadedTitles(vm.state), ["French", "Frenemies"])
    }

    func test_search_alsoMatchesTags() async {
        let repo = FakeFlashcardRepository()
        repo.decks = [makeDeck(id: 1, "Flags", tags: ["Geography"]), makeDeck(id: 2, "French", tags: ["Language"])]
        let vm = makeVM(repo)
        await vm.observeDecks()

        // "geo" matches the Geography tag even though no title contains it.
        vm.searchQuery = "geo"

        XCTAssertEqual(loadedTitles(vm.state), ["Flags"])
    }

    func test_recentlyPracticedSort_ordersByLastPracticedThenIdDesc() async {
        let repo = FakeFlashcardRepository()
        repo.decks = [makeDeck(id: 1, "A"), makeDeck(id: 2, "B"), makeDeck(id: 3, "C")]
        let sessions = FakePracticeSessionRepository()
        sessions.lastPracticed = [1: 500, 2: 900] // deck 3 never practiced
        let vm = makeVM(repo, sessions)
        await vm.observeDecks()
        await vm.observeLastPracticed()

        vm.sortOrder = .recentlypracticed

        // 2 (900) then 1 (500), then never-practiced 3 falls back to id-desc.
        XCTAssertEqual(loadedTitles(vm.state), ["B", "A", "C"])
    }

    func test_deleteDeck_callsRepository() async {
        let repo = FakeFlashcardRepository()
        repo.decks = [makeDeck(id: 7, "Gone")]
        let vm = makeVM(repo)
        await vm.observeDecks()

        await vm.deleteDeck(7)

        XCTAssertNil(vm.deleteError)
    }

    func test_deleteDeck_onError_setsMessage() async {
        let repo = FakeFlashcardRepository()
        repo.deleteError = NSError(domain: "test", code: 1)
        let vm = makeVM(repo)

        await vm.deleteDeck(1)

        XCTAssertEqual(vm.deleteError, "Couldn't delete the deck. Check your connection and try again.")
    }
}
