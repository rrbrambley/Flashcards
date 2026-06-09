import Shared
import SwiftUI

/// Multiple-choice practice: up to four options (the correct answer + distractors drawn from other
/// cards in the deck via the shared `buildChoices`). On pick, the right/wrong options highlight;
/// "Next" reports the outcome via `onResult`. Choices are built once per card (in `init`) and the
/// selection resets because the runner re-inits this view via `.id(position)`.
struct MultipleChoiceModeView: View {
    let card: Flashcard
    let onResult: (Bool) -> Void

    @State private var choices: [String]
    private let correctIndex: Int
    @State private var selected: Int?

    init(card: Flashcard, deck: [Flashcard], onResult: @escaping (Bool) -> Void) {
        self.card = card
        self.onResult = onResult
        let built = IosPracticeGradingKt.buildChoicesForSwift(card: card, deck: deck)
        _choices = State(initialValue: built)
        correctIndex = built.firstIndex(of: card.answer.trimmingCharacters(in: .whitespacesAndNewlines)) ?? -1
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                CardPrompt(card: card)
                    .padding(.bottom, Spacing.sm)

                ForEach(Array(choices.enumerated()), id: \.offset) { index, option in
                    choiceButton(index: index, option: option)
                }

                if selected != nil {
                    Button("Next") { onResult(selected == correctIndex) }
                        .buttonStyle(.primary)
                        .padding(.top, Spacing.sm)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, Spacing.lg)
        }
    }

    private func choiceButton(index: Int, option: String) -> some View {
        Button {
            if selected == nil { selected = index }
        } label: {
            Text(option)
                .font(.body)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.sm)
                .padding(.horizontal, Spacing.md)
        }
        .buttonStyle(.plain)
        .foregroundStyle(foreground(for: index))
        .background(background(for: index), in: RoundedRectangle(cornerRadius: 14))
        .disabled(selected != nil)
    }

    private func background(for index: Int) -> Color {
        guard let selected else { return Color(.secondarySystemBackground) }
        if index == correctIndex { return .green }
        if index == selected { return .red }
        return Color(.tertiarySystemBackground)
    }

    private func foreground(for index: Int) -> Color {
        guard selected != nil else { return .primary }
        if index == correctIndex || index == selected { return .white }
        return .secondary
    }
}
