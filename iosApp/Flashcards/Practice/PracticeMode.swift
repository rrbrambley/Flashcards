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
        case .test: String(localized: "Test")
        case .multiplechoice: String(localized: "Multiple Choice")
        default: String(localized: "Classic")
        }
    }

    /// One-line description shown in the practice-config picker (FLA-200), mirroring web/Android copy.
    var summary: String {
        switch self {
        case .test: String(localized: "Type the answer.")
        case .multiplechoice: String(localized: "Pick the answer from four options.")
        default: String(localized: "Flip the card and mark whether you knew it.")
        }
    }

    /// The modes offered in the chooser given the caller's resolved feature flags (FLA-213). Fail-open,
    /// matching Android: a mode shows unless its flag is explicitly `false`, so an offline/failed flag
    /// fetch (empty map) still offers every mode rather than locking the user out of practice.
    static func available(flags: [String: Bool]) -> [PracticeMode] {
        entries.filter { flags[$0.flagKey] != false }
    }
}
