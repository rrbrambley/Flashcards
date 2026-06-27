import SwiftUI

/// 🔥 N day streak pill (FLA-106) — the overall practice streak, shown on Home and the practice
/// completion screen. Warm accent, matching the web/Android treatment.
struct StreakBadge: View {
    let streak: Int

    var body: some View {
        // SF Symbol (not the 🔥 emoji) so it always renders + takes the warm tint.
        Label("\(streak) day streak", systemImage: "flame.fill")
            .font(.caption.weight(.semibold))
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .background(Color.orange.opacity(0.15), in: Capsule())
            .foregroundStyle(.orange)
    }
}
