import SDWebImageSwiftUI
import Shared
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

    /// Initials + hue come from the shared `Monogram` (FLA-194) so all platforms agree.
    private var initials: String { Monogram.shared.initials(name: name) }

    /// Builds the color from the shared hue (HSB at hue / 360, S 0.45, B 0.55).
    private var monogramColor: Color {
        Color(hue: Double(Monogram.shared.hue(name: name)) / 360.0, saturation: 0.45, brightness: 0.55)
    }

    private var label: String {
        if let name = trimmedName { return "\(name)'s avatar" }
        return "Avatar"
    }
}
