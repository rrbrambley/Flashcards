package com.rrbrambley.flashcards.practice.voice

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.rrbrambley.flashcards.R
import kotlinx.coroutines.delay

/**
 * How long a verdict stays up before a voice run moves on by itself.
 *
 * A wrong answer dwells longer: the correct answer only just appeared, and reading it is the entire
 * value of getting it wrong. A right answer has nothing left to read. Matches the web.
 */
const val VOICE_ADVANCE_DELAY_MS = 1500L
const val VOICE_ADVANCE_DELAY_INCORRECT_MS = 4000L

/**
 * Clears the verdict by itself so a voice run needs no taps at all (#387).
 *
 * Reaching for "Next" is exactly the reach voice exists to remove — the point is answering with the
 * phone across the room. Only ever enabled for a voice run: a typed or tapped run leaves the pause
 * alone, because that user's hand is already on the screen.
 *
 * Returns whether the card is counting down, which the caller renders in place of its Next button.
 */
@Composable
fun rememberVoiceAutoAdvance(active: Boolean, correct: Boolean, onAdvance: () -> Unit): Boolean {
    var cancelled by remember { mutableStateOf(false) }
    val currentOnAdvance by rememberUpdatedState(onAdvance)
    val advancing = active && !cancelled

    LaunchedEffect(advancing, correct) {
        if (!advancing) return@LaunchedEffect
        delay(if (correct) VOICE_ADVANCE_DELAY_MS else VOICE_ADVANCE_DELAY_INCORRECT_MS)
        currentOnAdvance()
    }

    return advancing
}

/**
 * Cancels a pending auto-advance on any touch anywhere in this subtree, restoring the Next button.
 *
 * Auto-advance must never yank a card away from someone reaching for it — to read the answer, or to
 * hit "This should be correct". Observed in the [PointerEventPass.Initial] pass so the touch still
 * reaches whatever the user was actually aiming at.
 */
fun Modifier.cancelVoiceAdvanceOnTouch(enabled: Boolean, onCancel: () -> Unit): Modifier {
    if (!enabled) {
        return this
    }
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onCancel()
        }
    }
}

/**
 * Stands in for the Next button while a voice run is advancing. Not a button: pressing it is the
 * thing being removed.
 */
@Composable
fun VoiceAdvanceNotice(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.practice_voice_advancing),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
