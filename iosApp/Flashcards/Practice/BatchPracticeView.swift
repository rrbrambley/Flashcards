import Shared
import SwiftUI

/// The "grade at the end" runner (#293): every card in one scrollable list to answer in any order,
/// then a Submit that grades the whole set and lands on the shared `CompletionView` recap (#298).
/// Test / Multiple Choice only. Presented (like `PracticeView`) as a full-screen cover.
struct BatchPracticeView: View {
    @StateObject private var viewModel: BatchPracticeViewModel
    @Environment(\.dismiss) private var dismiss

    init(
        flashcardRepository: FlashcardRepository,
        sessionRepository: PracticeSessionRepository,
        entry: PracticeEntry,
        apiClient: FlashcardApiClient
    ) {
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
                        Button("Close") { dismiss() }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        if let deckId = viewModel.shareDeckId, let url = shareURL(deckId) {
                            ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }
                                .accessibilityLabel("Share deck")
                        }
                    }
                }
                .task { await viewModel.start() }
                .onDisappear { viewModel.stopObserving() }
        }
    }

    private func shareURL(_ deckId: Int64) -> URL? {
        URL(string: "\(AppConfig.webAppBaseURL)/decks/\(deckId)/practice")
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingView()
        case let .answering(cards, mode):
            BatchAnsweringView(cards: cards, mode: mode) { answers in viewModel.submit(answers) }
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
}

/// The answering phase of a grade-at-the-end run: the card list + a Submit bar. Owns the per-card
/// entries (the typed text for Test, the chosen option index for Multiple Choice) until Submit.
private struct BatchAnsweringView: View {
    let cards: [Flashcard]
    let onSubmit: ([String?]) -> Void

    private let isTest: Bool
    /// Multiple-choice options per card, built once so they don't reshuffle on recomposition.
    private let choices: [[String]]
    @State private var typed: [String]
    @State private var picked: [Int] // -1 = none

    init(cards: [Flashcard], mode: String, onSubmit: @escaping ([String?]) -> Void) {
        self.cards = cards
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

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                LazyVStack(spacing: Spacing.md) {
                    ForEach(cards.indices, id: \.self) { i in
                        card(i)
                    }
                }
                .padding(Spacing.lg)
            }
            submitBar
        }
    }

    @ViewBuilder private func card(_ i: Int) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("\(i + 1).").font(.subheadline.bold()).foregroundStyle(.secondary)
            if !cards[i].question.isEmpty {
                Text(cards[i].question).font(.body.weight(.semibold))
            }
            if let url = cards[i].imageUrl, !url.isEmpty {
                RemoteCardImage(url: url).frame(maxHeight: 160)
            }
            if isTest {
                TextField("Answer", text: $typed[i])
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .accessibilityLabel("Answer for question \(i + 1)")
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
            let answers: [String?] = cards.indices.map { i in
                if isTest {
                    let text = typed[i].trimmingCharacters(in: .whitespaces)
                    return text.isEmpty ? nil : typed[i]
                }
                return picked[i] >= 0 ? choices[i][picked[i]] : nil
            }
            onSubmit(answers)
        } label: {
            Text("Submit (\(answeredCount)/\(cards.count))").frame(maxWidth: .infinity)
        }
        .buttonStyle(.primary)
        .disabled(answeredCount == 0)
        .padding(Spacing.lg)
        .background(.bar)
    }
}
