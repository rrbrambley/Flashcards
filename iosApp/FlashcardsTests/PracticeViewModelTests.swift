import XCTest
import Shared
@testable import Flashcards

@MainActor
final class PracticeViewModelTests: XCTestCase {
    private func threeCardDeck() -> FlashcardDeck {
        makeDeck(id: 1, "Deck", cards: [makeCard("q0", "a0"), makeCard("q1", "a1"), makeCard("q2", "a2")])
    }

    /// Asserts the VM is showing the expected card position/score, returning the shown card.
    @discardableResult
    private func assertShowing(
        _ state: PracticeState, position: Int, correct: Int, incorrect: Int, canGoBack: Bool,
        _ file: StaticString = #file, _ line: UInt = #line
    ) -> Flashcard? {
        guard case let .showCard(card, pos, numCorrect, numIncorrect, back, _, _, _, _) = state else {
            XCTFail("expected .showCard, got \(state)", file: file, line: line)
            return nil
        }
        XCTAssertEqual(pos, position, "position", file: file, line: line)
        XCTAssertEqual(numCorrect, correct, "numCorrect", file: file, line: line)
        XCTAssertEqual(numIncorrect, incorrect, "numIncorrect", file: file, line: line)
        XCTAssertEqual(back, canGoBack, "canGoBack", file: file, line: line)
        return card
    }

    private func makeVM(deck: FlashcardDeck, sessions: FakePracticeSessionRepository, entry: PracticeEntry) -> PracticeViewModel {
        let repo = FakeFlashcardRepository()
        repo.deck = deck
        return PracticeViewModel(flashcardRepository: repo, sessionRepository: sessions, entry: entry)
    }

    func test_deckEntry_startsAtFirstCard() async {
        let vm = makeVM(deck: threeCardDeck(), sessions: FakePracticeSessionRepository(), entry: .deck(1, mode: "flashcards"))
        await vm.start()
        let card = assertShowing(vm.state, position: 0, correct: 0, incorrect: 0, canGoBack: false)
        XCTAssertEqual(card?.question, "q0")
    }

    func test_swipeRightThenLeft_tracksScoreAndAdvances() async {
        let sessions = FakePracticeSessionRepository()
        let vm = makeVM(deck: threeCardDeck(), sessions: sessions, entry: .deck(1, mode: "flashcards"))
        await vm.start()

        vm.onResult(correct: true)
        assertShowing(vm.state, position: 1, correct: 1, incorrect: 0, canGoBack: true)
        // Progress is persisted to the session (fire-and-forget Task).
        await waitUntil { sessions.updatedProgress != nil }
        XCTAssertEqual(sessions.updatedProgress?.index, 1)
        XCTAssertEqual(sessions.updatedProgress?.correct, 1)

        vm.onResult(correct: false)
        assertShowing(vm.state, position: 2, correct: 1, incorrect: 1, canGoBack: true)
    }

    func test_goBack_returnsToPreviousCard() async {
        let vm = makeVM(deck: threeCardDeck(), sessions: FakePracticeSessionRepository(), entry: .deck(1, mode: "flashcards"))
        await vm.start()
        vm.onResult(correct: true) // -> position 1

        vm.goBack()

        assertShowing(vm.state, position: 0, correct: 1, incorrect: 0, canGoBack: false)
    }

    func test_advancingPastLastCard_completesAndPersists() async {
        let sessions = FakePracticeSessionRepository()
        let vm = makeVM(deck: threeCardDeck(), sessions: sessions, entry: .deck(1, mode: "flashcards"))
        await vm.start()

        vm.onResult(correct: true) // 0 -> 1
        vm.onResult(correct: true) // 1 -> 2
        vm.onResult(correct: false)  // 2 -> completed

        guard case let .completed(correct, incorrect) = vm.state else {
            return XCTFail("expected .completed, got \(vm.state)")
        }
        XCTAssertEqual(correct, 2)
        XCTAssertEqual(incorrect, 1)
        await waitUntil { sessions.completedSessionId != nil }
        XCTAssertEqual(sessions.completedSessionId, 1)
    }

    func test_sessionEntry_resumesProgress() async {
        let sessions = FakePracticeSessionRepository()
        sessions.session = makeSession(id: 5, deckId: 1, index: 1, correct: 2, incorrect: 1)
        let vm = makeVM(deck: threeCardDeck(), sessions: sessions, entry: .session(5))

        await vm.start()

        let card = assertShowing(vm.state, position: 1, correct: 2, incorrect: 1, canGoBack: true)
        XCTAssertEqual(card?.question, "q1")
    }

    func test_emptyDeck_failsGracefully() async {
        let vm = makeVM(deck: makeDeck(id: 1, "Empty"), sessions: FakePracticeSessionRepository(), entry: .deck(1, mode: "flashcards"))
        await vm.start()
        guard case .failed = vm.state else {
            return XCTFail("expected .failed, got \(vm.state)")
        }
    }

    func test_deckEntry_startsSessionInTheChosenMode() async {
        let sessions = FakePracticeSessionRepository()
        let vm = makeVM(deck: threeCardDeck(), sessions: sessions, entry: .deck(1, mode: "test"))

        await vm.start()

        XCTAssertEqual(sessions.startedMode, "test")
    }

    func test_sessionEntry_exposesTheSessionModeAndDeck() async {
        let sessions = FakePracticeSessionRepository()
        sessions.session = makeSession(id: 5, deckId: 1, mode: "multiple_choice")
        let vm = makeVM(deck: threeCardDeck(), sessions: sessions, entry: .session(5))

        await vm.start()

        guard case let .showCard(_, _, _, _, _, mode, deck, _, _) = vm.state else {
            return XCTFail("expected .showCard, got \(vm.state)")
        }
        XCTAssertEqual(mode, "multiple_choice")
        XCTAssertEqual(deck.count, 3)
    }
}
