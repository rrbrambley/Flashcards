import Shared
import SwiftUI

/// What the practice screen shows (mirrors Android's `FlashcardsUiState`).
enum PracticeState {
    case loading
    case showCard(
        card: Flashcard,
        position: Int,
        numCorrect: Int,
        numIncorrect: Int,
        canGoBack: Bool,
        mode: String,
        deck: [Flashcard],
        discussionsEnabled: Bool
    )
    case completed(numCorrect: Int, numIncorrect: Int)
    case failed
}

/// How a practice run is launched.
enum PracticeEntry {
    /// Start or resume a session for a deck in a given mode (Library "Practice" + Home "Practice").
    case deck(Int64, mode: String)
    /// Resume an existing session (Home "Continue practice"); the mode comes from the session.
    case session(Int64)
    /// Guest mode (FLA-104): practice a public catalog deck in memory — no session, no persistence.
    case guestDeck(Int64, mode: String)
}

/// State of the guest "create an account to save your progress" flow (FLA-104).
enum GuestSaveState: Equatable {
    case idle
    case saving
    case error(String)
    case saved
}

/// Drives a practice run: starts-or-resumes (or restores) a session, restores progress, and
/// persists each step. In guest mode it runs entirely in memory (no session) and offers to save the
/// session by creating an account. Swipe right = correct, left = needs practice.
@MainActor
final class PracticeViewModel: ObservableObject {
    @Published private(set) var state: PracticeState = .loading
    @Published private(set) var saveState: GuestSaveState = .idle
    /// Overall practice streak after this completion (FLA-106); nil until read / 0 = no streak.
    @Published private(set) var streak: Int?

    private let flashcardRepository: FlashcardRepository
    private let sessionRepository: PracticeSessionRepository
    private let apiClient: FlashcardApiClient?
    private let authService: AuthenticationService?
    private let entry: PracticeEntry

    private var sessionId: Int64?
    private var deckId: Int64?
    private(set) var deckTitle = ""
    private var isGuest = false
    private var cards: [Flashcard] = []
    private var index = 0
    private var numCorrect = 0
    private var numIncorrect = 0
    private var mode = "flashcards"
    private var discussionsEnabled = false

    init(
        flashcardRepository: FlashcardRepository,
        sessionRepository: PracticeSessionRepository,
        entry: PracticeEntry,
        apiClient: FlashcardApiClient? = nil,
        authService: AuthenticationService? = nil
    ) {
        self.flashcardRepository = flashcardRepository
        self.sessionRepository = sessionRepository
        self.entry = entry
        self.apiClient = apiClient
        self.authService = authService
    }

    /// The deck being practiced (id + title), for the Share action; nil until loaded.
    var shareDeckId: Int64? { deckId }

    /// Whether this run is a signed-out guest (gates the discussion post → sign-in conversion).
    var isGuestMode: Bool { isGuest }

    /// Whether leaving now should prompt a guest to save: guest, mid-session, with some progress.
    var shouldPromptSave: Bool {
        guard isGuest, case .showCard = state else { return false }
        return index > 0 || numCorrect > 0 || numIncorrect > 0
    }

    func start() async {
        switch entry {
        case let .deck(deckId, mode):
            // The backend keys start-or-resume on (user, deck, mode); restore then reads back the
            // session's mode as the source of truth.
            guard let started = try? await sessionRepository.startOrResumeSession(deckId: deckId, mode: mode)
            else {
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
        case let .guestDeck(deckId, mode):
            isGuest = true
            self.deckId = deckId
            self.mode = mode
            await loadGuestDeckCards(deckId: deckId)
        }
    }

    /// Records the outcome for the current card and advances. Used by every mode — a Classic swipe,
    /// or Test/Multiple-Choice after the answer is graded.
    func onResult(correct: Bool) {
        if correct { numCorrect += 1 } else { numIncorrect += 1 }
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

    /// Guest "save my progress": create an account, then create a server session and push the current
    /// progress so it's resumable. On success the token store flips the app to the signed-in state.
    func saveProgressByCreatingAccount(email: String, password: String) async {
        guard let apiClient, let authService, let deckId else { return }
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !password.isEmpty else {
            saveState = .error("Enter your email and password.")
            return
        }
        saveState = .saving
        let result = try? await authService.register(email: trimmed, password: password)
        if let failure = result as? AuthResult.Failure {
            saveState = .error(failure.message)
            return
        }
        guard result is AuthResult.Success else {
            saveState = .error("Something went wrong. Check your connection and try again.")
            return
        }
        // Best-effort: the account exists either way; push the in-progress session if we can.
        let request = UpdateProgressRequest(
            currentCardIndex: Int32(index),
            numCorrect: Int32(numCorrect),
            numIncorrect: Int32(numIncorrect)
        )
        if let session = try? await apiClient.createSession(deckId: deckId, mode: mode) {
            _ = try? await apiClient.updateProgress(sessionId: session.id, request: request)
        }
        saveState = .saved
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
            mode = session.mode
            deckId = session.deckId
            deckTitle = session.deckTitle
            return session.deckId
        }
        return nil
    }

    private func loadDeckCards(deckId: Int64) async {
        for await deck in asyncStream(BridgingKt.flashcardDeckAdapter(flashcardRepository, deckId: deckId)) {
            guard let deck else { continue }
            cards = (deck.flashcards as? [Flashcard]) ?? []
            // The discussions flag travels with the cached deck (FLA-123), so it works offline too.
            discussionsEnabled = deck.discussionsEnabled
            break
        }
        guard !cards.isEmpty else { state = .failed; return }
        index = min(max(index, 0), cards.count - 1)
        updateState()
    }

    /// Loads a public catalog deck's cards directly from the API (guest mode — no repository/session).
    private func loadGuestDeckCards(deckId: Int64) async {
        guard let apiClient, let deck = try? await apiClient.getCatalogDeck(deckId: deckId) else {
            state = .failed
            return
        }
        deckTitle = deck.title
        discussionsEnabled = deck.discussionsEnabled
        cards = ((deck.flashcards as? [FlashcardDto]) ?? []).map {
            Flashcard(
                question: $0.question,
                answer: $0.answer,
                imageUrl: $0.imageUrl,
                alternativeAnswers: $0.alternativeAnswers,
                cardUid: $0.cardUid
            )
        }
        guard !cards.isEmpty else { state = .failed; return }
        index = 0
        updateState()
    }

    private func updateState() {
        guard !cards.isEmpty else { state = .failed; return }
        state = .showCard(
            card: cards[index],
            position: index,
            numCorrect: numCorrect,
            numIncorrect: numIncorrect,
            canGoBack: index > 0,
            mode: mode,
            deck: cards,
            discussionsEnabled: discussionsEnabled
        )
    }

    private func persist() {
        guard let sid = sessionId else { return } // guests have no session
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
        Task {
            try? await sessionRepository.completeSession(sessionId: sid)
            // Read the overall streak only after the completion lands, so it reflects the day just
            // earned. Best-effort: a failure (or no streak) just leaves the badge hidden.
            guard let apiClient else { return }
            if let result = try? await apiClient.getStreaks(tz: TimeZone.current.identifier) {
                streak = Int(result.overall.current)
            }
        }
    }
}
