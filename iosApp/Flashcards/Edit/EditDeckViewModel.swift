import Shared
import SwiftUI

/// Drives the edit-deck form: loads the deck once from the shared repository, pre-fills the form,
/// tracks dirty state for the unsaved-changes guard, and saves via `updateFlashcardDeck`. The
/// read-only global catalog deck (`isEditable == false`) is view-only.
@MainActor
final class EditDeckViewModel: ObservableObject {
    @Published var deckTitle = ""
    @Published var cards: [CardDraft] = []
    @Published private(set) var isLoading = true
    @Published private(set) var isEditable = true
    @Published private(set) var showErrors = false
    @Published private(set) var isSaving = false
    @Published var saveError: String?
    @Published private(set) var didSave = false

    private let repository: FlashcardRepository
    private let deckId: Int64
    private var originalTitle = ""
    private var originalCards: [CardDraft] = []

    init(repository: FlashcardRepository, deckId: Int64) {
        self.repository = repository
        self.deckId = deckId
    }

    /// Unsaved edits exist (drives the discard-changes guard). False while loading or read-only.
    var isDirty: Bool {
        guard !isLoading, isEditable else { return false }
        return deckTitle != originalTitle || cards != originalCards
    }

    /// Loads the deck once (the stream re-syncs the full deck from the backend on subscribe); we
    /// stop after the first emission so a later background sync can't clobber in-progress edits.
    func load() async {
        for await deck in asyncStream(BridgingKt.flashcardDeckAdapter(repository, deckId: deckId)) {
            guard let deck else { continue }
            populate(from: deck)
            break
        }
    }

    func addCard() {
        cards.append(CardDraft())
    }

    func removeCard(_ id: UUID) {
        cards.removeAll { $0.id == id }
        showErrors = false
    }

    func save() async {
        guard isEditable else { return }
        let complete = cards.completeCards
        let isValid = !deckTitle.trimmed.isEmpty && !complete.isEmpty && !cards.hasIncompleteStartedCard
        guard isValid else {
            showErrors = true
            return
        }
        guard !isSaving else { return }

        isSaving = true
        saveError = nil
        let deck = FlashcardDeck(
            id: deckId,
            title: deckTitle.trimmed,
            flashcards: complete.map {
                Flashcard(question: $0.term.trimmed, answer: $0.definition.trimmed, imageUrl: nil)
            },
            isEditable: true
        )
        do {
            try await repository.updateFlashcardDeck(deck: deck)
            originalTitle = deckTitle
            originalCards = cards
            didSave = true
        } catch {
            saveError = "Couldn't save your changes. Check your connection and try again."
        }
        isSaving = false
    }

    private func populate(from deck: FlashcardDeck) {
        let drafts = (deck.flashcards as? [Flashcard])?.map {
            CardDraft(term: $0.question, definition: $0.answer)
        } ?? []
        let cards = drafts.isEmpty ? [CardDraft()] : drafts
        deckTitle = deck.title
        self.cards = cards
        isEditable = deck.isEditable
        originalTitle = deck.title
        originalCards = cards
        isLoading = false
    }
}
