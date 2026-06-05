import SwiftUI

/// Full-width filled button for the primary action on a screen (sign in, save, …).
struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .foregroundStyle(.white)
            .background(
                Color.accentColor.opacity(isEnabled ? 1 : 0.4),
                in: RoundedRectangle(cornerRadius: CornerRadius.control)
            )
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}

/// Full-width tinted/outline button for a secondary action (switch to register, cancel, …).
struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .foregroundStyle(Color.accentColor)
            .background(
                Color.accentColor.opacity(configuration.isPressed ? 0.2 : 0.12),
                in: RoundedRectangle(cornerRadius: CornerRadius.control)
            )
    }
}

extension ButtonStyle where Self == PrimaryButtonStyle {
    static var primary: PrimaryButtonStyle { PrimaryButtonStyle() }
}

extension ButtonStyle where Self == SecondaryButtonStyle {
    static var secondary: SecondaryButtonStyle { SecondaryButtonStyle() }
}
