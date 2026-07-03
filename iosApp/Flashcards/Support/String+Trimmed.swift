import Foundation

extension String {
    /// Whitespace/newline-trimmed copy — used for form validation + before saving.
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
