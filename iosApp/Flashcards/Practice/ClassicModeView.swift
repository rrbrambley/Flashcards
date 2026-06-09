import Shared
import SwiftUI

/// Classic practice: a tap-to-flip card with swipe scoring (right = knew it, left = needs practice),
/// plus Previous/Next that move between cards without scoring. Reports each swipe via `onResult`; the
/// runner owns advancing + the score.
struct ClassicModeView: View {
    let card: Flashcard
    let canGoBack: Bool
    let onResult: (Bool) -> Void
    let onPrevious: () -> Void
    let onNext: () -> Void

    var body: some View {
        VStack(spacing: Spacing.lg) {
            FlashcardCardView(
                card: card,
                onSwipeRight: { onResult(true) },
                onSwipeLeft: { onResult(false) }
            )
            .frame(maxHeight: .infinity)
            NavRow(canGoBack: canGoBack, onPrevious: onPrevious, onNext: onNext)
        }
    }
}

/// A tappable (flip) + draggable (swipe) flashcard. `question = term` on the front, `answer =
/// definition` on the back; an optional front-of-card image renders above the term.
private struct FlashcardCardView: View {
    let card: Flashcard
    let onSwipeRight: () -> Void
    let onSwipeLeft: () -> Void

    @State private var flipped = false
    @State private var drag: CGSize = .zero

    private let threshold: CGFloat = 120

    var body: some View {
        ZStack {
            face(text: card.question, imageUrl: card.imageUrl, caption: "Tap to flip")
                .opacity(flipped ? 0 : 1)
            face(text: card.answer, imageUrl: nil, caption: "Answer")
                .opacity(flipped ? 1 : 0)
                .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
        }
        .rotation3DEffect(.degrees(flipped ? 180 : 0), axis: (x: 0, y: 1, z: 0))
        .offset(x: drag.width, y: drag.height * 0.15)
        .rotationEffect(.degrees(Double(drag.width) / 25))
        .gesture(
            DragGesture()
                .onChanged { drag = $0.translation }
                .onEnded { value in
                    let width = value.translation.width
                    if width > threshold {
                        swipeAway(right: true)
                    } else if width < -threshold {
                        swipeAway(right: false)
                    } else {
                        withAnimation(.spring) { drag = .zero }
                    }
                }
        )
        .onTapGesture {
            withAnimation(.spring) { flipped.toggle() }
        }
    }

    private func swipeAway(right: Bool) {
        withAnimation(.easeOut(duration: 0.2)) {
            drag.width = right ? 700 : -700
        }
        Task {
            try? await Task.sleep(for: .milliseconds(200))
            // The runner swaps in the next card; this view re-inits via the parent's `.id(position)`.
            right ? onSwipeRight() : onSwipeLeft()
        }
    }

    private func face(text: String, imageUrl: String?, caption: String) -> some View {
        RoundedRectangle(cornerRadius: 20)
            .fill(Color(.secondarySystemBackground))
            .overlay {
                VStack(spacing: Spacing.md) {
                    if let imageUrl, !imageUrl.isEmpty {
                        RemoteCardImage(url: imageUrl)
                            .frame(maxHeight: 220)
                    }
                    if !text.isEmpty {
                        Text(text)
                            .font(.title2.weight(.semibold))
                            .multilineTextAlignment(.center)
                    }
                }
                .padding(Spacing.lg)
            }
            .overlay(alignment: .bottom) {
                Text(caption)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.bottom, Spacing.md)
            }
    }
}

/// Previous / Next navigation. Previous is disabled on the first card.
private struct NavRow: View {
    let canGoBack: Bool
    let onPrevious: () -> Void
    let onNext: () -> Void

    var body: some View {
        HStack {
            Button("Previous", systemImage: "chevron.left", action: onPrevious)
                .disabled(!canGoBack)
            Spacer()
            Button("Next", systemImage: "chevron.right", action: onNext)
                .labelStyle(.titleAndIcon)
        }
        .font(.headline)
    }
}
