import Foundation

/// A selectable practice mode: its persisted `key` (stored on the session + sent to the backend) and
/// the chooser copy. `allCases` is the chooser's display order; the runner dispatches the per-card UI
/// on the session's mode key (unknown keys fall back to Classic).
enum PracticeMode: String, CaseIterable, Identifiable {
    case classic = "flashcards"
    case test = "test"
    case multipleChoice = "multiple_choice"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .classic: "Classic"
        case .test: "Test"
        case .multipleChoice: "Multiple Choice"
        }
    }
}
