import SDWebImageSwiftUI
import SwiftUI

/// A user's avatar (FLA-162): the curated CDN image clipped to a circle, or — when no avatar is set
/// or the CDN is unconfigured (`url == nil`) — a fallback initials monogram on a color hashed from
/// `name` (so a given user keeps a stable color). Mirrors the web `<Avatar>` and Android `Avatar`.
struct AvatarView: View {
    let url: String?
    let name: String?
    var size: CGFloat = 32

    var body: some View {
        Group {
            if let url, let parsed = URL(string: url) {
                WebImage(url: parsed) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    monogram
                }
            } else {
                monogram
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityLabel(label)
    }

    private var monogram: some View {
        Circle()
            .fill(monogramColor)
            .overlay {
                Text(initials)
                    .font(.system(size: size * 0.42, weight: .semibold))
                    .foregroundStyle(.white)
            }
    }

    private var trimmedName: String? {
        guard let trimmed = name?.trimmingCharacters(in: .whitespaces), !trimmed.isEmpty else { return nil }
        return trimmed
    }

    /// Up to two uppercase initials from the name; "?" when there's no name.
    private var initials: String {
        guard let name = trimmedName else { return "?" }
        let words = name.split(whereSeparator: { $0.isWhitespace })
        let first = words.first?.first.map(String.init) ?? ""
        let last = words.count > 1 ? (words.last?.first.map(String.init) ?? "") : ""
        let result = (first + last).uppercased()
        return result.isEmpty ? "?" : result
    }

    /// A stable, pleasant color derived from the name (HSB hue hashed from its characters).
    private var monogramColor: Color {
        let seed = trimmedName ?? ""
        var hash = 0
        for scalar in seed.unicodeScalars {
            hash = (hash * 31 + Int(scalar.value)) % 360
        }
        let hue = Double((hash % 360 + 360) % 360) / 360.0
        return Color(hue: hue, saturation: 0.45, brightness: 0.55)
    }

    private var label: String {
        if let name = trimmedName { return "\(name)'s avatar" }
        return "Avatar"
    }
}
