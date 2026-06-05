import Shared
import SwiftUI

/// How the library is ordered (parity with Android's `DeckSortOrder`).
enum DeckSortOrder: String, CaseIterable, Identifiable {
    case alphabetical
    case recentlyPracticed

    var id: String { rawValue }
    var label: String { self == .alphabetical ? "A–Z" : "Recently practiced" }
}

/// Lists the user's decks from the offline-first `FlashcardRepository` (which best-effort re-syncs
/// on subscribe), with a live title filter and A–Z / recently-practiced sorting applied in memory.
@MainActor
final class LibraryViewModel: ObservableObject {
    @Published private(set) var state: LoadState<[FlashcardDeck]> = .loading
    @Published var searchQuery = "" { didSet { recompute() } }
    @Published var sortOrder: DeckSortOrder = .alphabetical { didSet { recompute() } }
    @Published var deleteError: String?

    /// True once decks have loaded — lets the view tell "no decks yet" from "no search matches".
    var hasAnyDecks: Bool { !rawDecks.isEmpty }

    private var rawDecks: [FlashcardDeck] = []
    private var lastPracticed: [Int64: Int64] = [:]
    private var loaded = false

    private let flashcardRepository: FlashcardRepository
    private let sessionRepository: PracticeSessionRepository

    init(flashcardRepository: FlashcardRepository, sessionRepository: PracticeSessionRepository) {
        self.flashcardRepository = flashcardRepository
        self.sessionRepository = sessionRepository
    }

    func observeDecks() async {
        for await decks in asyncStream(BridgingKt.flashcardDecksAdapter(flashcardRepository)) {
            rawDecks = (decks as? [FlashcardDeck]) ?? []
            loaded = true
            recompute()
        }
    }

    /// Deletes an owned deck (backend-first, then the Room flow drops it → the list updates).
    /// The global catalog deck isn't deletable, so the view only offers this on editable decks.
    func deleteDeck(_ deckId: Int64) async {
        do {
            try await flashcardRepository.deleteFlashcardDeck(deckId: deckId)
        } catch {
            deleteError = "Couldn't delete the deck. Check your connection and try again."
        }
    }

    func observeLastPracticed() async {
        for await map in asyncStream(BridgingKt.lastPracticedAdapter(sessionRepository)) {
            var result: [Int64: Int64] = [:]
            if let dict = map as? [NSNumber: NSNumber] {
                for (key, value) in dict { result[key.int64Value] = value.int64Value }
            }
            lastPracticed = result
            recompute()
        }
    }

    func refresh() async {
        for await decks in asyncStream(BridgingKt.flashcardDecksAdapter(flashcardRepository)) {
            rawDecks = (decks as? [FlashcardDeck]) ?? []
            loaded = true
            recompute()
            return
        }
    }

    private func recompute() {
        guard loaded else { return }
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        let filtered = query.isEmpty
            ? rawDecks
            : rawDecks.filter { $0.title.localizedCaseInsensitiveContains(query) }
        state = .loaded(sorted(filtered))
    }

    private func sorted(_ decks: [FlashcardDeck]) -> [FlashcardDeck] {
        switch sortOrder {
        case .alphabetical:
            return decks.sorted { $0.title.lowercased() < $1.title.lowercased() }
        case .recentlyPracticed:
            // Most-recently-practiced first; never-practiced fall back to newest-created (id desc).
            return decks.sorted {
                let lhs = lastPracticed[$0.id] ?? 0
                let rhs = lastPracticed[$1.id] ?? 0
                return lhs == rhs ? $0.id > $1.id : lhs > rhs
            }
        }
    }
}
