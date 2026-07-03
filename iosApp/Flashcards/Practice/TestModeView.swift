import Shared
import SwiftUI

/// Text-entry "Test" practice: the user types the answer, graded case-insensitively and
/// typo-tolerantly via the shared `gradeTextAnswer`. After submitting, the typed answer + a verdict
/// are revealed (plus the correct answer when wrong); grading scores it via `onGraded` (so the streak
/// badge shows on the verdict), and "Next" advances via `onAdvance`. The two-phase state resets per
/// card because the runner re-inits this view via `.id(position)`.
struct TestModeView: View {
    let card: Flashcard
    /// Called when the answer is graded (verdict shown) — scores it + reveals the streak badge on the
    /// answer itself, before advancing.
    let onGraded: (Bool, String?) -> Void
    /// Called on "Next" to move to the following card.
    let onAdvance: () -> Void
    var discussionsEnabled = false
    var onDiscuss: () -> Void = {}
    /// Whether this is a global (catalog) deck — gates the "this should be correct" action (FLA-135).
    var canSuggest = false
    var isGuest = false
    var apiClient: FlashcardApiClient?
    var authService: AuthService?

    @State private var input = ""
    @State private var graded: Graded?
    /// Guards an accidental empty submit (keyboard Done or Check) from grading it wrong (FLA-190).
    @State private var confirmingBlank = false

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
                    // On a global-deck card graded wrong, offer to suggest the typed answer (FLA-135) —
                    // but never for a blank answer (a skip can't be a valid alternative, FLA-190).
                    if canSuggest, !graded.correct,
                        !graded.input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                        !card.cardUid.isEmpty, let apiClient {
                        SuggestAnswerView(
                            cardUid: card.cardUid,
                            suggestedAnswer: graded.input,
                            isGuest: isGuest,
                            apiClient: apiClient,
                            authService: authService
                        )
                    }
                    Button("Next") { onAdvance() }
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
                        .onChange(of: input) {
                            // Typing again means they didn't want to skip — dismiss the prompt.
                            if confirmingBlank { confirmingBlank = false }
                        }
                    Button("Check", action: submit)
                        .buttonStyle(.primary)
                    if confirmingBlank {
                        Text("You haven't typed an answer — skip this one?")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                        Button("Confirm") { grade() }
                            .buttonStyle(.primary)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, Spacing.lg)
        }
    }

    private func submit() {
        if input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            // Confirm the skip instead of grading a blank answer wrong; Confirm still allows an
            // intentional blank submit, so Check is never disabled (FLA-190).
            confirmingBlank = true
            return
        }
        grade()
    }

    private func grade() {
        confirmingBlank = false
        // Kotlin default args don't bridge to Swift, so pass alternativeAnswers explicitly (FLA-109).
        let correct = TextAnswerGradingKt.gradeTextAnswer(
            input: input,
            answer: card.answer,
            alternativeAnswers: card.alternativeAnswers
        ).correct
        graded = Graded(input: input, correct: correct)
        // Score it now (verdict is on screen) so the streak badge shows on this answer, not the next card.
        onGraded(correct, input)
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
