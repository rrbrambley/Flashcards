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
    /// Prompt-image readiness for the timed-run pause (#314); forwarded to `CardPrompt`.
    var onImageReadyChanged: (Bool) -> Void = { _ in }
    /// Answer by speaking as well as typing (#389). Additive — the text field stays, so a refused
    /// microphone or an unsupported locale leaves the card fully answerable.
    var voiceInput = false
    var onDisableVoice: (() -> Void)?
    var showVoicePrivacyNotice = false
    var onVoicePrivacyNoticeShown: () -> Void = {}
    /// Seconds left in a timed run (#289), or nil when untimed — read by the voice grace window (#426).
    var remainingSeconds: Int?

    @State private var input = ""
    @State private var graded: Graded?
    /// Guards an accidental empty submit (keyboard Done or Check) from grading it wrong (FLA-190).
    @State private var confirmingBlank = false
    /// Set when the user takes over during the auto-advance dwell, pinning the card until they act.
    @State private var advanceCancelled = false

    private struct Graded {
        let input: String
        let correct: Bool
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                CardPrompt(card: card, onImageReadyChanged: onImageReadyChanged)

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
                    if advancing(graded) {
                        VoiceAdvanceNotice()
                    } else {
                        Button("Next") { onAdvance() }
                            .buttonStyle(.primary)
                    }
                    // Discussion opens once the answer is revealed (after grading), mirroring web.
                    if discussionsEnabled {
                        DiscussButton(action: onDiscuss)
                    }
                } else {
                    if voiceInput {
                        VoiceAnswerPanel(
                            onSubmit: { spoken in grade(spoken) },
                            interpret: { hypotheses in
                                // Picks which hypothesis to grade — n-best rescoring (#390).
                                //
                                // The recogniser ranks by a general-purpose language model biased
                                // toward everyday words, which is why proper nouns lose: a country
                                // name is outscored by whatever common phrase it sounds like. We know
                                // something it doesn't — this card's answer — so its own list is
                                // re-ranked with it.
                                //
                                // `gradeTextAnswer` is untouched and still decides, at the same
                                // threshold, so a spoken and a typed string grade identically; what
                                // changes is which string gets graded. That does make voice more
                                // forgiving than typing — any hypothesis can win, and only the
                                // recogniser proposed them — which is the point, since the mis-hear
                                // was never the user's mistake.
                                //
                                // Falls back to the top hypothesis rather than re-prompting: a wrong
                                // answer still has to be recordable, as what they most likely said.
                                guard let chosen = TextAnswerGradingKt.pickSpokenAnswer(
                                    hypotheses: hypotheses,
                                    answer: card.answer,
                                    alternativeAnswers: card.alternativeAnswers
                                ) else { return nil }
                                return VoiceInterpretation(transcript: chosen)
                            },
                            onDisableVoice: onDisableVoice,
                            showPrivacyNotice: showVoicePrivacyNotice,
                            onPrivacyNoticeShown: onVoicePrivacyNoticeShown,
                            remainingSeconds: remainingSeconds
                        )
                    }
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
        .voiceAutoAdvance(
            active: voiceInput && graded != nil,
            correct: graded?.correct == true,
            cancelled: $advanceCancelled,
            onAdvance: onAdvance
        )
    }

    private func advancing(_ graded: Graded) -> Bool {
        voiceInput && !advanceCancelled
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

    /// Whether a candidate answer grades correct, via the shared grader.
    ///
    /// Kotlin default args don't bridge to Swift, so pass alternativeAnswers explicitly (FLA-109).
    private func isCorrect(_ value: String) -> Bool {
        TextAnswerGradingKt.gradeTextAnswer(
            input: value,
            answer: card.answer,
            alternativeAnswers: card.alternativeAnswers
        ).correct
    }

    /// One grading call site, so a spoken answer and a typed one are scored by exactly the same code.
    private func grade(_ spoken: String? = nil) {
        let value = spoken ?? input
        confirmingBlank = false
        let correct = isCorrect(value)
        graded = Graded(input: value, correct: correct)
        // Score it now (verdict is on screen) so the streak badge shows on this answer, not the next card.
        onGraded(correct, value)
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
