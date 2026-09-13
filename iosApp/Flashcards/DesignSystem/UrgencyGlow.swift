import Shared
import SwiftUI

/// Red used by the vignette and the ≤10s chip — matches Android and the web glow. See #443 on why
/// this isn't centralised into a palette yet.
let urgencyRed = Color(red: 0.83, green: 0.24, blue: 0.24)
/// Deeper red for the final 5s. Deeper rather than brighter: the text is white, and a more saturated
/// red would cut the contrast where this lifts it.
let urgencyRedCritical = Color(red: 0.55, green: 0.11, blue: 0.09)

/// Vignette alpha at rest, and the extra it gains at the peak of a tick's flash (× intensity).
private let vignetteRestingAlpha = 0.16
private let vignetteFlashAlpha = 0.54

/// How much the chip grows at the peak of a tick's flash.
let chipPulseScale = 0.12

/// Above this ramp — the final three seconds — the tick switches to the heavier haptic.
private let heavyHapticFrom: Float = 0.6

extension View {
    /// The timed-run urgency treatment for the final seconds (#443): a pulsing red vignette at the
    /// edges of the screen, plus a haptic tick per second.
    ///
    /// Attach to the `NavigationStack`, **not** to its content — content sits inside the toolbar and
    /// safe area, so a glow there stops at the app bar instead of reading as the screen's edges.
    ///
    /// Pass nil for `remainingSeconds` when the run isn't showing a card, so the glow clears on the
    /// time-up reveal and the recap.
    func urgencyGlow(remainingSeconds: Int?) -> some View {
        modifier(UrgencyGlowModifier(remainingSeconds: remainingSeconds))
    }
}

/// Whether this second should carry the escalation. Stops short of 0: the run is already leaving for
/// the time-up screen, so a final hit would land on a view that's going away (#443).
func isCriticalSecond(_ remainingSeconds: Int?) -> Bool {
    guard let seconds = remainingSeconds, seconds > 0 else { return false }
    return TimedUrgency.shared.isCritical(remainingSeconds: Int32(seconds))
}

/// 0...1 ramp for a critical second; 0 otherwise.
func urgencyIntensity(_ remainingSeconds: Int?) -> Double {
    guard let seconds = remainingSeconds, isCriticalSecond(seconds) else { return 0 }
    return Double(TimedUrgency.shared.intensity(remainingSeconds: Int32(seconds)))
}

private struct UrgencyGlowModifier: ViewModifier {
    let remainingSeconds: Int?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .overlay {
                if isCriticalSecond(remainingSeconds), let seconds = remainingSeconds {
                    UrgencyVignette(
                        ramp: urgencyIntensity(seconds),
                        reduceMotion: reduceMotion,
                        trigger: seconds
                    )
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                    .transition(.opacity)
                }
            }
            // Fires only on a *change*, so a clock held for a prompt image (#311) — which re-emits the
            // same Int, conflated away by StateFlow — is silent with no extra code. Returning nil means
            // "no feedback this time", so the modifier can sit here for the whole run.
            // SwiftUI owns generator lifecycle and honours System Haptics + Low Power Mode; a
            // UIImpactFeedbackGenerator would mean hand-rolling prepare() and the enable check.
            .sensoryFeedback(trigger: remainingSeconds) { _, new in
                guard isCriticalSecond(new), let seconds = new else { return nil }
                let ramp = TimedUrgency.shared.intensity(remainingSeconds: Int32(seconds))
                return ramp >= heavyHapticFrom
                    ? .impact(weight: .heavy, intensity: 1.0)
                    : .impact(weight: .light, intensity: 0.7)
            }
    }
}

/// The edge vignette itself. `keyframeAnimator(initialValue:trigger:)` runs the track once per change
/// of `trigger` and comes to rest back at `initialValue` — exactly a per-tick flash.
///
/// Note what this is *not*: `flash = 1; withAnimation { flash = 0 }` would be coalesced into a single
/// transaction and you'd typically only ever see the end state.
private struct UrgencyVignette: View {
    let ramp: Double
    let reduceMotion: Bool
    let trigger: Int

    var body: some View {
        GeometryReader { proxy in
            let radius = max(proxy.size.width, proxy.size.height) * 0.72
            Rectangle()
                .fill(
                    RadialGradient(
                        stops: [
                            .init(color: .clear, location: 0.45),
                            .init(color: urgencyRed, location: 1.0),
                        ],
                        center: .center,
                        startRadius: 0,
                        endRadius: radius
                    )
                )
                .keyframeAnimator(initialValue: 0.0, trigger: trigger) { view, flash in
                    // Rests at a low tint rather than 0: between flashes the time really is nearly up,
                    // so the edges stay lit — only the motion is per-tick. Reduce Motion holds it there.
                    view.opacity(
                        reduceMotion
                            ? ramp * 0.24
                            : ramp * (vignetteRestingAlpha + vignetteFlashAlpha * flash)
                    )
                } keyframes: { _ in
                    KeyframeTrack {
                        CubicKeyframe(1.0, duration: 0.08)
                        CubicKeyframe(0.0, duration: 0.52)
                    }
                }
        }
    }
}

/// Announces the urgency tiers to VoiceOver — once on reaching 10s, once on reaching 5s.
///
/// Keyed on the *tier*, not the reading: the chip's label changes every second, so announcing that
/// would interrupt itself ten times over and drown out the card. The tier only ever deepens, so
/// hitting 0 (where the chip's critical styling drops) doesn't fall back and re-announce "10 seconds".
struct UrgencyAnnouncement: ViewModifier {
    let remainingSeconds: Int?

    private var tier: Int? {
        guard let seconds = remainingSeconds, seconds >= 0 else { return nil }
        if TimedUrgency.shared.isCritical(remainingSeconds: Int32(seconds)) { return 5 }
        if TimedUrgency.shared.isUrgent(remainingSeconds: Int32(seconds)) { return 10 }
        return nil
    }

    func body(content: Content) -> some View {
        content.onChange(of: tier) { _, newTier in
            guard let newTier, UIAccessibility.isVoiceOverRunning else { return }
            AccessibilityNotification.Announcement(
                String(localized: "\(newTier) seconds remaining")
            ).post()
        }
    }
}

extension View {
    /// See `UrgencyAnnouncement`.
    func urgencyAnnouncement(remainingSeconds: Int?) -> some View {
        modifier(UrgencyAnnouncement(remainingSeconds: remainingSeconds))
    }
}
