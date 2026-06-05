import XCTest
@testable import Flashcards

/// Pure validation logic for the create/edit card model — the parity-sensitive rules that decide
/// when a card is complete (incl. image-only cards) and which cards get saved.
final class CardDraftTests: XCTestCase {
    func test_isComplete_requiresDefinitionAndTermOrImage() {
        XCTAssertTrue(CardDraft(term: "Bonjour", definition: "Hello").isComplete)
        // Image-only: a definition + an image (no term) is complete (parity with Android).
        XCTAssertTrue(CardDraft(term: "", definition: "Canada", imageUrl: "https://x/flag.svg").isComplete)
        // Missing definition is never complete, even with a term + image.
        XCTAssertFalse(CardDraft(term: "Bonjour", definition: "", imageUrl: "https://x/f.svg").isComplete)
        // A term alone (no definition) is incomplete.
        XCTAssertFalse(CardDraft(term: "Bonjour", definition: "").isComplete)
        // Whitespace is trimmed, so blanks don't count.
        XCTAssertFalse(CardDraft(term: "  ", definition: "  ").isComplete)
    }

    func test_isStarted_trueWhenAnyFieldOrImagePresent() {
        XCTAssertFalse(CardDraft().isStarted)
        XCTAssertTrue(CardDraft(term: "x").isStarted)
        XCTAssertTrue(CardDraft(definition: "y").isStarted)
        XCTAssertTrue(CardDraft(imageUrl: "https://x/f.svg").isStarted)
    }

    func test_completeCards_filtersIncomplete() {
        let cards = [
            CardDraft(term: "a", definition: "1"),
            CardDraft(term: "b", definition: ""),        // started but incomplete
            CardDraft(),                                   // empty
            CardDraft(term: "", definition: "2", imageUrl: "https://x/f.svg"), // image-only
        ]
        XCTAssertEqual(cards.completeCards.count, 2)
    }

    func test_hasIncompleteStartedCard() {
        XCTAssertTrue([CardDraft(term: "a", definition: "")].hasIncompleteStartedCard)
        XCTAssertFalse([CardDraft(term: "a", definition: "1")].hasIncompleteStartedCard)
        XCTAssertFalse([CardDraft()].hasIncompleteStartedCard)
    }

    func test_equality_ignoresTransientUploadState() {
        let id = UUID()
        let a = CardDraft(id: id, term: "a", definition: "1", uploading: false, uploadError: nil)
        let b = CardDraft(id: id, term: "a", definition: "1", uploading: true, uploadError: "oops")
        // Transient upload state must not count as an edit (drives the edit screen's dirty guard).
        XCTAssertEqual(a, b)
    }
}
