import Shared
import SwiftUI

/// What the practice screen shows (mapped from the shared `PracticeUiState`, FLA-197).
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
        discussionsEnabled: Bool,
        isGlobal: Bool,
        streak: Int
    )
    case completed(numCorrect: Int, numIncorrect: Int)
    case failed
}

/// How a practice run is launched (maps to the shared `PracticeEntry`).
enum PracticeEntry {
    /// Start or resume a session for a deck in a given mode + shuffle + question-count choice (Library
    /// "Practice"). `questionCount` is a subset of the deck (FLA-219); nil = the whole deck.
    case deck(Int64, mode: String, shuffle: Bool, questionCount: Int32?)
    /// Resume an existing session (Home "Continue practice"); the mode + order come from the session.
    case session(Int64)
    /// Guest mode (FLA-104): practice a public catalog deck in memory — no session, no persistence.
    case guestDeck(Int64, mode: String)

    var shared: Shared.PracticeEntry {
        switch self {
        // Kotlin default args don't bridge, so shuffle + questionCount are always passed (FLA-200/219).
        case let .deck(id, mode, shuffle, questionCount):
            Shared.PracticeEntry.Deck(
                deckId: id, mode: mode, shuffle: shuffle,
                questionCount: questionCount.map { KotlinInt(int: $0) }
            )
        case let .session(id): Shared.PracticeEntry.Session(sessionId: id)
        // Guest quick-practice has no config picker; keep the saved order + whole deck.
        case let .guestDeck(id, mode):
            Shared.PracticeEntry.GuestDeck(deckId: id, mode: mode, shuffle: false, questionCount: nil)
        }
    }
}

/// State of the guest "create an account to save your progress" flow (FLA-104).
enum GuestSaveState: Equatable {
    case idle
    case saving
    case error(String)
    case saved
}

/// The shared per-card review row (FLA-149) is adopted directly; add the SwiftUI `Identifiable` id.
extension ReviewItem: @retroactive Identifiable {
    public var id: String { answerUid }
}

/// Thin iOS adapter over the shared `PracticeSessionController` (FLA-197): builds it for the entry,
/// mirrors its `state`/`saveState` into `@Published` (mapping the shared state to the Swift `PracticeState`
/// the view switches on), and delegates the runner actions. The `discussions` feature flag stays in the
/// view (`featureFlagStore.discussionsVisible`), so the controller's raw opt-in flows straight through.
@MainActor
final class PracticeViewModel: ObservableObject {
    @Published private(set) var state: PracticeState = .loading
    @Published private(set) var saveState: GuestSaveState = .idle
    /// Overall practice streak after this completion (FLA-106); nil until read / 0 = no streak.
    @Published private(set) var streak: Int?
    /// Per-card recap of the run (FLA-149); populated after completion.
    @Published private(set) var review: [ReviewItem] = []

    private let controller: PracticeSessionController
    private var stateTask: Task<Void, Never>?
    private var saveTask: Task<Void, Never>?

    init(
        flashcardRepository: FlashcardRepository,
        sessionRepository: PracticeSessionRepository,
        entry: PracticeEntry,
        apiClient: FlashcardApiClient,
        authService: AuthService? = nil
    ) {
        controller = BridgingKt.createPracticeSessionController(
            flashcardRepository: flashcardRepository,
            sessionRepository: sessionRepository,
            apiClient: apiClient,
            authService: authService,
            entry: entry.shared
        )
    }

    /// The deck being practiced (id), for the Share action; nil until loaded.
    var shareDeckId: Int64? { controller.deckId?.int64Value }

    /// Whether this run is a signed-out guest (gates the discussion post → sign-in conversion).
    var isGuestMode: Bool { controller.isGuest }

    /// Whether leaving now should prompt a guest to save: guest, mid-session, with some progress.
    var shouldPromptSave: Bool { controller.shouldPromptSave }

    func start() async {
        let c = controller
        stateTask = Task { [weak self] in
            for await state in asyncStream(c.stateAdapter()) {
                if let state { self?.apply(state) }
            }
        }
        saveTask = Task { [weak self] in
            for await save in asyncStream(c.saveStateAdapter()) {
                if let save { self?.applySave(save) }
            }
        }
        try? await c.start()
    }

    /// Stops observing + tears down the controller — call when the practice screen goes away.
    func stopObserving() {
        stateTask?.cancel()
        saveTask?.cancel()
        controller.close()
    }

    func onResult(correct: Bool, submittedText: String? = nil) {
        controller.onResult(correct: correct, submittedText: submittedText)
    }

    func applyResult(correct: Bool, submittedText: String? = nil) {
        controller.applyResult(correct: correct, submittedText: submittedText)
    }

    func goBack() { controller.goBack() }

    func goForward() { controller.goForward() }

    func saveProgressByCreatingAccount(email: String, password: String) async {
        try? await controller.saveProgressByCreatingAccount(email: email, password: password)
    }

    private func apply(_ shared: PracticeUiState) {
        switch shared {
        case let show as PracticeUiState.ShowCard:
            state = .showCard(
                card: show.card,
                position: Int(show.position),
                numCorrect: Int(show.numCorrect),
                numIncorrect: Int(show.numIncorrect),
                canGoBack: show.canGoBack,
                mode: show.mode,
                deck: (show.deck as? [Flashcard]) ?? [],
                discussionsEnabled: show.discussionsEnabled,
                isGlobal: show.isGlobal,
                streak: Int(show.streak)
            )
        case let done as PracticeUiState.Completed:
            streak = done.streak.map { Int($0.intValue) }
            review = (done.review as? [ReviewItem]) ?? []
            state = .completed(numCorrect: Int(done.numCorrect), numIncorrect: Int(done.numIncorrect))
        case is PracticeUiState.Failed:
            state = .failed
        default:
            state = .loading
        }
    }

    private func applySave(_ shared: Shared.GuestSaveState) {
        switch shared {
        case is Shared.GuestSaveState.Saving: saveState = .saving
        case let error as Shared.GuestSaveState.Error: saveState = .error(error.message)
        case is Shared.GuestSaveState.Saved: saveState = .saved
        default: saveState = .idle
        }
    }
}
