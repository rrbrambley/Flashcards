import Shared
import SwiftUI

/// Text-entry "Test" practice: the user types the answer, graded case-insensitively and
/// typo-tolerantly via the shared `gradeTextAnswer`. After submitting, the typed answer + a verdict
/// are revealed (plus the correct answer when wrong); "Next" reports the outcome via `onResult`. The
/// two-phase state resets per card because the runner re-inits this view via `.id(position)`.
struct TestModeView: View {
    let card: Flashcard
    let onResult: (Bool) -> Void

    @State private var input = ""
    @State private var graded: Graded?

    private struct Graded {
        let input: String
        let correct: Bool
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                CardPrompt(card: card)

                if let graded {
                    verdict(graded)
                    Button("Next") { onResult(graded.correct) }
                        .buttonStyle(.primary)
                } else {
                    TextField("Type the answer", text: $input)
                        .textFieldStyle(.roundedBorder)
                        .autocorrectionDisabled()
                        .submitLabel(.done)
                        .onSubmit(submit)
                    Button("Check", action: submit)
                        .buttonStyle(.primary)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, Spacing.lg)
        }
    }

    private func submit() {
        // Kotlin default args don't bridge to Swift, so pass alternativeAnswers explicitly (FLA-109).
        let correct = TextAnswerGradingKt.gradeTextAnswer(
            input: input,
            answer: card.answer,
            alternativeAnswers: card.alternativeAnswers
        ).correct
        graded = Graded(input: input, correct: correct)
    }

    @ViewBuilder
    private func verdict(_ graded: Graded) -> some View {
        let trimmed = graded.input.trimmingCharacters(in: .whitespacesAndNewlines)
        HStack {
            Text(trimmed.isEmpty ? "(blank)" : trimmed)
                .font(.headline)
            Spacer()
            Label(graded.correct ? "Correct" : "Incorrect", systemImage: graded.correct ? "checkmark" : "xmark")
                .font(.headline)
                .foregroundStyle(graded.correct ? .green : .red)
        }
        if !graded.correct {
            Text("Answer: \(card.answer)")
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
        }
    }
}
