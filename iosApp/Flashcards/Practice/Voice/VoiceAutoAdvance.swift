import SwiftUI

/// How long a verdict stays up before a voice run moves on by itself.
///
/// A wrong answer dwells longer: the correct answer only just appeared, and reading it is the entire
/// value of getting it wrong. A right answer has nothing left to read. Matches web and Android.
let voiceAdvanceDelay: Duration = .milliseconds(1500)
let voiceAdvanceDelayIncorrect: Duration = .milliseconds(4000)

/// Clears the verdict by itself so a voice run needs no taps at all (#387).
///
/// Reaching for "Next" is exactly the reach voice exists to remove — the point is answering with the
/// phone across the room. Only ever used in a voice run: a typed or tapped run leaves the pause
/// alone, because that user's hand is already on the screen.
///
/// Any tap cancels for good and restores the button, so auto-advance can never yank a card away from
/// someone reaching to read the answer or to dispute the grade.
struct VoiceAutoAdvance: ViewModifier {
    let active: Bool
    let correct: Bool
    let onAdvance: () -> Void
    @Binding var cancelled: Bool

    @ViewBuilder
    func body(content: Content) -> some View {
        let timed = content
            .task(id: active) {
                guard active, !cancelled else { return }
                try? await Task.sleep(for: correct ? voiceAdvanceDelay : voiceAdvanceDelayIncorrect)
                guard !Task.isCancelled, !cancelled else { return }
                onAdvance()
            }
        if active {
            // Installed only during the dwell, and only in a voice run. A gesture attached to the
            // mode's ScrollView competes with the things inside it — scrolling, and focusing a text
            // field — so it must not exist a moment longer than it's needed (#417). A `.subviews`
            // gesture mask is not enough: the recogniser is still attached either way.
            //
            // simultaneousGesture rather than onTapGesture so it observes the tap without consuming
            // it, letting the control the user was actually reaching for still receive it.
            timed.simultaneousGesture(TapGesture().onEnded { cancelled = true })
        } else {
            timed
        }
    }
}

extension View {
    /// Advances past a verdict on its own in a voice run; see `VoiceAutoAdvance`.
    func voiceAutoAdvance(
        active: Bool,
        correct: Bool,
        cancelled: Binding<Bool>,
        onAdvance: @escaping () -> Void
    ) -> some View {
        modifier(VoiceAutoAdvance(active: active, correct: correct, onAdvance: onAdvance, cancelled: cancelled))
    }
}

/// Stands in for the Next button while a voice run is advancing. Not a button: pressing it is the
/// thing being removed.
struct VoiceAdvanceNotice: View {
    var body: some View {
        Text("Next question…")
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity)
    }
}
