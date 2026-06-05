import Shared
import SwiftUI

/// What the practice screen shows (mirrors Android's `FlashcardsUiState`).
enum PracticeState {
    case loading
    case showCard(card: Flashcard, position: Int, numCorrect: Int, numIncorrect: Int, canGoBack: Bool)
    case completed(numCorrect: Int, numIncorrect: Int)
    case failed
}

/// Drives a practice run for a deck: starts-or-resumes a session, restores progress, and persists
/// each step (current card index + correct/incorrect) through the shared `PracticeSessionRepository`
/// (offline-first → syncs to the backend). Swipe right = correct, left = needs practice.
@MainActor
final class PracticeViewModel: ObservableObject {
    @Published private(set) var state: PracticeState = .loading

    private let flashcardRepository: FlashcardRepository
    private let sessionRepository: PracticeSessionRepository
    private let deckId: Int64

    private var sessionId: Int64?
    private var cards: [Flashcard] = []
    private var index = 0
    private var numCorrect = 0
    private var numIncorrect = 0

    init(flashcardRepository: FlashcardRepository, sessionRepository: PracticeSessionRepository, deckId: Int64) {
        self.flashcardRepository = flashcardRepository
        self.sessionRepository = sessionRepository
        self.deckId = deckId
    }

    func start() async {
        // Start or resume a session, restore its progress, then load the deck's cards.
        guard let session = try? await sessionRepository.startOrResumeSession(deckId: deckId) else {
            state = .failed
            return
        }
        let sid = session.int64Value
        sessionId = sid

        for await session in asyncStream(BridgingKt.sessionAdapter(sessionRepository, sessionId: sid)) {
            guard let session else { continue }
            index = Int(session.currentCardIndex)
            numCorrect = Int(session.numCorrect)
            numIncorrect = Int(session.numIncorrect)
            break
        }

        for await deck in asyncStream(BridgingKt.flashcardDeckAdapter(flashcardRepository, deckId: deckId)) {
            guard let deck else { continue }
            cards = (deck.flashcards as? [Flashcard]) ?? []
            break
        }

        guard !cards.isEmpty else { state = .failed; return }
        index = min(max(index, 0), cards.count - 1)
        updateState()
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
