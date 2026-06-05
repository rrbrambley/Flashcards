import SwiftUI

/// A consistently-styled text field (filled background, rounded). Use `isSecure` for passwords.
struct AppTextField: View {
    let placeholder: String
    @Binding var text: String
    var isSecure: Bool = false
    var keyboard: UIKeyboardType = .default
    var textContentType: UITextContentType?
    var submitLabel: SubmitLabel = .next

    var body: some View {
        Group {
            if isSecure {
                SecureField(placeholder, text: $text)
            } else {
                TextField(placeholder, text: $text)
            }
        }
        .textFieldStyle(.plain)
        .keyboardType(keyboard)
        .textContentType(textContentType)
        .submitLabel(submitLabel)
        .autocorrectionDisabled(keyboard == .emailAddress || isSecure)
        .textInputAutocapitalization(keyboard == .emailAddress ? .never : .sentences)
        .padding(Spacing.md)
        .background(
            Color(.secondarySystemBackground),
            in: RoundedRectangle(cornerRadius: CornerRadius.control)
        )
    }
}
