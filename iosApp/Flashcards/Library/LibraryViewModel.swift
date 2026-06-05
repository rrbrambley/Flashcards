import Shared
import SwiftUI

/// Lists the user's decks from the offline-first `FlashcardRepository` (which best-effort re-syncs
/// from the backend on subscribe, then emits the cached decks).
@MainActor
final class LibraryViewModel: ObservableObject {
    @Published private(set) var state: LoadState<[FlashcardDeck]> = .loading

    private let repository: FlashcardRepository

    init(repository: FlashcardRepository) {
        self.repository = repository
    }

    /// Long-lived subscription driving the list. New subscriptions re-sync from the backend first.
    func observe() async {
        for await decks in asyncStream(BridgingKt.flashcardDecksAdapter(repository)) {
            state = .loaded((decks as? [FlashcardDeck]) ?? [])
        }
    }

    /// Pull-to-refresh: a fresh subscription re-syncs; await the first emission so the spinner holds.
    func refresh() async {
        for await decks in asyncStream(BridgingKt.flashcardDecksAdapter(repository)) {
            state = .loaded((decks as? [FlashcardDeck]) ?? [])
            return
        }
    }
}
