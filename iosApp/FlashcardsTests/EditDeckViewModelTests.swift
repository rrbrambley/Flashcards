import XCTest
import Shared
@testable import Flashcards

@MainActor
final class EditDeckViewModelTests: XCTestCase {
    private func makeVM(_ repo: FakeFlashcardRepository, deckId: Int64 = 1) -> EditDeckViewModel {
        EditDeckViewModel(repository: repo, imageUploader: StubImageUploader(), deckId: deckId)
    }

    func test_load_populatesFormFromDeck() async {
        let repo = FakeFlashcardRepository()
        repo.deck = makeDeck(id: 1, "French", cards: [makeCard("Bonjour", "Hello")])
        let vm = makeVM(repo)

        await vm.load()

        XCTAssertEqual(vm.deckTitle, "French")
        XCTAssertEqual(vm.cards.count, 1)
        XCTAssertEqual(vm.cards.first?.term, "Bonjour")
        XCTAssertTrue(vm.isEditable)
        XCTAssertFalse(vm.isDirty)
    }

    func test_load_readOnlyDeck_isNotEditableOrDirty() async {
        let repo = FakeFlashcardRepository()
        repo.deck = makeDeck(id: 1, "Flags of the World", cards: [makeCard("", "Canada", imageUrl: "https://x/f.svg")], editable: false)
        let vm = makeVM(repo)

        await vm.load()

        XCTAssertFalse(vm.isEditable)
        // Image-only card round-trips into the form.
        XCTAssertEqual(vm.cards.first?.imageUrl, "https://x/f.svg")
        vm.deckTitle = "changed"
        XCTAssertFalse(vm.isDirty) // read-only is never dirty
    }

    func test_isDirty_afterEditingTitle() async {
        let repo = FakeFlashcardRepository()
        repo.deck = makeDeck(id: 1, "French", cards: [makeCard("Bonjour", "Hello")])
        let vm = makeVM(repo)
        await vm.load()

        vm.deckTitle = "Français"

        XCTAssertTrue(vm.isDirty)
    }

    func test_save_updatesDeckAndMarksSaved() async {
        let repo = FakeFlashcardRepository()
        repo.deck = makeDeck(id: 1, "French", cards: [makeCard("Bonjour", "Hello")])
        let vm = makeVM(repo)
        await vm.load()
        vm.deckTitle = "Français"

        await vm.save()

        XCTAssertEqual(repo.updatedDeck?.title, "Français")
        XCTAssertTrue(vm.didSave)
    }

    func test_save_invalidWhenAllCardsRemoved() async {
        let repo = FakeFlashcardRepository()
        repo.deck = makeDeck(id: 1, "French", cards: [makeCard("Bonjour", "Hello")])
        let vm = makeVM(repo)
        await vm.load()
        vm.cards = [CardDraft()] // cleared

        await vm.save()

        XCTAssertTrue(vm.showErrors)
        XCTAssertNil(repo.updatedDeck)
    }

    func test_load_populatesCategoryFromFirstTag() async {
        let repo = FakeFlashcardRepository()
        repo.deck = makeDeck(id: 1, "Flags", cards: [makeCard("Bonjour", "Hello")], tags: ["Geography"])
        let vm = makeVM(repo)

        await vm.load()

        XCTAssertEqual(vm.category, "Geography")
        XCTAssertFalse(vm.isDirty)
    }

    func test_changingCategory_marksDirtyAndSavesAsSingleTag() async {
        let repo = FakeFlashcardRepository()
        repo.deck = makeDeck(id: 1, "Flags", cards: [makeCard("Bonjour", "Hello")], tags: ["Geography"])
        let vm = makeVM(repo)
        await vm.load()

        vm.category = "History"
        XCTAssertTrue(vm.isDirty)

        await vm.save()
        XCTAssertEqual(repo.updatedDeck?.tags as? [String], ["History"])
    }
}
