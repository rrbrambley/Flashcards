import Shared
import SwiftUI

/// What the practice screen shows (mirrors Android's `FlashcardsUiState`).
enum PracticeState {
    case loading
    case showCard(card: Flashcard, position: Int, numCorrect: Int, numIncorrect: Int, canGoBack: Bool)
    case completed(numCorrect: Int, numIncorrect: Int)
    case failed
}

/// How a practice run is launched.
enum PracticeEntry {
    /// Start or resume a session for a deck (Library "Practice").
    case deck(Int64)
    /// Resume an existing session (Home "Continue practice").
    case session(Int64)
    /// The global Flags of the World cards, with no session (Home "Practice the flags of the world").
    case defaultFlashcards
}

/// Drives a practice run: starts-or-resumes (or restores) a session, restores progress, and
/// persists each step (current card index + correct/incorrect) through the shared
/// `PracticeSessionRepository` (offline-first → syncs to the backend). The default-flashcards entry
/// has no session, so it isn't persisted. Swipe right = correct, left = needs practice.
@MainActor
final class PracticeViewModel: ObservableObject {
    @Published private(set) var state: PracticeState = .loading

    private let flashcardRepository: FlashcardRepository
    private let sessionRepository: PracticeSessionRepository
    private let entry: PracticeEntry

    private var sessionId: Int64?
    private var cards: [Flashcard] = []
    private var index = 0
    private var numCorrect = 0
    private var numIncorrect = 0

    init(flashcardRepository: FlashcardRepository, sessionRepository: PracticeSessionRepository, entry: PracticeEntry) {
        self.flashcardRepository = flashcardRepository
        self.sessionRepository = sessionRepository
        self.entry = entry
    }

    func start() async {
        switch entry {
        case let .deck(deckId):
            guard let started = try? await sessionRepository.startOrResumeSession(deckId: deckId) else {
                state = .failed
                return
            }
            sessionId = started.int64Value
            await restoreFromSession()
            await loadDeckCards(deckId: deckId)
        case let .session(sid):
            sessionId = sid
            guard let deckId = await restoreFromSession() else { state = .failed; return }
            await loadDeckCards(deckId: deckId)
        case .defaultFlashcards:
            guard let adapter = try? await BridgingKt.defaultFlashcardsAdapter(flashcardRepository) else {
                state = .failed
                return
            }
            for await cards in asyncStream(adapter) {
                self.cards = (cards as? [Flashcard]) ?? []
                break
            }
            guard !cards.isEmpty else { state = .failed; return }
            updateState()
        }
    }

    func swipeRight() {
        numCorrect += 1
        goForward()
    }

    func swipeLeft() {
        numIncorrect += 1
        goForward()
    }

    func goBack() {
        guard index > 0 else { return }
        index -= 1
        updateState()
        persist()
    }

    func goForward() {
        if index < cards.count - 1 {
            index += 1
            updateState()
            persist()
        } else {
            state = .completed(numCorrect: numCorrect, numIncorrect: numIncorrect)
            complete()
        }
    }

    /// Reads the session once (restoring index + scores) and returns its deck id.
    @discardableResult
    private func restoreFromSession() async -> Int64? {
        guard let sid = sessionId else { return nil }
        for await session in asyncStream(BridgingKt.sessionAdapter(sessionRepository, sessionId: sid)) {
            guard let session, !session.isCompleted else { continue }
            index = Int(session.currentCardIndex)
            numCorrect = Int(session.numCorrect)
            numIncorrect = Int(session.numIncorrect)
            return session.deckId
        }
        return nil
    }

    private func loadDeckCards(deckId: Int64) async {
        for await deck in asyncStream(BridgingKt.flashcardDeckAdapter(flashcardRepository, deckId: deckId)) {
            guard let deck else { continue }
            cards = (deck.flashcards as? [Flashcard]) ?? []
            break
        }
        guard !cards.isEmpty else { state = .failed; return }
        index = min(max(index, 0), cards.count - 1)
        updateState()
    }

    private func updateState() {
        guard !cards.isEmpty else { state = .failed; return }
        state = .showCard(
            card: cards[index],
            position: index,
            numCorrect: numCorrect,
            numIncorrect: numIncorrect,
            canGoBack: index > 0
        )
    }

    private func persist() {
        guard let sid = sessionId else { return }
        let (i, c, w) = (index, numCorrect, numIncorrect)
        Task {
            try? await sessionRepository.updateProgress(
                sessionId: sid,
                currentCardIndex: Int32(i),
                numCorrect: Int32(c),
                numIncorrect: Int32(w)
            )
        }
    }

    private func complete() {
        guard let sid = sessionId else { return }
        Task { try? await sessionRepository.completeSession(sessionId: sid) }
    }
}
