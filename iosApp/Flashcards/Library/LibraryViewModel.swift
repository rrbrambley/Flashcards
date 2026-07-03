import Shared
import SwiftUI

/// SwiftUI conveniences on the shared `DeckSortOrder` (FLA-193). The sort *order type* and the
/// filter/sort *logic* now live in the shared KMP `DeckLibrary`; this only adds the label + the
/// `Identifiable` conformance `Picker`/`ForEach` need. (The bridged Kotlin enum is a non-final class,
/// so it can't cleanly be `CaseIterable` — the view iterates its `.entries` instead.)
extension DeckSortOrder: @retroactive Identifiable {
    public var id: String { name }
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
    /// True when the latest background deck refresh failed but cached decks are still shown — the
    /// view surfaces an unobtrusive banner (parity with Android's snackbar).
    @Published private(set) var refreshFailed = false

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

    /// Flags `refreshFailed` whenever a background deck refresh fails, so the view can warn the user
    /// it's showing cached decks. Long-lived for the screen's lifetime.
    func observeRefreshFailures() async {
        for await _ in asyncStream(BridgingKt.deckRefreshFailuresAdapter(flashcardRepository)) {
            refreshFailed = true
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
        // Optimistically clear the failure banner; the failure stream re-sets it if this refresh
        // also fails (a successful refresh emits no signal, so it simply stays cleared).
        refreshFailed = false
        for await decks in asyncStream(BridgingKt.flashcardDecksAdapter(flashcardRepository)) {
            rawDecks = (decks as? [FlashcardDeck]) ?? []
            loaded = true
            recompute()
            return
        }
    }

    private func recompute() {
        guard loaded else { return }
        // Search + sort rules live in the shared KMP layer (DeckLibrary) so Android + iOS match.
        // Kotlin `Map<Long, Long>` params cross the KN bridge as boxed `KotlinLong` keys/values.
        let boxed = Dictionary(uniqueKeysWithValues: lastPracticed.map {
            (KotlinLong(longLong: $0.key), KotlinLong(longLong: $0.value))
        })
        state = .loaded(
            DeckLibrary.shared.query(
                decks: rawDecks,
                query: searchQuery,
                order: sortOrder,
                lastPracticedMillis: boxed
            )
        )
    }
}
