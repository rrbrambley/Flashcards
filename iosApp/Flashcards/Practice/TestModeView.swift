import Shared
import SwiftUI

/// Text-entry "Test" practice: the user types the answer, graded case-insensitively and
/// typo-tolerantly via the shared `gradeTextAnswer`. After submitting, the typed answer + a verdict
/// are revealed (plus the correct answer when wrong); "Next" reports the outcome via `onResult`. The
/// two-phase state resets per card because the runner re-inits this view via `.id(position)`.
struct TestModeView: View {
    let card: Flashcard
    let onResult: (Bool, String?) -> Void
    var discussionsEnabled = false
    var onDiscuss: () -> Void = {}
    /// Whether this is a global (catalog) deck — gates the "this should be correct" action (FLA-135).
    var canSuggest = false
    var isGuest = false
    var apiClient: FlashcardApiClient?
    var authService: AuthService?

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
                    // Teach the full set of valid responses (FLA-131); shown on either verdict.
                    if !card.alternativeAnswers.isEmpty {
                        Text("Also acceptable: \(card.alternativeAnswers.joined(separator: ", "))")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                    }
                    // On a global-deck card graded wrong, offer to suggest the typed answer (FLA-135).
                    if canSuggest, !graded.correct, !card.cardUid.isEmpty, let apiClient {
                        SuggestAnswerView(
                            cardUid: card.cardUid,
                            suggestedAnswer: graded.input,
                            isGuest: isGuest,
                            apiClient: apiClient,
                            authService: authService
                        )
                    }
                    Button("Next") { onResult(graded.correct, graded.input) }
                        .buttonStyle(.primary)
                    // Discussion opens once the answer is revealed (after grading), mirroring web.
                    if discussionsEnabled {
                        DiscussButton(action: onDiscuss)
                    }
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
