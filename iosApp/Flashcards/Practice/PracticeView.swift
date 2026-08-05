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
    /// Confirm-before-leaving a single-sitting run in progress (#307).
    @State private var showLeaveConfirm = false
    /// The card whose discussion sheet is open (its cardUid), or nil when closed (FLA-123).
    @State private var discussionTarget: DiscussionTarget?

    /// Whether this run is single-sitting (timed / grade-at-the-end, #306) — hides the casual exit +
    /// warns on leaving while in progress (#307). Resolved by the runner before this view is built.
    private let singleSitting: Bool

    // Kept so the discussion sheet can post over the shared client (and convert a guest to sign in).
    private let apiClient: FlashcardApiClient
    private let authService: AuthService?
    // Gates the discuss affordance behind the `discussions` kill switch (FLA-185); guests bypass it.
    private let featureFlagStore: FeatureFlagStore

    init(
        flashcardRepository: FlashcardRepository,
        sessionRepository: PracticeSessionRepository,
        entry: PracticeEntry,
        featureFlagStore: FeatureFlagStore,
        apiClient: FlashcardApiClient,
        singleSitting: Bool = false,
        authService: AuthService? = nil
    ) {
        self.apiClient = apiClient
        self.authService = authService
        self.featureFlagStore = featureFlagStore
        self.singleSitting = singleSitting
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

    /// The exit guard is live only while a single-sitting run is still showing cards (#307): once
    /// completed/failed there's nothing to lose, so Close leaves normally.
    private var guardActive: Bool {
        if case .showCard = viewModel.state { return singleSitting }
        return false
    }

    var body: some View {
        NavigationStack {
            content
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        // A fullScreenCover has no swipe-to-dismiss, so Close is the only exit; while a
                        // single-sitting run is in progress it routes through a confirm (#307) — no
                        // casual exit, but not a trap either (iOS has no system-back like Android).
                        Button("Close") { handleClose() }
                    }
                    ToolbarItemGroup(placement: .topBarTrailing) {
                        if let deckId = viewModel.shareDeckId, let url = shareURL(deckId) {
                            ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }
                                .accessibilityLabel(Text("Share deck"))
                        }
                        if isClassic {
                            Button { showHelp = true } label: { Image(systemName: "questionmark.circle") }
                                .accessibilityLabel(Text("How to practice"))
                        }
                    }
                }
                .alert("How to practice", isPresented: $showHelp) {
                    Button("Got it", role: .cancel) {}
                } message: {
                    Text("Tap a card to flip it.\nSwipe right if you got it, left if you need more practice.")
                }
                .alert("Leave this session?", isPresented: $showLeaveConfirm) {
                    Button("Leave", role: .destructive) { dismiss() }
                    Button("Keep practicing", role: .cancel) {}
                } message: {
                    Text("This session runs in one sitting — if you leave, your progress won't be saved.")
                }
                .sheet(isPresented: $showSavePrompt) {
                    GuestSavePromptView(viewModel: viewModel, onLeave: { dismiss() }, onCancel: { showSavePrompt = false })
                }
                .sheet(item: $discussionTarget) { target in
                    DiscussionView(
                        cardUid: target.id,
                        isGuest: viewModel.isGuestMode,
                        apiClient: apiClient,
                        authService: authService
                    )
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

    /// Single-sitting runs in progress confirm first (#307); guests with progress get the save prompt;
    /// everyone else just dismisses.
    private func handleClose() {
        if guardActive {
            showLeaveConfirm = true
        } else if viewModel.shouldPromptSave {
            showSavePrompt = true
        } else {
            dismiss()
        }
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
                // Timed countdown (#289): a m:ss chip, urgent styling in the final 10s.
                if let remaining = viewModel.remainingSeconds {
                    TimerChip(remainingSeconds: remaining)
                }
                // Live in-session streak (FLA-99): appears at 2+ in a row, milestone emphasis at 5+.
                if InSessionStreak.shared.showsBadge(streak: Int32(streak)) {
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
                review: viewModel.review,
                // Offer "this should be correct" on wrong Test answers of a global deck on the
                // card-by-card completion recap too, not just grade-at-the-end (#361).
                suggestion: ReviewSuggestion(
                    mode: viewModel.mode,
                    isGlobal: viewModel.isGlobal,
                    isGuest: viewModel.isGuestMode,
                    apiClient: apiClient,
                    authService: authService
                )
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
        // Pause the countdown while this card's prompt image loads (#314); no-op for untimed runs.
        let onImageReadyChanged = { (ready: Bool) in viewModel.setPromptImageReady(ready) }
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
                authService: authService,
                onImageReadyChanged: onImageReadyChanged
            )
        case .multiplechoice:
            MultipleChoiceModeView(
                card: card,
                deck: deck,
                onGraded: viewModel.applyResult,
                onAdvance: viewModel.goForward,
                discussionsEnabled: showDiscuss,
                onDiscuss: onDiscuss,
                onImageReadyChanged: onImageReadyChanged
            )
        default:
            ClassicModeView(
                card: card,
                canGoBack: canGoBack,
                onResult: viewModel.onResult,
                onPrevious: viewModel.goBack,
                onNext: viewModel.goForward,
                discussionsEnabled: showDiscuss,
                onDiscuss: onDiscuss,
                onImageReadyChanged: onImageReadyChanged
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
    /// Reports prompt-image readiness for the timed-run pause (#314): `false` while an image is still
    /// loading (pause the countdown), `true` once it settles or when there's no image (resume). Default
    /// no-op so non-timed / non-runner callers need no change.
    var onImageReadyChanged: (Bool) -> Void = { _ in }

    private var hasImage: Bool { card.imageUrl.map { !$0.isEmpty } ?? false }
    @State private var imageLoaded = false

    var body: some View {
        VStack(spacing: Spacing.md) {
            if let imageUrl = card.imageUrl, !imageUrl.isEmpty {
                RemoteCardImage(url: imageUrl, onReady: { imageLoaded = true })
                    .frame(maxHeight: 220)
            }
            if !card.question.isEmpty {
                Text(card.question)
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
            }
        }
        // Report the *reconciled* ready state (ready = no image, or the image has settled) on appear and
        // on every change — so the final report always reflects reality. Reporting edges instead would
        // race: a fast/cached image's onReady (resume) can fire before the view's appear (pause), which
        // would then strand the countdown paused (#314).
        .onChange(of: imageLoaded, initial: true) { _, _ in
            onImageReadyChanged(!hasImage || imageLoaded)
        }
    }
}

/// Live in-session answer-streak pill (FLA-99) — warm like the daily streak, bolder at the 5+ milestone.
private struct SessionStreakBadge: View {
    let streak: Int

    var body: some View {
        let hot = InSessionStreak.shared.isHot(streak: Int32(streak))
        // SF Symbol (not the 🔥 emoji) so it always renders + takes the warm tint.
        Label("\(streak) in a row", systemImage: "flame.fill")
            .font(hot ? .subheadline.bold() : .caption.weight(.semibold))
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .background(Color.orange.opacity(hot ? 0.28 : 0.15), in: Capsule())
            .foregroundStyle(hot ? Color(red: 0.6, green: 0.21, blue: 0.05) : .orange)
    }
}

/// Formats remaining seconds as m:ss (e.g. 90 → "1:30", 5 → "0:05"). Mirrors Android's `formatMinSec`.
func formatMinSec(_ totalSeconds: Int) -> String {
    let secs = max(0, totalSeconds)
    return "\(secs / 60):\(String(format: "%02d", secs % 60))"
}

/// Live timed-session countdown (#289): a m:ss pill, red in the final 10s. Shared by the card-by-card
/// and grade-at-the-end runners.
struct TimerChip: View {
    let remainingSeconds: Int

    var body: some View {
        let urgent = remainingSeconds <= 10
        Text("\(formatMinSec(remainingSeconds)) left")
            .font(.subheadline.bold())
            .foregroundStyle(urgent ? Color.white : Color.primary)
            .padding(.horizontal, 14)
            .padding(.vertical, 5)
            .background(
                urgent ? Color(red: 0.83, green: 0.24, blue: 0.24) : Color(.tertiarySystemFill),
                in: Capsule()
            )
            .accessibilityLabel(Text("\(formatMinSec(remainingSeconds)) remaining"))
    }
}

/// Context for offering the Test-mode "this should be correct" suggestion from the recap (#338): a
/// wrong Test answer with a typed response, on a global-deck card. Nil for the card-by-card recap
/// (it offers the suggestion inline during the run).
struct ReviewSuggestion {
    let mode: String
    let isGlobal: Bool
    let isGuest: Bool
    let apiClient: FlashcardApiClient
    let authService: AuthService?

    func canSuggest(_ item: ReviewItem) -> Bool {
        mode == PracticeMode.test.key &&
            isGlobal &&
            !item.correct &&
            !item.cardUid.isEmpty &&
            !(item.submittedText ?? "").isEmpty
    }
}

/// End-of-deck summary + the per-card recap of the run (FLA-149). Shared by the card-by-card runner
/// and the grade-at-the-end batch runner (#293/#298).
struct CompletionView: View {
    let numCorrect: Int
    let numIncorrect: Int
    let streak: Int?
    let review: [ReviewItem]
    /// When set (grade-at-the-end, #338), long-pressing a wrong Test row offers the suggestion.
    var suggestion: ReviewSuggestion? = nil
    let onDone: () -> Void

    @State private var suggestTarget: ReviewItem?

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
                    ReviewList(items: review, suggestion: suggestion, onSuggest: { suggestTarget = $0 })
                }
                Button("Done", action: onDone)
                    .buttonStyle(.primary)
                    .padding(.horizontal, Spacing.xl)
                    .padding(.top, Spacing.md)
            }
            .padding(Spacing.lg)
        }
        .sheet(item: $suggestTarget) { item in
            if let suggestion {
                SuggestAnswerView(
                    cardUid: item.cardUid,
                    suggestedAnswer: item.submittedText ?? "",
                    isGuest: suggestion.isGuest,
                    apiClient: suggestion.apiClient,
                    authService: suggestion.authService
                )
            }
        }
    }

    private func stat(_ label: LocalizedStringKey, _ value: Int, color: Color) -> some View {
        VStack {
            Text("\(value)").font(.largeTitle.bold()).foregroundStyle(color)
            Text(label).font(.subheadline).foregroundStyle(.secondary)
        }
    }
}

/// The per-card recap list: each graded answer in play order, joined to its card.
private struct ReviewList: View {
    let items: [ReviewItem]
    var suggestion: ReviewSuggestion?
    var onSuggest: (ReviewItem) -> Void = { _ in }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("Review")
                .font(.headline)
                .frame(maxWidth: .infinity, alignment: .leading)
            ForEach(items) { item in
                ReviewRow(
                    item: item,
                    canSuggest: suggestion?.canSuggest(item) ?? false,
                    onSuggest: { onSuggest(item) }
                )
            }
        }
        .padding(.top, Spacing.md)
    }
}

/// One recap row: outcome + (image) + prompt + correct answer + the submitted text (Test/MC).
/// When `canSuggest` (a wrong Test answer on a global deck, #338), long-pressing offers the
/// "this should be correct" suggestion via a context menu.
private struct ReviewRow: View {
    let item: ReviewItem
    var canSuggest = false
    var onSuggest: () -> Void = {}

    var body: some View {
        row
            .padding(Spacing.md)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
            .modifier(SuggestContextMenu(enabled: canSuggest, onSuggest: onSuggest))
    }

    private var row: some View {
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
    }
}

/// Attaches the "this should be correct" long-press menu only when eligible — an empty
/// `.contextMenu` would still capture the long-press but show nothing, so gate the whole modifier.
private struct SuggestContextMenu: ViewModifier {
    let enabled: Bool
    let onSuggest: () -> Void

    func body(content: Content) -> some View {
        if enabled {
            content.contextMenu {
                Button(action: onSuggest) {
                    Label("This should be correct", systemImage: "checkmark.circle")
                }
            }
        } else {
            content
        }
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
