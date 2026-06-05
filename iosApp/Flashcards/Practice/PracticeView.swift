import Shared
import SwiftUI

/// The practice run (parity with Android's FlashcardsScreen): a flip card, swipe right = correct /
/// left = needs practice, a running score, Previous/Skip, and a completion summary. Presented as a
/// full-screen cover; closes when done.
struct PracticeView: View {
    @StateObject private var viewModel: PracticeViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showHelp = false

    init(flashcardRepository: FlashcardRepository, sessionRepository: PracticeSessionRepository, entry: PracticeEntry) {
        _viewModel = StateObject(
            wrappedValue: PracticeViewModel(
                flashcardRepository: flashcardRepository,
                sessionRepository: sessionRepository,
                entry: entry
            )
        )
    }

    var body: some View {
        NavigationStack {
            content
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") { dismiss() }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { showHelp = true } label: { Image(systemName: "questionmark.circle") }
                    }
                }
                .alert("How to practice", isPresented: $showHelp) {
                    Button("Got it", role: .cancel) {}
                } message: {
                    Text("Tap a card to flip it.\nSwipe right if you got it, left if you need more practice.")
                }
                .task { await viewModel.start() }
        }
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingView()
        case let .showCard(card, position, numCorrect, numIncorrect, canGoBack):
            VStack(spacing: Spacing.lg) {
                ScoreRow(numIncorrect: numIncorrect, numCorrect: numCorrect)
                FlashcardCardView(
                    card: card,
                    onSwipeRight: viewModel.swipeRight,
                    onSwipeLeft: viewModel.swipeLeft
                )
                .id(position)
                .frame(maxHeight: .infinity)
                NavRow(canGoBack: canGoBack, onPrevious: viewModel.goBack, onSkip: viewModel.goForward)
            }
            .padding(Spacing.lg)
        case let .completed(numCorrect, numIncorrect):
            CompletionView(numCorrect: numCorrect, numIncorrect: numIncorrect) { dismiss() }
        case .failed:
            ContentUnavailableView {
                Label("Couldn't start practice", systemImage: "exclamationmark.triangle")
            } description: {
                Text("This deck has no cards, or we couldn't reach the server.")
            } actions: {
                Button("Close") { dismiss() }.buttonStyle(.borderedProminent)
            }
        }
    }
}

/// Running score: needs-practice (red) on the left, correct (green) on the right.
private struct ScoreRow: View {
    let numIncorrect: Int
    let numCorrect: Int

    var body: some View {
        HStack {
            chip(numIncorrect, color: .red, icon: "xmark")
            Spacer()
            chip(numCorrect, color: .green, icon: "checkmark")
        }
    }

    private func chip(_ value: Int, color: Color, icon: String) -> some View {
        Label("\(value)", systemImage: icon)
            .font(.headline)
            .foregroundStyle(color)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .background(color.opacity(0.12), in: Capsule())
    }
}

/// A tappable (flip) + draggable (swipe) flashcard. `question = term` on the front, `answer =
/// definition` on the back. (Front-of-card images arrive in FLA-52.)
private struct FlashcardCardView: View {
    let card: Flashcard
    let onSwipeRight: () -> Void
    let onSwipeLeft: () -> Void

    @State private var flipped = false
    @State private var drag: CGSize = .zero

    private let threshold: CGFloat = 120

    var body: some View {
        ZStack {
            face(text: card.question, caption: "Tap to flip")
                .opacity(flipped ? 0 : 1)
            face(text: card.answer, caption: "Answer")
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
            // The view model swaps in the next card; this view re-inits via `.id(position)`.
            right ? onSwipeRight() : onSwipeLeft()
        }
    }

    private func face(text: String, caption: String) -> some View {
        RoundedRectangle(cornerRadius: 20)
            .fill(Color(.secondarySystemBackground))
            .overlay {
                Text(text.isEmpty ? "—" : text)
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
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

/// Previous / Skip navigation. Previous is disabled on the first card.
private struct NavRow: View {
    let canGoBack: Bool
    let onPrevious: () -> Void
    let onSkip: () -> Void

    var body: some View {
        HStack {
            Button("Previous", systemImage: "chevron.left", action: onPrevious)
                .disabled(!canGoBack)
            Spacer()
            Button("Skip", systemImage: "chevron.right", action: onSkip)
                .labelStyle(.titleAndIcon)
        }
        .font(.headline)
    }
}

/// End-of-deck summary.
private struct CompletionView: View {
    let numCorrect: Int
    let numIncorrect: Int
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: Spacing.lg) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 64))
                .foregroundStyle(.green)
            Text("Practice complete")
                .font(.title.bold())
            HStack(spacing: Spacing.xl) {
                stat("Correct", numCorrect, color: .green)
                stat("To review", numIncorrect, color: .red)
            }
            Button("Done", action: onDone)
                .buttonStyle(.primary)
                .padding(.horizontal, Spacing.xl)
                .padding(.top, Spacing.md)
        }
        .padding(Spacing.lg)
    }

    private func stat(_ label: String, _ value: Int, color: Color) -> some View {
        VStack {
            Text("\(value)").font(.largeTitle.bold()).foregroundStyle(color)
            Text(label).font(.subheadline).foregroundStyle(.secondary)
        }
    }
}
