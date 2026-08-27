import Shared
import SwiftUI

/// The "grade at the end" runner (#293): every card in one scrollable list to answer in any order,
/// then a Submit that grades the whole set and lands on the shared `CompletionView` recap (#298).
/// Test / Multiple Choice only. Presented (like `PracticeView`) as a full-screen cover.
struct BatchPracticeView: View {
    @StateObject private var viewModel: BatchPracticeViewModel
    @Environment(\.dismiss) private var dismiss
    /// Confirm-before-leaving while answering — a batch is single-sitting (#306/#307).
    @State private var showLeaveConfirm = false

    /// The exit guard is live only while still answering: a batch run is single-sitting, so leaving
    /// mid-answer loses progress (#307). Once completed/failed, Close leaves normally.
    private var guardActive: Bool {
        if case .answering = viewModel.state { return true }
        return false
    }

    /// For the recap's "this should be correct" suggestion (#338): the client to post it + the auth
    /// service driving the guest → sign-in conversion, and whether this run is a guest run.
    private let apiClient: FlashcardApiClient
    private let authService: AuthService?
    private let isGuest: Bool

    init(
        flashcardRepository: FlashcardRepository,
        sessionRepository: PracticeSessionRepository,
        entry: PracticeEntry,
        apiClient: FlashcardApiClient,
        authService: AuthService? = nil,
        isGuest: Bool = false
    ) {
        self.apiClient = apiClient
        self.authService = authService
        self.isGuest = isGuest
        _viewModel = StateObject(
            wrappedValue: BatchPracticeViewModel(
                flashcardRepository: flashcardRepository,
                sessionRepository: sessionRepository,
                entry: entry,
                apiClient: apiClient
            )
        )
    }

    var body: some View {
        NavigationStack {
            content
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        // Close is the only exit (a fullScreenCover has no swipe-dismiss); while answering
                        // it confirms first — the batch is single-sitting so leaving loses progress (#307).
                        Button("Close") { handleClose() }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        if let deckId = viewModel.shareDeckId, let url = shareURL(deckId) {
                            ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }
                                .accessibilityLabel(Text("Share deck"))
                        }
                    }
                }
                .alert("Leave this session?", isPresented: $showLeaveConfirm) {
                    Button("Leave", role: .destructive) { dismiss() }
                    Button("Keep practicing", role: .cancel) {}
                } message: {
                    Text("This session runs in one sitting — if you leave, your progress won't be saved.")
                }
                .task { await viewModel.start() }
                .onDisappear { viewModel.stopObserving() }
        }
    }

    /// While answering, confirm before leaving (progress isn't saved); otherwise just dismiss.
    private func handleClose() {
        if guardActive { showLeaveConfirm = true } else { dismiss() }
    }

    private func shareURL(_ deckId: Int64) -> URL? {
        URL(string: "\(AppConfig.webAppBaseURL)/decks/\(deckId)/practice")
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingView()
        case let .answering(cards, mode):
            // Pass the countdown so the answering view shows the chip + auto-submits at 0 (#289).
            BatchAnsweringView(
                cards: cards,
                mode: mode,
                remainingSeconds: viewModel.remainingSeconds,
                onPromptImageSettled: { viewModel.onPromptImageSettled(index: $0) }
            ) { answers in viewModel.submit(answers) }
        case let .completed(numCorrect, numIncorrect):
            CompletionView(
                numCorrect: numCorrect,
                numIncorrect: numIncorrect,
                streak: viewModel.streak,
                review: viewModel.review,
                suggestion: ReviewSuggestion(
                    mode: viewModel.mode,
                    isGlobal: viewModel.isGlobal,
                    isGuest: isGuest,
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
}

/// The answering phase of a grade-at-the-end run: the card list + a Submit bar. Owns the per-card
/// entries (the typed text for Test, the chosen option index for Multiple Choice) until Submit.
private struct BatchAnsweringView: View {
    let cards: [Flashcard]
    /// Timed batch countdown (#289); nil = untimed. When it hits 0 the view auto-submits.
    let remainingSeconds: Int?
    let onSubmit: ([String?]) -> Void
    /// Reports an opening card's prompt image settling, so a timed run doesn't spend its budget while
    /// the first cards are still spinners (#372).
    let onPromptImageSettled: (Int) -> Void

    private let isTest: Bool
    /// Multiple-choice options per card, built once so they don't reshuffle on recomposition.
    private let choices: [[String]]
    @State private var typed: [String]
    @State private var picked: [Int] // -1 = none
    /// Guards against a double auto-submit if `remainingSeconds` re-emits 0.
    @State private var didAutoSubmit = false
    /// Which answer field holds focus, so the keyboard's Next key can move to the following one (#416).
    @FocusState private var focusedField: Int?

    init(
        cards: [Flashcard],
        mode: String,
        remainingSeconds: Int?,
        onPromptImageSettled: @escaping (Int) -> Void,
        onSubmit: @escaping ([String?]) -> Void
    ) {
        self.cards = cards
        self.remainingSeconds = remainingSeconds
        self.onPromptImageSettled = onPromptImageSettled
        self.onSubmit = onSubmit
        let test = PracticeMode.companion.fromKey(key: mode) == .test
        self.isTest = test
        self.choices = test ? [] : cards.map { IosPracticeGradingKt.buildChoicesForSwift(card: $0, deck: cards) }
        _typed = State(initialValue: Array(repeating: "", count: cards.count))
        _picked = State(initialValue: Array(repeating: -1, count: cards.count))
    }

    private var answeredCount: Int {
        isTest
            ? typed.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.count
            : picked.filter { $0 >= 0 }.count
    }

    /// The per-card answers aligned to the card list: typed text (Test) or the chosen option (MC),
    /// nil when left unanswered.
    private func buildAnswers() -> [String?] {
        cards.indices.map { i in
            if isTest {
                let text = typed[i].trimmingCharacters(in: .whitespaces)
                return text.isEmpty ? nil : typed[i]
            }
            return picked[i] >= 0 ? choices[i][picked[i]] : nil
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Timed batch (#289): the countdown chip + auto-submit whatever's answered when it hits 0.
            if let remainingSeconds {
                TimerChip(remainingSeconds: remainingSeconds)
                    .padding(.top, Spacing.sm)
            }
            // ScrollViewReader so Next can reach a field that hasn't been built yet: the stack is
            // lazy, so the following row often doesn't exist until it's scrolled towards (#416).
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: Spacing.md) {
                        ForEach(cards.indices, id: \.self) { i in
                            card(i).id(i)
                        }
                    }
                    .padding(Spacing.lg)
                }
                .onChange(of: focusedField) { _, field in
                    // Keep the focused field clear of the keyboard. Scrolling on focus rather than on
                    // the Next tap also covers a field the user simply tapped into.
                    guard let field else { return }
                    withAnimation { proxy.scrollTo(field, anchor: .center) }
                }
            }
            submitBar
        }
        .onChange(of: remainingSeconds) { _, secs in
            if secs == 0, !didAutoSubmit {
                didAutoSubmit = true
                onSubmit(buildAnswers())
            }
        }
    }

    @ViewBuilder private func card(_ i: Int) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("\(i + 1).").font(.subheadline.bold()).foregroundStyle(.secondary)
            if !cards[i].question.isEmpty {
                Text(cards[i].question).font(.body.weight(.semibold))
            }
            if let url = cards[i].imageUrl, !url.isEmpty {
                RemoteCardImage(url: url, onReady: { onPromptImageSettled(i) })
                    .frame(maxHeight: 160)
            }
            if isTest {
                TextField("Answer", text: $typed[i])
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .accessibilityLabel(Text("Answer for question \(i + 1)"))
                    .focused($focusedField, equals: i)
                    // The last field takes Done — there's nothing after it to move to, and
                    // submitting from the keyboard would be too easy to do by accident in a run
                    // that can't be resumed (#306).
                    .submitLabel(i == cards.count - 1 ? .done : .next)
                    .onSubmit {
                        guard i < cards.count - 1 else {
                            focusedField = nil
                            return
                        }
                        focusedField = i + 1
                    }
            } else {
                ForEach(choices[i].indices, id: \.self) { idx in
                    Button {
                        picked[i] = idx
                    } label: {
                        HStack {
                            Text(choices[i][idx]).foregroundStyle(.primary)
                            Spacer()
                            if picked[i] == idx {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(.tint)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .padding(Spacing.sm)
                    .background(
                        RoundedRectangle(cornerRadius: 10)
                            .fill(picked[i] == idx ? Color.accentColor.opacity(0.15) : Color(.secondarySystemBackground))
                    )
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    private var submitBar: some View {
        Button {
            onSubmit(buildAnswers())
        } label: {
            Text("Submit (\(answeredCount)/\(cards.count))").frame(maxWidth: .infinity)
        }
        .buttonStyle(.primary)
        .disabled(answeredCount == 0)
        .padding(Spacing.lg)
        .background(.bar)
    }
}
