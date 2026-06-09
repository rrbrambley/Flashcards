import SwiftUI

/// A deck row/card: title + optional category label + card count, with a chevron affordance.
/// Used by the Library list.
struct DeckCard: View {
    let title: String
    var category: String? = nil
    let cardCount: Int

    var body: some View {
        HStack(spacing: Spacing.md) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                if let category, !category.isEmpty {
                    // Small secondary "Category" label (Linear-style chip).
                    Text(category)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, Spacing.sm)
                        .padding(.vertical, 2)
                        .background(Color(.tertiarySystemGroupedBackground), in: Capsule())
                }
                Text("^[\(cardCount) card](inflect: true)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: Spacing.sm)
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.tertiary)
                .accessibilityHidden(true)
        }
        .padding(Spacing.md)
        .background(
            Color(.secondarySystemGroupedBackground),
            in: RoundedRectangle(cornerRadius: CornerRadius.card)
        )
    }
}
