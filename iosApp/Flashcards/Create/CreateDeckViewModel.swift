import PhotosUI
import Shared
import SwiftUI

/// Drives the create-deck form. Validation mirrors Android: a title plus at least one complete
/// card, and no started-but-incomplete card. Saves through the shared offline-first
/// `FlashcardRepository`. (See `CardDraft` for the card model + completeness rules.)
@MainActor
final class CreateDeckViewModel: ObservableObject {
    @Published var deckTitle = ""
    /// Optional category — surfaced as the deck's single tag (the backend stores a list).
    @Published var category = ""
    @Published var cards: [CardDraft] = [CardDraft()]
    @Published private(set) var showErrors = false
    @Published private(set) var isSaving = false
    @Published var saveError: String?
    @Published private(set) var justSaved = false

    private let repository: FlashcardRepository
    private let imageUploader: ImageUploading

    init(repository: FlashcardRepository, imageUploader: ImageUploading) {
        self.repository = repository
        self.imageUploader = imageUploader
    }

    func addCard() {
        cards.append(CardDraft())
        justSaved = false
    }

    func removeCard(_ id: UUID) {
        cards.removeAll { $0.id == id }
        showErrors = false
    }

    func pickImage(cardId: UUID, item: PhotosPickerItem) {
        updateCard(cardId) { $0.uploading = true; $0.uploadError = nil }
        Task {
            do {
                let url = try await imageUploader.upload(item: item)
                updateCard(cardId) { $0.imageUrl = url; $0.uploading = false }
            } catch {
                updateCard(cardId) { $0.uploading = false; $0.uploadError = ImageUploader.errorMessage }
            }
        }
    }

    func removeImage(cardId: UUID) {
        updateCard(cardId) { $0.imageUrl = nil; $0.uploadError = nil }
    }

    private func updateCard(_ id: UUID, _ transform: (inout CardDraft) -> Void) {
        guard let index = cards.firstIndex(where: { $0.id == id }) else { return }
        transform(&cards[index])
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
                // New cards have no cardUid — the backend mints one on save (FLA-113).
                Flashcard(
                    question: $0.term.trimmed,
                    answer: $0.definition.trimmed,
                    imageUrl: $0.imageUrl,
                    alternativeAnswers: parseAlternatives($0.alternatives),
                    cardUid: $0.cardUid
                )
            },
            isEditable: true,
            // The optional category as a single tag (empty when blank). Kotlin default args don't
            // bridge, so pass it explicitly.
            tags: category.toCategoryTags(),
            // Discussions are admin-toggled on global decks; a newly created deck never has them.
            discussionsEnabled: false,
            // A user-created deck is never a global (catalog) deck.
            isGlobal: false
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
        category = ""
        cards = [CardDraft()]
        showErrors = false
    }
}
