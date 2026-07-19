import Shared
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
    let onStart: (_ modeKey: String, _ shuffle: Bool, _ questionCount: Int32?, _ gradeAtEnd: Bool) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var selectedMode: String?
    @State private var shuffle = true
    @State private var gradeAtEnd = false
    @State private var questionsText = ""

    /// Grade-at-the-end only applies to the objectively-graded modes (#293), not Classic's self-graded flip.
    private var canGradeAtEnd: Bool {
        gradeAtEndEnabled && (selectedMode == PracticeMode.test.key || selectedMode == PracticeMode.multiplechoice.key)
    }

    var body: some View {
        NavigationStack {
            Form {
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
                                if selectedMode == mode.key {
                                    Image(systemName: "checkmark").foregroundStyle(.tint).fontWeight(.semibold)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(selectedMode == mode.key ? [.isSelected] : [])
                    }
                }
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
                    // Grade-at-the-end (#293): always shown when flagged, but disabled unless a gradeable
                    // mode (Test / Multiple Choice) is selected — Classic is a self-graded flip.
                    if gradeAtEndEnabled {
                        VStack(alignment: .leading, spacing: 2) {
                            Toggle("Grade at the end", isOn: $gradeAtEnd)
                                .disabled(!canGradeAtEnd)
                            Text(canGradeAtEnd
                                ? "Answer every card, then submit to see your score"
                                : "Available for Test & Multiple Choice")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Practice \(deckTitle)")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { if questionsText.isEmpty { questionsText = String(maxQuestions) } }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Start") {
                        if let selectedMode {
                            onStart(selectedMode, shuffle, chosenQuestionCount(), canGradeAtEnd && gradeAtEnd)
                        }
                    }
                    .disabled(selectedMode == nil)
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
}
