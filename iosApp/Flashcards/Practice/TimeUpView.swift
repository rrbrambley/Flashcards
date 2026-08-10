import Shared
import SwiftUI

/// The "Time's up" reveal (#375): the card the clock expired on, with its answer, shown before the
/// completion recap. Without it the one question the user was actively working on is the one whose
/// answer they never see.
struct TimeUpView: View {
    let card: Flashcard
    let onContinue: () -> Void

    var body: some View {
        VStack(spacing: Spacing.lg) {
            Spacer()
            Text("Time's up")
                .font(.title.bold())

            if !card.question.isEmpty {
                Text(card.question)
                    .font(.title3)
                    .multilineTextAlignment(.center)
            }
            if let url = card.imageUrl, !url.isEmpty {
                RemoteCardImage(url: url).frame(maxHeight: 200)
            }

            Text("The answer was")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text(card.answer)
                .font(.title2.weight(.semibold))
                .multilineTextAlignment(.center)

            Spacer()
            Button("See results", action: onContinue)
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
        }
        .padding(Spacing.lg)
    }
}
