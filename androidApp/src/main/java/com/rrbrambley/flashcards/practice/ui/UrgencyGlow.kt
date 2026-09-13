package com.rrbrambley.flashcards.practice.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.testTag
import com.rrbrambley.flashcards.R
import com.rrbrambley.flashcards.shared.domain.TimedUrgency

/** Test handle for the edge vignette — it has no text or role of its own to find it by. */
internal const val URGENCY_VIGNETTE_TAG = "urgency-vignette"

/** Matches the chip's red and the web glow; see #443 on why this isn't centralised yet. */
private val UrgencyRed = Color(0xFFD33D3D)

/** Vignette alpha at rest, and the extra it gains at the peak of a tick's flash (× intensity). */
private const val VIGNETTE_RESTING_ALPHA = 0.16f
private const val VIGNETTE_FLASH_ALPHA = 0.54f

/** How much the chip grows at the peak of a tick's flash. */
internal const val CHIP_PULSE_SCALE = 0.12f

/** Above this ramp — the final three seconds — the tick switches to the heavier haptic. */
private const val HEAVY_HAPTIC_FROM = 0.6f

private const val FLASH_DECAY_MILLIS = 520

/**
 * The countdown's urgency state for a timed run (#443): the per-tick flash that drives the chip and
 * the edge vignette, plus the haptic tick and the tier announcement.
 *
 * [pulse] is deliberately a lambda, not a `Float`. Reading the animation in composition would
 * recompose the whole practice screen at 60fps; callers read it inside `graphicsLayer {}` /
 * `onDrawBehind {}` so invalidation stays in the draw phase.
 */
@Stable
internal class CountdownUrgency(
    /** 0f..1f — how hard this second should land. 0f outside the critical tier. */
    val intensity: Float,
    /** Whether the edge vignette should be on screen at all. */
    val showsVignette: Boolean,
    val pulse: () -> Float,
)

/**
 * Drives [CountdownUrgency] off the shared countdown.
 *
 * **The flash is retriggered by each new second, never by a looping animation.** That matters for the
 * paused case: while the clock is held for a prompt image (#311) the shared controller keeps
 * assigning the *same* `Int` to `remainingSeconds`, `StateFlow` conflates equal values, so nothing is
 * emitted, [LaunchedEffect] isn't re-keyed, and the flash simply decays to rest while the haptics
 * fall silent — with no paused flag needed anywhere (#443/#444). A `rememberInfiniteTransition` would
 * keep pulsing against a frozen clock and drift out of phase with the digit besides.
 *
 * Pass null for [remainingSeconds] when the run isn't showing a card, so the glow clears on the
 * time-up reveal and the recap.
 */
@Composable
internal fun rememberCountdownUrgency(remainingSeconds: Int?): CountdownUrgency {
    val reduceMotion = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    val flash = remember { Animatable(0f) }
    // Stops short of 0: the run is already leaving for the time-up screen, so a final hit would land
    // on a view that's unmounting (#443).
    val critical = remainingSeconds != null && remainingSeconds > 0 && TimedUrgency.isCritical(remainingSeconds)
    val intensity = if (critical) TimedUrgency.intensity(remainingSeconds!!) else 0f

    LaunchedEffect(remainingSeconds, reduceMotion) {
        if (!critical) return@LaunchedEffect
        // Haptics before the reduce-motion check: Reduce Motion is a vestibular accommodation, and a
        // haptic isn't motion — it's the accessible channel for this feature.
        haptics.performHapticFeedback(
            // SegmentTick is the platform's countdown/scrubber tick; it routes through
            // HapticFeedbackConstantsCompat, so the API-34 fallback is androidx's problem, not ours.
            if (intensity >= HEAVY_HAPTIC_FROM) HapticFeedbackType.LongPress else HapticFeedbackType.SegmentTick,
        )
        if (reduceMotion) return@LaunchedEffect
        // A new second cancels the in-flight decay and re-arms from the top — that *is* the retrigger.
        flash.snapTo(1f)
        flash.animateTo(0f, tween(durationMillis = FLASH_DECAY_MILLIS, easing = FastOutLinearInEasing))
    }

    return remember(intensity, critical) {
        CountdownUrgency(intensity = intensity, showsVignette = critical, pulse = { flash.value })
    }
}

/**
 * Android has no `accessibilityReduceMotion`; the animator duration scale is the public proxy, and
 * what the platform's own animation code checks. Read once — nobody flips it mid-session, so a live
 * `ContentObserver` would be overkill.
 */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * The red vignette at the edges of the screen for a timed run's final seconds. Peripheral by design,
 * so it reads without covering the card being answered — and it carries only draw modifiers, so it's
 * touch-transparent and can't intercept an answer tap.
 */
@Composable
internal fun UrgencyVignette(intensity: Float, pulse: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            // Decorative: cleared from the a11y tree, keeping only a handle for tests.
            .clearAndSetSemantics { testTag = URGENCY_VIGNETTE_TAG }
            .drawWithCache {
                val brush = Brush.radialGradient(
                    0.45f to Color.Transparent,
                    1.0f to UrgencyRed,
                    center = size.center,
                    radius = maxOf(size.width, size.height) * 0.72f,
                )
                onDrawBehind {
                    val alpha = intensity * (VIGNETTE_RESTING_ALPHA + VIGNETTE_FLASH_ALPHA * pulse())
                    drawRect(brush, alpha = alpha.coerceIn(0f, 1f))
                }
            },
    )
}

/**
 * Announces the urgency tiers to TalkBack — once on reaching 10s, once on reaching 5s.
 *
 * Keyed on the *tier*, not the reading: the chip's text changes every second, so a live region on it
 * would interrupt itself ten times over and drown out the card. Note the tier only ever deepens, so
 * hitting 0 (where the chip's critical styling drops) doesn't fall back and re-announce "10 seconds".
 */
@Composable
internal fun UrgencyAnnouncement(remainingSeconds: Int?, modifier: Modifier = Modifier) {
    val seconds = remainingSeconds ?: return
    val announcement = when {
        TimedUrgency.isCritical(seconds) -> stringResource(R.string.practice_timer_urgency_announcement, 5)
        TimedUrgency.isUrgent(seconds) -> stringResource(R.string.practice_timer_urgency_announcement, 10)
        else -> return
    }
    Box(
        modifier.clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = announcement
        },
    )
}
