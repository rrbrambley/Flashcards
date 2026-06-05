import SwiftUI

/// Design tokens shared across screens. Colors + typography lean on the system (semantic colors and
/// Dynamic Type text styles) so light/dark and accessibility work for free; the accent is the
/// asset-catalog `AccentColor`. These tokens cover the bits the system doesn't: a spacing scale and
/// corner radii, for consistent layout.
enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
    static let xl: CGFloat = 32
}

enum CornerRadius {
    static let control: CGFloat = 10
    static let card: CGFloat = 12
}
