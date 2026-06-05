import Shared
import SwiftUI

/// Drives the create-deck form. Validation mirrors Android: a title plus at least one complete
/// card, and no started-but-incomplete card. Saves through the shared offline-first
/// `FlashcardRepository`. (See `CardDraft` for the card model + completeness rules.)
@MainActor
final class CreateDeckViewModel: ObservableObject {
    @Published var deckTitle = ""
    @Published var cards: [CardDraft] = [CardDraft()]
    @Published private(set) var showErrors = false
    @Published private(set) var isSaving = false
    @Published var saveError: String?
    @Published private(set) var justSaved = false

    private let repository: FlashcardRepository

    init(repository: FlashcardRepository) {
        self.repository = repository
    }

    func addCard() {
        cards.append(CardDraft())
        justSaved = false
    }

    func removeCard(_ id: UUID) {
        cards.removeAll { $0.id == id }
        showErrors = false
    }

    func save() async {
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
            id: 0,
            title: deckTitle.trimmed,
            flashcards: complete.map {
                Flashcard(question: $0.term.trimmed, answer: $0.definition.trimmed, imageUrl: nil)
            },
            isEditable: true
        )
        do {
            try await repository.saveFlashcardDeck(deck: deck)
            reset()
            justSaved = true
        } catch {
            // Keep the form so the user can retry (e.g. offline).
            saveError = "Couldn't save the deck. Check your connection and try again."
        }
        isSaving = false
    }

    func acknowledgeSaved() { justSaved = false }

    private func reset() {
        deckTitle = ""
        cards = [CardDraft()]
        showErrors = false
    }
}
