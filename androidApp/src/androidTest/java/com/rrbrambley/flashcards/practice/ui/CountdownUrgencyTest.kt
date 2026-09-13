package com.rrbrambley.flashcards.practice.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The countdown's urgency tiers (#443) on the timer chip + edge vignette, and the per-second haptic.
 *
 * The haptic assertions are the point of this file: a tick is invisible in code review and on CI, so
 * a fake [HapticFeedback] is the only mechanical way to catch a regression in when they fire.
 */
class CountdownUrgencyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class RecordingHaptics : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    /** Drives the chip + vignette off a mutable second, the way the shared countdown does. */
    private fun setContent(initialSeconds: Int?, haptics: HapticFeedback = RecordingHaptics()): MutableState<Int?> {
        val seconds = mutableStateOf(initialSeconds)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                val urgency = rememberCountdownUrgency(seconds.value)
                seconds.value?.let { TimerChip(remainingSeconds = it, pulse = urgency.pulse) }
                if (urgency.showsVignette) {
                    UrgencyVignette(intensity = urgency.intensity, pulse = urgency.pulse)
                }
            }
        }
        return seconds
    }

    private fun vignette() = composeTestRule.onNodeWithTag(URGENCY_VIGNETTE_TAG, useUnmergedTree = true)

    @Test
    fun aboveTheUrgentTier_showsTheChipWithNoVignette() {
        setContent(12)

        // practice_timer_remaining = "%1$s left"
        composeTestRule.onNodeWithText("0:12 left").assertIsDisplayed()
        vignette().assertDoesNotExist()
    }

    @Test
    fun urgentTier_stillHasNoVignette() {
        // The ≤10s tier is unchanged from #289: a red chip, no glow, no haptics.
        setContent(8)

        composeTestRule.onNodeWithText("0:08 left").assertIsDisplayed()
        vignette().assertDoesNotExist()
    }

    @Test
    fun criticalTier_lightsTheVignette() {
        setContent(4)

        composeTestRule.onNodeWithText("0:04 left").assertIsDisplayed()
        vignette().assertExists()
    }

    @Test
    fun atZero_theVignetteIsGone() {
        // The run is already leaving for the time-up reveal; a final flash would land on a screen
        // that's going away (#443).
        val seconds = setContent(1)
        vignette().assertExists()

        composeTestRule.runOnIdle { seconds.value = 0 }
        composeTestRule.waitForIdle()

        vignette().assertDoesNotExist()
    }

    @Test
    fun haptics_fireOncePerSecondOnlyInTheCriticalTier() {
        val haptics = RecordingHaptics()
        val seconds = setContent(8, haptics)

        // Nothing above 5s…
        composeTestRule.waitForIdle()
        assertEquals(emptyList<HapticFeedbackType>(), haptics.performed)

        composeTestRule.runOnIdle { seconds.value = 6 }
        composeTestRule.waitForIdle()
        assertEquals(emptyList<HapticFeedbackType>(), haptics.performed)

        // …then one per second, escalating to the heavier tick for the final three.
        composeTestRule.runOnIdle { seconds.value = 5 }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { seconds.value = 4 }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { seconds.value = 3 }
        composeTestRule.waitForIdle()

        assertEquals(
            listOf(
                HapticFeedbackType.SegmentTick, // 5s — ramp 0.2
                HapticFeedbackType.SegmentTick, // 4s — ramp 0.4
                HapticFeedbackType.LongPress, // 3s — ramp 0.6, crosses into the heavy tick
            ),
            haptics.performed,
        )
    }

    @Test
    fun haptics_fallSilentWhileTheClockIsHeld() {
        // The pause case (#311), reproduced the way the shared controller presents it: a held clock
        // re-emits nothing, because StateFlow conflates the same Int. So re-setting the identical
        // value must not fire a second tick — that's what keeps the escalation quiet during an
        // image load without any paused flag (#443/#444).
        val haptics = RecordingHaptics()
        val seconds = setContent(4, haptics)
        composeTestRule.waitForIdle()
        assertEquals(1, haptics.performed.size)

        repeat(3) {
            composeTestRule.runOnIdle { seconds.value = 4 }
            composeTestRule.waitForIdle()
        }

        assertEquals(1, haptics.performed.size)
    }

    @Test
    fun haptics_doNotFireAtZero() {
        val haptics = RecordingHaptics()
        val seconds = setContent(1, haptics)
        composeTestRule.waitForIdle()
        val beforeZero = haptics.performed.size

        composeTestRule.runOnIdle { seconds.value = 0 }
        composeTestRule.waitForIdle()

        assertEquals(beforeZero, haptics.performed.size)
    }
}
