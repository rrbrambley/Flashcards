import Shared
import SwiftUI

/// SwiftUI conveniences on the shared `PracticeMode` (FLA-195). The mode + its persisted `key` live in
/// `:shared`; only the localized label + the `Identifiable` conformance `Picker`/`ForEach` need stay
/// here. The chooser iterates `PracticeMode.entries` (KN-provided) since the bridged enum is a
/// non-final class and can't be `CaseIterable`.
extension PracticeMode: @retroactive Identifiable {
    public var id: String { key }

    var label: String {
        switch self {
        case .test: "Test"
        case .multiplechoice: "Multiple Choice"
        default: "Classic"
        }
    }

    /// One-line description shown in the practice-config picker (FLA-200), mirroring web/Android copy.
    var summary: String {
        switch self {
        case .test: "Type the answer."
        case .multiplechoice: "Pick the answer from four options."
        default: "Flip the card and mark whether you knew it."
        }
    }
}
