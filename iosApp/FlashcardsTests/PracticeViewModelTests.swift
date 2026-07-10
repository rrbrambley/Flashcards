import XCTest
import Shared
@testable import Flashcards

/// Exercises the thin iOS adapter over the shared `PracticeSessionController` (FLA-197): that it
/// drives the controller and maps its `PracticeUiState` onto the Swift `PracticeState` the view uses.
/// The controller's own state-machine logic is covered in shared `commonTest`.
@MainActor
final class PracticeViewModelTests: XCTestCase {
    /// Builds a view model that resumes a session (the entry that reaches ShowCard purely from the
    /// fakes — no network). A stub API client satisfies the constructor without being exercised.
    private func makeVM(deck: FlashcardDeck, session: PracticeSession) -> PracticeViewModel {
        let repo = FakeFlashcardRepository()
        repo.deck = deck
        let sessions = FakePracticeSessionRepository()
        sessions.session = session
        return PracticeViewModel(
            flashcardRepository: repo,
            sessionRepository: sessions,
            entry: .session(session.id),
            apiClient: FlowTestSupportKt.stubApiClient()
        )
    }

    private let twoCardDeck = makeDeck(id: 1, "Spanish", cards: [makeCard("Q1", "A1"), makeCard("Q2", "A2")])

    func test_start_showsTheFirstCardMappedFromTheController() async {
        let vm = makeVM(deck: twoCardDeck, session: makeSession(id: 5, deckId: 1, index: 0))

        await vm.start()
        await waitUntil { if case .showCard = vm.state { return true }; return false }

        guard case let .showCard(card, position, numCorrect, _, canGoBack, mode, deckCards, _, _, _) = vm.state else {
            return XCTFail("expected .showCard, got \(vm.state)")
        }
        XCTAssertEqual(card.question, "Q1")
        XCTAssertEqual(position, 0)
        XCTAssertEqual(numCorrect, 0)
        XCTAssertFalse(canGoBack) // first card
        XCTAssertEqual(mode, "flashcards")
        XCTAssertEqual(deckCards.count, 2)
        vm.stopObserving()
    }

    func test_onResult_advancesToNextCardAndTracksScore() async {
        let vm = makeVM(deck: twoCardDeck, session: makeSession(id: 5, deckId: 1, index: 0))
        await vm.start()
        await waitUntil { if case .showCard = vm.state { return true }; return false }

        vm.onResult(correct: true)
        await waitUntil {
            if case let .showCard(card, _, _, _, _, _, _, _, _, _) = vm.state { return card.question == "Q2" }
            return false
        }

        guard case let .showCard(card, position, numCorrect, _, canGoBack, _, _, _, _, _) = vm.state else {
            return XCTFail("expected .showCard, got \(vm.state)")
        }
        XCTAssertEqual(card.question, "Q2")
        XCTAssertEqual(position, 1)
        XCTAssertEqual(numCorrect, 1)
        XCTAssertTrue(canGoBack) // no longer the first card
        vm.stopObserving()
    }
}
