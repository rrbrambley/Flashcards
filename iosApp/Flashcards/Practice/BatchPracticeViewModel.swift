import Shared
import SwiftUI

/// What the grade-at-the-end batch screen shows (mapped from the shared `BatchPracticeUiState`, #293).
enum BatchPracticeState {
    case loading
    case answering(cards: [Flashcard], mode: String)
    case completed(numCorrect: Int, numIncorrect: Int)
    case failed
}

/// Thin iOS adapter over the shared `BatchPracticeController` (#293): builds it for the entry, mirrors
/// its `state` into `@Published` (mapping the shared state to the Swift `BatchPracticeState` the view
/// switches on), and forwards Submit. The recap fields (`streak`, `review`) are lifted out of the
/// shared `Completed` state so the shared `CompletionView` can be reused for the results screen (#298).
@MainActor
final class BatchPracticeViewModel: ObservableObject {
    @Published private(set) var state: BatchPracticeState = .loading
    /// Overall practice streak after this completion (FLA-106); nil until read / 0 = no streak.
    @Published private(set) var streak: Int?
    /// Per-card recap of the run (FLA-149); populated after submit.
    @Published private(set) var review: [ReviewItem] = []

    private let controller: BatchPracticeController
    private var stateTask: Task<Void, Never>?

    init(
        flashcardRepository: FlashcardRepository,
        sessionRepository: PracticeSessionRepository,
        entry: PracticeEntry,
        apiClient: FlashcardApiClient
    ) {
        controller = BridgingKt.createBatchPracticeController(
            flashcardRepository: flashcardRepository,
            sessionRepository: sessionRepository,
            apiClient: apiClient,
            entry: entry.shared
        )
    }

    /// The deck being practiced (id), for the Share action; nil until loaded.
    var shareDeckId: Int64? { controller.deckId?.int64Value }

    func start() async {
        let c = controller
        stateTask = Task { [weak self] in
            for await state in asyncStream(c.batchStateAdapter()) {
                if let state { self?.apply(state) }
            }
        }
        try? await c.start()
    }

    /// Stops observing + tears down the controller — call when the batch screen goes away.
    func stopObserving() {
        stateTask?.cancel()
        controller.close()
    }

    /// Grades the whole set (#293): `answers` aligns with the answering card list — the typed text
    /// (Test) or the chosen option's text (Multiple Choice) per card, nil when left unanswered.
    func submit(_ answers: [String?]) {
        controller.submit(answers: answers)
    }

    private func apply(_ shared: BatchPracticeUiState) {
        switch shared {
        case let answering as BatchPracticeUiState.Answering:
            state = .answering(cards: (answering.cards as? [Flashcard]) ?? [], mode: answering.mode)
        case let done as BatchPracticeUiState.Completed:
            streak = done.streak.map { Int($0.intValue) }
            review = (done.review as? [ReviewItem]) ?? []
            state = .completed(numCorrect: Int(done.numCorrect), numIncorrect: Int(done.numIncorrect))
        case is BatchPracticeUiState.Failed:
            state = .failed
        default:
            state = .loading
        }
    }
}
