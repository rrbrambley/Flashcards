import Shared
import Speech
import SwiftUI

/// Configure a practice run before it starts (FLA-200): pick a mode (the primary choice), adjust
/// settings (Shuffle, default On), then Start. Mirrors the web/Android configure-then-start flow.
/// Presented as a sheet from the library deck actions; on Start it hands the (mode, shuffle) choice
/// back to the caller, which launches the run.
struct PracticeConfigView: View {
    let deckTitle: String
    /// Modes offered, already filtered by their feature flags (FLA-213) by the presenter.
    let availableModes: [PracticeMode]
    /// The deck's card count = the max questions; the field defaults to it (whole deck). FLA-219.
    let maxQuestions: Int
    /// Whether to offer the "Questions" subset field (gated on `practice_question_count`).
    let questionCountEnabled: Bool
    /// Whether to offer the "Grade at the end" toggle (gated on `practice_grade_at_end`, #293).
    let gradeAtEndEnabled: Bool
    /// Whether to offer the "Timed" toggle (gated on `practice_timer`, #289).
    let timerEnabled: Bool
    /// Whether voice answering is offered at all (#389) — the `practice_voice_input` flag, which is
    /// seeded off and dark-launched.
    var voiceEnabled = false
    let onStart: (
        _ modeKey: String, _ shuffle: Bool, _ questionCount: Int32?, _ gradeAtEnd: Bool, _ timeLimitSeconds: Int32?
    ) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var selectedMode: String?
    @State private var shuffle = true
    @State private var voiceInput = VoiceInputPreference.isEnabled
    @State private var gradeAtEnd = false
    @State private var timed = false
    @State private var minutesText = "1"
    @State private var secondsText = "0"
    @State private var questionsText = ""

    /// Grade-at-the-end only applies to the objectively-graded modes (#293), not Classic's self-graded flip.
    /// The two modes with an answer to speak. Classic is a self-graded flip — nothing to say, and
    /// nothing to grade a transcript against.
    private var canUseVoice: Bool {
        selectedMode == PracticeMode.test.key || selectedMode == PracticeMode.multiplechoice.key
    }

    /// The deck on step 1, the mode being configured on step 2 — so the title says what you're doing.
    private var navigationTitle: String {
        guard let selectedMode,
            let label = availableModes.first(where: { $0.key == selectedMode })?.label
        else { return String(localized: "Practice \(deckTitle)") }
        return String(localized: "\(label) settings")
    }

    private var canGradeAtEnd: Bool {
        gradeAtEndEnabled && (selectedMode == PracticeMode.test.key || selectedMode == PracticeMode.multiplechoice.key)
    }

    var body: some View {
        NavigationStack {
            Form {
                if selectedMode == nil {
                    // Step 1: the modes and nothing else (#410). Tapping one *is* advancing, so the
                    // common path stays two taps — no Next button taxing every run. A chevron rather
                    // than a checkmark, because this navigates rather than selecting-in-place.
                    Section("Choose a mode") {
                        if availableModes.isEmpty {
                            Text("No practice modes are available right now.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        ForEach(availableModes) { mode in
                            Button {
                                selectedMode = mode.key
                            } label: {
                                HStack(alignment: .firstTextBaseline) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(mode.label).font(.headline).foregroundStyle(.primary)
                                        Text(mode.summary).font(.subheadline).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.footnote)
                                        .foregroundStyle(.tertiary)
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                } else {
                Section("Settings") {
                    if questionCountEnabled && maxQuestions > 0 {
                        HStack {
                            Text("Questions (max \(maxQuestions))")
                            Spacer()
                            TextField("", text: $questionsText)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 80)
                        }
                    }
                    Toggle("Shuffle cards", isOn: $shuffle)
                    // Answering by voice (#389) — an input method, not a mode, so it sits with the
                    // other settings. Flipping this must not prompt for the microphone: permission
                    // is requested when the user first actually asks to speak.
                    if voiceEnabled, canUseVoice {
                        VStack(alignment: .leading, spacing: 2) {
                            Toggle("Answer by voice", isOn: voiceToggleBinding)
                                .disabled(!speechAvailable || blockedByGradeAtEnd)
                                // Local preference, not a property of the run — so it has to outlive
                                // this sheet rather than ride on the session (#386).
                                .onChange(of: voiceInput) { VoiceInputPreference.isEnabled = voiceInput }
                            Text(voiceCaption)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                // Single-sitting settings (#306): grade-at-the-end + timed run start to finish in one go.
                if canGradeAtEnd || timerEnabled {
                    Section("Complete in a single session") {
                        // Grade-at-the-end (#293): always shown when flagged, but disabled unless a
                        // gradeable mode (Test / Multiple Choice) is selected — Classic self-grades.
                        if canGradeAtEnd {
                            VStack(alignment: .leading, spacing: 2) {
                                Toggle("Grade at the end", isOn: $gradeAtEnd)
                                Text("Answer every card, then submit to see your score")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        if timerEnabled {
                            Toggle("Timed", isOn: $timed)
                            if timed {
                                HStack {
                                    Text("Time limit")
                                    Spacer()
                                    TextField("", text: $minutesText)
                                        .keyboardType(.numberPad)
                                        .multilineTextAlignment(.trailing)
                                        .frame(width: 44)
                                    Text("min").foregroundStyle(.secondary)
                                    TextField("", text: $secondsText)
                                        .keyboardType(.numberPad)
                                        .multilineTextAlignment(.trailing)
                                        .frame(width: 44)
                                    Text("sec").foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
                }
            }
            .navigationTitle(navigationTitle)
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { if questionsText.isEmpty { questionsText = String(maxQuestions) } }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    // On the settings step, back returns to the modes — a wrong pick is recoverable
                    // without abandoning the sheet.
                    if selectedMode == nil {
                        Button("Cancel") { dismiss() }
                    } else {
                        Button("Modes") { selectedMode = nil }
                    }
                }
                // Start belongs to the settings step, so it's never shown disabled waiting for a mode.
                if let mode = selectedMode {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Start") {
                            onStart(mode, shuffle, chosenQuestionCount(), canGradeAtEnd && gradeAtEnd, chosenTimeLimit())
                        }
                    }
                }
            }
        }
    }

    /// The chosen subset size: clamped to 1...max; nil (whole deck) when disabled or left at the max.
    private func chosenQuestionCount() -> Int32? {
        guard questionCountEnabled, maxQuestions > 0 else { return nil }
        let n = min(max(Int(questionsText) ?? maxQuestions, 1), maxQuestions)
        return n < maxQuestions ? Int32(n) : nil
    }

    /// The chosen time limit in seconds (mm:ss → total), at least 1; nil when off (#289).
    private func chosenTimeLimit() -> Int32? {
        guard timerEnabled, timed else { return nil }
        let total = (Int(minutesText) ?? 0) * 60 + (Int(secondsText) ?? 0)
        return Int32(max(1, total))
    }

    /// Whether this device can recognise speech at all. Checked up front so the toggle can say so,
    /// rather than leaving the user to discover an inert panel mid-practice.
    private var speechAvailable: Bool {
        SFSpeechRecognizer(locale: .current) != nil
    }

    /// Grade-at-the-end puts the whole deck on screen at once, so a single microphone has no
    /// unambiguous target — voice is card-by-card only (#386). Saying so beats accepting the
    /// combination and silently dropping voice at runtime, which is what it did before (#413 review).
    private var blockedByGradeAtEnd: Bool { canGradeAtEnd && gradeAtEnd }

    /// Reads as off while grade-at-the-end blocks it, without disturbing the stored preference —
    /// turning grade-at-the-end back off restores the user's actual choice.
    private var voiceToggleBinding: Binding<Bool> {
        Binding(
            get: { voiceInput && !blockedByGradeAtEnd },
            set: { voiceInput = $0 }
        )
    }

    /// The mode case can't happen now that the row is rendered solely on the steps for modes that
    /// can use it (#410); what's left is device support and the grade-at-the-end conflict.
    private var voiceCaption: LocalizedStringKey {
        if !speechAvailable { return "Not supported on this device" }
        if blockedByGradeAtEnd { return "Not available when grading at the end" }
        return "Say your answer instead of typing or tapping"
    }

}
