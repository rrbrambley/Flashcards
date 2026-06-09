import Foundation

extension String {
    /// Whitespace/newline-trimmed copy — used for form validation + before saving.
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }

    /// The optional deck category as a tag list: a single trimmed tag, or empty when blank.
    func toCategoryTags() -> [String] {
        let value = trimmed
        return value.isEmpty ? [] : [value]
    }
}
