import Shared
import SwiftUI

/// Multiple-choice practice: up to four options (the correct answer + distractors drawn from other
/// cards in the deck via the shared `buildChoices`). On pick, the right/wrong options highlight and
/// the outcome is scored via `onGraded` (so the streak badge shows on the revealed answer); "Next"
/// advances via `onAdvance`. Choices are built once per card (in `init`) and the selection resets
/// because the runner re-inits this view via `.id(position)`.
struct MultipleChoiceModeView: View {
    let card: Flashcard
    /// Called when an option is picked (answer revealed) — scores it + reveals the streak badge here.
    let onGraded: (Bool, String?) -> Void
    /// Called on "Next" to move to the following card.
    let onAdvance: () -> Void
    let discussionsEnabled: Bool
    let onDiscuss: () -> Void

    @State private var choices: [String]
    @State private var selected: Int?

    // Derived from the @State `choices` (not recomputed), so it always matches the displayed order.
    // `buildChoices` shuffles non-deterministically, and SwiftUI re-runs `init` on every render — so
    // computing this from a fresh `buildChoices` call would desync from the shown choices, mismarking
    // the correct option (and the highlight) after a re-render.
    private var correctIndex: Int {
        choices.firstIndex(of: card.answer.trimmingCharacters(in: .whitespacesAndNewlines)) ?? -1
    }

    init(
        card: Flashcard,
        deck: [Flashcard],
        onGraded: @escaping (Bool, String?) -> Void,
        onAdvance: @escaping () -> Void,
        discussionsEnabled: Bool = false,
        onDiscuss: @escaping () -> Void = {}
    ) {
        self.card = card
        self.onGraded = onGraded
        self.onAdvance = onAdvance
        self.discussionsEnabled = discussionsEnabled
        self.onDiscuss = onDiscuss
        // The first init's shuffle wins (@State ignores later initialValues), so `choices` is stable.
        _choices = State(initialValue: IosPracticeGradingKt.buildChoicesForSwift(card: card, deck: deck))
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
                    Button("Next") { onAdvance() }
                        .buttonStyle(.primary)
                        .padding(.top, Spacing.sm)
                    // Discussion opens once an option is picked (the answer is revealed), mirroring web.
                    if discussionsEnabled {
                        DiscussButton(action: onDiscuss)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, Spacing.lg)
        }
    }

    private func choiceButton(index: Int, option: String) -> some View {
        Button {
            // Grade on first pick so the streak badge surfaces on the revealed answer, not the next card.
            if selected == nil {
                selected = index
                onGraded(index == correctIndex, option)
            }
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
