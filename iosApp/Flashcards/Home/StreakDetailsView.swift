import SwiftUI

/// The streak-details popup (#353): the flame current streak, a headline, and a max-streak /
/// sessions-completed footer. Presented from the tappable home streak badge.
struct StreakDetailsView: View {
    let current: Int
    let maxStreak: Int
    let sessionsCompleted: Int

    var body: some View {
        VStack(spacing: Spacing.md) {
            Text("🔥 \(current)")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(.orange)
            Text("You're on a \(current)-day streak!")
                .font(.subheadline.weight(.medium))
            Divider()
            HStack(spacing: Spacing.xl) {
                stat(value: maxStreak, label: "Max streak")
                stat(value: sessionsCompleted, label: "Sessions completed")
            }
        }
        .padding(Spacing.lg)
        .frame(minWidth: 260)
    }

    private func stat(value: Int, label: LocalizedStringKey) -> some View {
        VStack(spacing: 2) {
            Text("\(value)").font(.title2.weight(.bold))
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
    }
}
