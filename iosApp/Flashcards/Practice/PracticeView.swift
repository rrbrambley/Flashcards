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

    init(
        flashcardRepository: FlashcardRepository,
        sessionRepository: PracticeSessionRepository,
        entry: PracticeEntry,
        apiClient: FlashcardApiClient? = nil,
        authService: AuthService? = nil
    ) {
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
        if case let .showCard(_, _, _, _, _, mode, _) = viewModel.state {
            return (PracticeMode(rawValue: mode) ?? .classic) == .classic
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
                .onChange(of: viewModel.saveState) { _, newValue in
                    // A successful save signs the user in; leave the practice screen so RootView swaps
                    // to the main tabs (the saved session shows under "Continue studying").
                    if newValue == .saved { dismiss() }
                }
                .task { await viewModel.start() }
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
        case let .showCard(card, position, numCorrect, numIncorrect, canGoBack, mode, deck):
            VStack(spacing: Spacing.lg) {
                ScoreRow(numIncorrect: numIncorrect, numCorrect: numCorrect)
                modeView(mode: mode, card: card, deck: deck, canGoBack: canGoBack)
                    // Re-init the per-card view (flip / two-phase Test+MC state) on advance.
                    .id(position)
                    .frame(maxHeight: .infinity)
            }
            .padding(Spacing.lg)
        case let .completed(numCorrect, numIncorrect):
            CompletionView(numCorrect: numCorrect, numIncorrect: numIncorrect) { dismiss() }
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
    private func modeView(mode: String, card: Flashcard, deck: [Flashcard], canGoBack: Bool) -> some View {
        switch PracticeMode(rawValue: mode) ?? .classic {
        case .classic:
            ClassicModeView(
                card: card,
                canGoBack: canGoBack,
                onResult: viewModel.onResult,
                onPrevious: viewModel.goBack,
                onNext: viewModel.goForward
            )
        case .test:
            TestModeView(card: card, onResult: viewModel.onResult)
        case .multipleChoice:
            MultipleChoiceModeView(card: card, deck: deck, onResult: viewModel.onResult)
        }
    }
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

/// End-of-deck summary.
private struct CompletionView: View {
    let numCorrect: Int
    let numIncorrect: Int
    let onDone: () -> Void

    var body: some View {
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
            Button("Done", action: onDone)
                .buttonStyle(.primary)
                .padding(.horizontal, Spacing.xl)
                .padding(.top, Spacing.md)
        }
        .padding(Spacing.lg)
    }

    private func stat(_ label: String, _ value: Int, color: Color) -> some View {
        VStack {
            Text("\(value)").font(.largeTitle.bold()).foregroundStyle(color)
            Text(label).font(.subheadline).foregroundStyle(.secondary)
        }
    }
}
