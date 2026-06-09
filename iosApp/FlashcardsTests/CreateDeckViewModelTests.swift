import XCTest
import Shared
@testable import Flashcards

@MainActor
final class CreateDeckViewModelTests: XCTestCase {
    private func makeVM(_ repo: FakeFlashcardRepository) -> CreateDeckViewModel {
        CreateDeckViewModel(repository: repo, imageUploader: StubImageUploader())
    }

    func test_save_withEmptyTitle_isInvalidAndDoesNotSave() async {
        let repo = FakeFlashcardRepository()
        let vm = makeVM(repo)
        vm.cards = [CardDraft(term: "a", definition: "1")]

        await vm.save()

        XCTAssertTrue(vm.showErrors)
        XCTAssertNil(repo.savedDeck)
    }

    func test_save_withNoCompleteCard_isInvalid() async {
        let repo = FakeFlashcardRepository()
        let vm = makeVM(repo)
        vm.deckTitle = "Title"
        vm.cards = [CardDraft(term: "a", definition: "")] // started but incomplete

        await vm.save()

        XCTAssertTrue(vm.showErrors)
        XCTAssertNil(repo.savedDeck)
    }

    func test_save_success_buildsDeckFromCompleteCardsAndResets() async {
        let repo = FakeFlashcardRepository()
        let vm = makeVM(repo)
        vm.deckTitle = "  French  "
        vm.cards = [
            CardDraft(term: "Bonjour", definition: "Hello"),
            CardDraft(term: "", definition: "Canada", imageUrl: "https://x/flag.svg"), // image-only, kept
            CardDraft(), // empty, dropped
        ]

        await vm.save()

        XCTAssertEqual(repo.savedDeck?.title, "French") // trimmed
        XCTAssertEqual(repo.savedDeck?.flashcards.count, 2)
        XCTAssertTrue(vm.justSaved)
        XCTAssertEqual(vm.deckTitle, "") // form reset
    }

    func test_save_withCategory_setsSingleTag() async {
        let repo = FakeFlashcardRepository()
        let vm = makeVM(repo)
        vm.deckTitle = "Capitals"
        vm.category = "  Geography  "
        vm.cards = [CardDraft(term: "France", definition: "Paris")]

        await vm.save()

        // The trimmed category becomes the deck's single tag.
        XCTAssertEqual(repo.savedDeck?.tags as? [String], ["Geography"])
    }

    func test_save_withBlankCategory_setsNoTags() async {
        let repo = FakeFlashcardRepository()
        let vm = makeVM(repo)
        vm.deckTitle = "Capitals"
        vm.cards = [CardDraft(term: "France", definition: "Paris")]

        await vm.save()

        XCTAssertEqual(repo.savedDeck?.tags as? [String], [])
    }

    func test_save_failure_keepsFormAndSetsError() async {
        let repo = FakeFlashcardRepository()
        repo.saveError = NSError(domain: "test", code: 1)
        let vm = makeVM(repo)
        vm.deckTitle = "French"
        vm.cards = [CardDraft(term: "Bonjour", definition: "Hello")]

        await vm.save()

        XCTAssertNotNil(vm.saveError)
        XCTAssertFalse(vm.justSaved)
        XCTAssertEqual(vm.deckTitle, "French") // not reset, so the user can retry
    }

    func test_addAndRemoveCard() {
        let vm = makeVM(FakeFlashcardRepository())
        XCTAssertEqual(vm.cards.count, 1)
        vm.addCard()
        XCTAssertEqual(vm.cards.count, 2)
        vm.removeCard(vm.cards[0].id)
        XCTAssertEqual(vm.cards.count, 1)
    }

    func test_removeImage_clearsUrlAndError() {
        let vm = makeVM(FakeFlashcardRepository())
        vm.cards = [CardDraft(term: "a", definition: "1", imageUrl: "https://x/i.jpg", uploadError: "oops")]
        let id = vm.cards[0].id

        vm.removeImage(cardId: id)

        XCTAssertNil(vm.cards[0].imageUrl)
        XCTAssertNil(vm.cards[0].uploadError)
    }
}
