import Shared
import SwiftUI

/// The mode-agnostic practice runner (parity with Android's FlashcardsScreen). The toolbar + score
/// row + loading/completion states are shared; each card is rendered by the per-mode view chosen
/// from the session's mode (Classic flip / Test text-entry / Multiple Choice). The view model owns
/// the session loop — index, score, persistence, completion — and every mode reports its outcome via
/// `onResult(correct:)`. Presented as a full-screen cover; closes when done.
struct PracticeView: View {
    @StateObject private var viewModel: PracticeViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showHelp = false
    @State private var showSavePrompt = false
    /// The card whose discussion sheet is open (its cardUid), or nil when closed (FLA-123).
    @State private var discussionTarget: DiscussionTarget?

    // Kept so the discussion sheet can post over the shared client (and convert a guest to sign in).
    private let apiClient: FlashcardApiClient?
    private let authService: AuthService?
    // Gates the discuss affordance behind the `discussions` kill switch (FLA-185); guests bypass it.
    private let featureFlagStore: FeatureFlagStore

    init(
        flashcardRepository: FlashcardRepository,
        sessionRepository: PracticeSessionRepository,
        entry: PracticeEntry,
        featureFlagStore: FeatureFlagStore,
        apiClient: FlashcardApiClient? = nil,
        authService: AuthService? = nil
    ) {
        self.apiClient = apiClient
        self.authService = authService
        self.featureFlagStore = featureFlagStore
        _viewModel = StateObject(
            wrappedValue: PracticeViewModel(
                flashcardRepository: flashcardRepository,
                sessionRepository: sessionRepository,
                entry: entry,
                apiClient: apiClient,
                authService: authService
            )
        )
    }

    /// The help copy explains flip/swipe, so it's only offered in Classic mode.
    private var isClassic: Bool {
        if case let .showCard(_, _, _, _, _, mode, _, _, _, _) = viewModel.state {
            return PracticeMode.companion.fromKey(key: mode) == .classic
        }
        return true
    }

    var body: some View {
        NavigationStack {
            content
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") { handleClose() }
                    }
                    ToolbarItemGroup(placement: .topBarTrailing) {
                        if let deckId = viewModel.shareDeckId, let url = shareURL(deckId) {
                            ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }
                                .accessibilityLabel("Share deck")
                        }
                        if isClassic {
                            Button { showHelp = true } label: { Image(systemName: "questionmark.circle") }
                                .accessibilityLabel("How to practice")
                        }
                    }
                }
                .alert("How to practice", isPresented: $showHelp) {
                    Button("Got it", role: .cancel) {}
                } message: {
                    Text("Tap a card to flip it.\nSwipe right if you got it, left if you need more practice.")
                }
                .sheet(isPresented: $showSavePrompt) {
                    GuestSavePromptView(viewModel: viewModel, onLeave: { dismiss() }, onCancel: { showSavePrompt = false })
                }
                .sheet(item: $discussionTarget) { target in
                    if let apiClient {
                        DiscussionView(
                            cardUid: target.id,
                            isGuest: viewModel.isGuestMode,
                            apiClient: apiClient,
                            authService: authService
                        )
                    }
                }
                .onChange(of: viewModel.saveState) { _, newValue in
                    // A successful save signs the user in; leave the practice screen so RootView swaps
                    // to the main tabs (the saved session shows under "Continue studying").
                    if newValue == .saved { dismiss() }
                }
                .task { await viewModel.start() }
                .onDisappear { viewModel.stopObserving() }
        }
    }

    /// Guests with progress get the save prompt before leaving; everyone else just dismisses.
    private func handleClose() {
        if viewModel.shouldPromptSave { showSavePrompt = true } else { dismiss() }
    }

    private func shareURL(_ deckId: Int64) -> URL? {
        URL(string: "\(AppConfig.webAppBaseURL)/decks/\(deckId)/practice")
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingView()
        case let .showCard(card, position, numCorrect, numIncorrect, canGoBack, mode, deck, discussionsEnabled, isGlobal, streak):
            VStack(spacing: Spacing.lg) {
                ScoreRow(numIncorrect: numIncorrect, numCorrect: numCorrect)
                // Live in-session streak (FLA-99): appears at 2+ in a row, milestone emphasis at 5+.
                if streak >= 2 {
                    SessionStreakBadge(streak: streak)
                }
                modeView(
                    mode: mode,
                    card: card,
                    deck: deck,
                    canGoBack: canGoBack,
                    discussionsEnabled: discussionsEnabled,
                    isGlobal: isGlobal
                )
                // Re-init the per-card view (flip / two-phase Test+MC state) on advance.
                .id(position)
                .frame(maxHeight: .infinity)
            }
            .padding(Spacing.lg)
        case let .completed(numCorrect, numIncorrect):
            CompletionView(
                numCorrect: numCorrect,
                numIncorrect: numIncorrect,
                streak: viewModel.streak,
                review: viewModel.review
            ) { dismiss() }
        case .failed:
            ContentUnavailableView {
                Label("Couldn't start practice", systemImage: "exclamationmark.triangle")
            } description: {
                Text("This deck has no cards, or we couldn't reach the server.")
            } actions: {
                Button("Close") { dismiss() }.buttonStyle(.borderedProminent)
            }
        }
    }

    @ViewBuilder
    private func modeView(
        mode: String,
        card: Flashcard,
        deck: [Flashcard],
        canGoBack: Bool,
        discussionsEnabled: Bool,
        isGlobal: Bool
    ) -> some View {
        // Opens the discussion sheet for the current card once its answer is revealed.
        let onDiscuss = { discussionTarget = DiscussionTarget(id: card.cardUid) }
        // Kill switch (FLA-185): hide discuss for signed-in users when `discussions` is off; guests keep it.
        let showDiscuss = featureFlagStore.discussionsVisible(
            deckEnabled: discussionsEnabled,
            isGuest: viewModel.isGuestMode
        )
        // Bridged Kotlin enum is a non-final class, so the switch needs a `default` (→ Classic).
        switch PracticeMode.companion.fromKey(key: mode) {
        case .test:
            TestModeView(
                card: card,
                onGraded: viewModel.applyResult,
                onAdvance: viewModel.goForward,
                discussionsEnabled: showDiscuss,
                onDiscuss: onDiscuss,
                canSuggest: isGlobal,
                isGuest: viewModel.isGuestMode,
                apiClient: apiClient,
                authService: authService
            )
        case .multiplechoice:
            MultipleChoiceModeView(
                card: card,
                deck: deck,
                onGraded: viewModel.applyResult,
                onAdvance: viewModel.goForward,
                discussionsEnabled: showDiscuss,
                onDiscuss: onDiscuss
            )
        default:
            ClassicModeView(
                card: card,
                canGoBack: canGoBack,
                onResult: viewModel.onResult,
                onPrevious: viewModel.goBack,
                onNext: viewModel.goForward,
                discussionsEnabled: showDiscuss,
                onDiscuss: onDiscuss
            )
        }
    }
}

/// Identifies the card whose discussion sheet is open (its `cardUid`), for `.sheet(item:)` (FLA-123).
private struct DiscussionTarget: Identifiable {
    let id: String
}

/// Running score: needs-practice (red) on the left, correct (green) on the right.
private struct ScoreRow: View {
    let numIncorrect: Int
    let numCorrect: Int

    var body: some View {
        HStack {
            chip(numIncorrect, color: .red, icon: "xmark")
            Spacer()
            chip(numCorrect, color: .green, icon: "checkmark")
        }
    }

    private func chip(_ value: Int, color: Color, icon: String) -> some View {
        Label("\(value)", systemImage: icon)
            .font(.headline)
            .foregroundStyle(color)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .background(color.opacity(0.12), in: Capsule())
    }
}

/// The card's question text + optional image — the prompt shared by the Test + Multiple-Choice modes.
struct CardPrompt: View {
    let card: Flashcard

    var body: some View {
        VStack(spacing: Spacing.md) {
            if let imageUrl = card.imageUrl, !imageUrl.isEmpty {
                RemoteCardImage(url: imageUrl)
                    .frame(maxHeight: 220)
            }
            if !card.question.isEmpty {
                Text(card.question)
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
            }
        }
    }
}

/// Live in-session answer-streak pill (FLA-99) — warm like the daily streak, bolder at the 5+ milestone.
private struct SessionStreakBadge: View {
    let streak: Int

    var body: some View {
        let hot = streak >= 5
        // SF Symbol (not the 🔥 emoji) so it always renders + takes the warm tint.
        Label("\(streak) in a row", systemImage: "flame.fill")
            .font(hot ? .subheadline.bold() : .caption.weight(.semibold))
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .background(Color.orange.opacity(hot ? 0.28 : 0.15), in: Capsule())
            .foregroundStyle(hot ? Color(red: 0.6, green: 0.21, blue: 0.05) : .orange)
    }
}

/// End-of-deck summary + the per-card recap of the run (FLA-149).
private struct CompletionView: View {
    let numCorrect: Int
    let numIncorrect: Int
    let streak: Int?
    let review: [ReviewItem]
    let onDone: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(.green)
                    .accessibilityHidden(true)
                Text("Practice complete")
                    .font(.title.bold())
                HStack(spacing: Spacing.xl) {
                    stat("Correct", numCorrect, color: .green)
                    stat("To review", numIncorrect, color: .red)
                }
                if let streak, streak > 0 {
                    StreakBadge(streak: streak)
                }
                if !review.isEmpty {
                    ReviewList(items: review)
                }
                Button("Done", action: onDone)
                    .buttonStyle(.primary)
                    .padding(.horizontal, Spacing.xl)
                    .padding(.top, Spacing.md)
            }
            .padding(Spacing.lg)
        }
    }

    private func stat(_ label: String, _ value: Int, color: Color) -> some View {
        VStack {
            Text("\(value)").font(.largeTitle.bold()).foregroundStyle(color)
            Text(label).font(.subheadline).foregroundStyle(.secondary)
        }
    }
}

/// The per-card recap list: each graded answer in play order, joined to its card.
private struct ReviewList: View {
    let items: [ReviewItem]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("Review")
                .font(.headline)
                .frame(maxWidth: .infinity, alignment: .leading)
            ForEach(items) { item in
                ReviewRow(item: item)
            }
        }
        .padding(.top, Spacing.md)
    }
}

/// One recap row: outcome + (image) + prompt + correct answer + the submitted text (Test/MC).
private struct ReviewRow: View {
    let item: ReviewItem

    var body: some View {
        HStack(spacing: Spacing.md) {
            OutcomeBadge(correct: item.correct)
            if let url = item.imageUrl, !url.isEmpty {
                RemoteCardImage(url: url).frame(width: 44, height: 44)
            }
            VStack(alignment: .leading, spacing: 2) {
                if !item.question.isEmpty {
                    Text(item.question).font(.body.weight(.semibold))
                }
                Text(item.answer).font(.subheadline).foregroundStyle(.secondary)
                if let submitted = item.submittedText, !submitted.isEmpty {
                    Text("You answered: \(submitted)")
                        .font(.caption)
                        .foregroundStyle(Color(red: 0.6, green: 0.21, blue: 0.05))
                }
            }
            Spacer(minLength: 0)
        }
        .padding(Spacing.md)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }
}

/// ✓ / ✗ outcome chip for a review row.
private struct OutcomeBadge: View {
    let correct: Bool

    var body: some View {
        Image(systemName: correct ? "checkmark" : "xmark")
            .font(.caption.bold())
            .foregroundStyle(.white)
            .frame(width: 26, height: 26)
            .background(correct ? Color.green : Color.red, in: Circle())
            .accessibilityLabel(correct ? "Correct" : "Incorrect")
    }
}
