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
    /// Prompt-image readiness for the timed-run pause (#314); forwarded to `CardPrompt`.
    let onImageReadyChanged: (Bool) -> Void
    /// Say the answer rather than the option letter (#389) — "Paris", not "option B". Additive: the
    /// options stay tappable throughout.
    let voiceInput: Bool
    let onDisableVoice: (() -> Void)?
    let showVoicePrivacyNotice: Bool
    let onVoicePrivacyNoticeShown: () -> Void

    @State private var choices: [String]
    @State private var selected: Int?
    @State private var advanceCancelled = false

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
        onDiscuss: @escaping () -> Void = {},
        onImageReadyChanged: @escaping (Bool) -> Void = { _ in },
        voiceInput: Bool = false,
        onDisableVoice: (() -> Void)? = nil,
        showVoicePrivacyNotice: Bool = false,
        onVoicePrivacyNoticeShown: @escaping () -> Void = {}
    ) {
        self.card = card
        self.onGraded = onGraded
        self.onAdvance = onAdvance
        self.discussionsEnabled = discussionsEnabled
        self.onDiscuss = onDiscuss
        self.onImageReadyChanged = onImageReadyChanged
        self.voiceInput = voiceInput
        self.onDisableVoice = onDisableVoice
        self.showVoicePrivacyNotice = showVoicePrivacyNotice
        self.onVoicePrivacyNoticeShown = onVoicePrivacyNoticeShown
        // The first init's shuffle wins (@State ignores later initialValues), so `choices` is stable.
        _choices = State(initialValue: IosPracticeGradingKt.buildChoicesForSwift(card: card, deck: deck))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                CardPrompt(card: card, onImageReadyChanged: onImageReadyChanged)
                    .padding(.bottom, Spacing.sm)

                // Above the options, never instead of them — a misrecognition, a refused microphone
                // or an unsupported locale all leave the card answerable by tapping.
                if voiceInput, selected == nil {
                    VoiceAnswerPanel(
                        onSubmit: { transcript in
                            if let index = matchSpoken(transcript) { pick(index) }
                        },
                        interpret: { transcript in
                            // nil re-prompts instead of submitting — never a best guess, since an
                            // auto-submitted wrong answer is recorded and can't be un-graded.
                            guard let index = matchSpoken(transcript) else { return nil }
                            return VoiceInterpretation(note: choices[index])
                        },
                        onDisableVoice: onDisableVoice,
                        showPrivacyNotice: showVoicePrivacyNotice,
                        onPrivacyNoticeShown: onVoicePrivacyNoticeShown
                    )
                    .padding(.bottom, Spacing.sm)
                }

                ForEach(Array(choices.enumerated()), id: \.offset) { index, option in
                    choiceButton(index: index, option: option)
                }

                if selected != nil {
                    if voiceInput, !advanceCancelled {
                        VoiceAdvanceNotice()
                            .padding(.top, Spacing.sm)
                    } else {
                        Button("Next") { onAdvance() }
                            .buttonStyle(.primary)
                            .padding(.top, Spacing.sm)
                    }
                    // Discussion opens once an option is picked (the answer is revealed), mirroring web.
                    if discussionsEnabled {
                        DiscussButton(action: onDiscuss)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, Spacing.lg)
        }
        .voiceAutoAdvance(
            active: voiceInput && selected != nil,
            correct: selected == correctIndex,
            cancelled: $advanceCancelled,
            onAdvance: onAdvance
        )
    }

    /// The shared matcher (#389), so web, Android and iOS agree on which option was named — its
    /// floor and margin are pinned by the golden fixture, not re-decided per platform.
    private func matchSpoken(_ transcript: String) -> Int? {
        VoiceChoiceKt.matchSpokenChoice(transcript: transcript, options: choices)?.intValue
    }

    /// The pick, shared by tapping and speaking, so both grade through exactly the same path.
    private func pick(_ index: Int) {
        guard selected == nil else { return }
        selected = index
        onGraded(index == correctIndex, choices[index])
    }

    private func choiceButton(index: Int, option: String) -> some View {
        Button {
            // Grade on first pick so the streak badge surfaces on the revealed answer, not the next card.
            pick(index)
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
