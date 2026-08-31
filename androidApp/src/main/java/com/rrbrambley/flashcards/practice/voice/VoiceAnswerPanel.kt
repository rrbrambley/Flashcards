package com.rrbrambley.flashcards.practice.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rrbrambley.flashcards.R
import kotlinx.coroutines.delay

/**
 * How long a heard answer is shown before it's submitted.
 *
 * This window is the whole reason retry is possible. Auto-submitting is the point of voice — speak,
 * it grades, next card — but once the answer is graded it's recorded and there's no un-grading it.
 * So the correction has to happen *before* submission, not after. Matches the web's
 * `VOICE_SUBMIT_DELAY_MS`.
 */
const val VOICE_SUBMIT_DELAY_MS = 1500L

/**
 * How many hypotheses to ask the recogniser for.
 *
 * More than one because the ranking is done by a general-purpose language model biased toward
 * everyday words, which is exactly why proper nouns lose — a country name is outscored by whatever
 * common phrase it sounds like (#390). Callers know something the recogniser doesn't (the card's
 * answer, or the four options on screen) and can re-rank with it.
 *
 * Kept small: alternatives past the first few are acoustically remote, and every extra one is
 * another chance to accept something that wasn't said. Matches the web's `VOICE_MAX_ALTERNATIVES`.
 */
const val VOICE_MAX_ALTERNATIVES = 4

/**
 * Least time that must remain in a timed run for the grace window to be worth starting (#426).
 *
 * The remaining time ticks in whole seconds, so it can overstate what's left by nearly a second;
 * this covers that plus the window itself. Matches the web's `VOICE_SUBMIT_DELAY_MS` +
 * `VOICE_DEADLINE_SLACK_MS`.
 */
private const val VOICE_DEADLINE_FLOOR_SECONDS = 3

/**
 * How long to wait for the recogniser to say *anything* before giving up on it.
 *
 * A recogniser can accept `startListening`, report itself ready, and then never call back — no
 * partial, no result, no error (#402 review). Without this the panel sits on "Listening…" with a
 * Stop button and no explanation, which reads as "the app is broken" rather than "try again".
 * Any partial transcript resets the clock, so a slow speaker is never cut off.
 */
private const val VOICE_LISTEN_TIMEOUT_MS = 12_000L

/** What a final transcript means, when the caller needs to interpret it (Multiple Choice). */
data class VoiceInterpretation(
    /** The hypothesis to show and submit — the caller's pick from the ones offered. */
    val transcript: String,
    /** Shown alongside the transcript, e.g. the option that was matched. */
    val note: String? = null,
)

/**
 * Answer by speaking. Owns the recogniser and the states around it; owns no grading — the transcript
 * goes to [onSubmit] and the mode grades it exactly as it would a typed answer.
 *
 * Rendered *alongside* a mode's normal input, never instead of it. That's what makes every failure
 * mode survivable: no recognition service, a denied microphone, or a misrecognition all leave the
 * card fully answerable by typing or tapping.
 *
 * The microphone permission is requested on the *first tap of the mic*, not on composition — a
 * permission dialog the moment a card appears would be asking before it's needed.
 */
@Composable
fun VoiceAnswerPanel(
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Picks which of the recogniser's hypotheses to use, and what to show for it. `null` means
     * "didn't catch that" — the panel re-prompts instead of submitting.
     *
     * The list arrives best-ranked first and is usually one entry. Callers that know what a right
     * answer looks like can re-rank it (#390); the default simply takes the recogniser's own pick.
     */
    interpret: ((List<String>) -> VoiceInterpretation?)? = null,
    /** Offered when the mic is unusable, so a stuck user can switch voice off without navigating away. */
    onDisableVoice: (() -> Unit)? = null,
    /** Whether the one-time speech-processing disclosure still needs showing. */
    showPrivacyNotice: Boolean = false,
    /** Called once the disclosure has actually been on screen for a card. */
    onPrivacyNoticeShown: () -> Unit = {},
    /** Seconds left in a timed run (#289), or null when untimed. See the grace window below (#426). */
    remainingSeconds: Int? = null,
) {
    val context = LocalContext.current
    var heard by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var unheard by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    // Set when the recogniser went quiet on us — see VOICE_LISTEN_TIMEOUT_MS.
    var timedOut by remember { mutableStateOf(false) }

    val recognizer = rememberVoiceRecognizer { hypotheses ->
        val interpretation =
            if (interpret != null) interpret(hypotheses) else VoiceInterpretation(hypotheses.first())
        if (interpretation == null || interpretation.transcript.isBlank()) {
            unheard = true
        } else {
            unheard = false
            heard = interpretation.transcript.trim() to interpretation.note
        }
    }

    var pendingStart by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        // Start straight away on a grant: the user tapped the mic to speak, so make them tap once,
        // not twice.
        if (granted && pendingStart) recognizer.start()
        pendingStart = false
    }

    fun listen() {
        unheard = false
        timedOut = false
        recognizer.reset()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionDenied = false
            recognizer.start()
        } else {
            pendingStart = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Start listening as soon as the card appears — but only when the microphone is already granted.
    // The panel is composed once per card (the mode swaps it out for the verdict once answered), so
    // this fires exactly once per question, which is what makes a run genuinely hands-free: tapping
    // "Speak answer" on every card is the tap voice exists to remove.
    //
    // Never auto-requests the permission: that's still owed to a deliberate tap. And never re-fires
    // after a stop, an error or a "didn't catch that" — auto-restarting would loop the microphone and
    // leave its indicator lit with no way out.
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted && recognizer.supported) recognizer.start()
    }

    // Mark the disclosure seen when the card is done with, not the moment it renders: flipping it
    // mid-card would make the text vanish under the user as they read it.
    DisposableEffect(showPrivacyNotice) {
        onDispose { if (showPrivacyNotice) onPrivacyNoticeShown() }
    }

    // Watchdog. Keyed on the interim transcript too, so any sign of life restarts it.
    LaunchedEffect(recognizer.listening, recognizer.interim) {
        if (!recognizer.listening) return@LaunchedEffect
        delay(VOICE_LISTEN_TIMEOUT_MS)
        recognizer.cancel()
        timedOut = true
    }

    // The grace window: submit unless the user intervenes first — unless the clock would beat it.
    //
    // In a timed run this delay is *ours*, not the user's. Left to outlive the deadline the answer is
    // never submitted and the run scores the card unanswered, marking a spoken, correct, in-time
    // answer wrong (#426). Retrying needs time to re-speak *and* be re-recognised, so in the last
    // couple of seconds the window was offering something that couldn't be used anyway.
    val pending = heard
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        val timeToSpare = remainingSeconds == null || remainingSeconds >= VOICE_DEADLINE_FLOOR_SECONDS
        if (timeToSpare) delay(VOICE_SUBMIT_DELAY_MS)
        heard = null
        // Keep the payload sane whatever the recogniser produced; the server clamps too (#391).
        onSubmit(pending.first.take(200))
    }

    if (!recognizer.supported) {
        Text(
            text = stringResource(R.string.practice_voice_unsupported),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    val blocked = permissionDenied || recognizer.error == VoiceError.Denied || recognizer.error == VoiceError.NoMic

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VoiceStatus(
                heard = pending,
                listening = recognizer.listening,
                interim = recognizer.interim,
                unheard = unheard,
                error = recognizer.error,
                permissionDenied = permissionDenied,
                timedOut = timedOut,
            )

            if (pending != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            heard = null
                            onSubmit(pending.first.take(200))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.practice_voice_submit_now)) }
                    OutlinedButton(
                        onClick = {
                            heard = null
                            listen()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.practice_voice_retry)) }
                }
            } else if (recognizer.listening) {
                OutlinedButton(onClick = { recognizer.cancel() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.practice_voice_stop))
                }
            } else if (!blocked) {
                // Retrying a blocked mic can't succeed, so that case gets the way out below instead
                // of a button that does nothing.
                OutlinedButton(onClick = { listen() }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (unheard || timedOut || recognizer.error != null) {
                                R.string.practice_voice_try_again
                            } else {
                                R.string.practice_voice_speak
                            },
                        ),
                    )
                }
            }

            if (blocked && onDisableVoice != null) {
                TextButton(onClick = onDisableVoice) {
                    Text(stringResource(R.string.practice_voice_turn_off))
                }
            }

            if (showPrivacyNotice && pending == null && recognizer.error == null && !permissionDenied && !timedOut) {
                Text(
                    text = stringResource(R.string.practice_voice_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun VoiceStatus(
    heard: Pair<String, String?>?,
    listening: Boolean,
    interim: String,
    unheard: Boolean,
    error: VoiceError?,
    permissionDenied: Boolean,
    timedOut: Boolean,
) {
    when {
        heard != null -> {
            Text(
                text = stringResource(R.string.practice_voice_heard, heard.first),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            heard.second?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        listening -> Text(
            text = interim.ifBlank { stringResource(R.string.practice_voice_listening) },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        timedOut -> VoiceMessage(R.string.practice_voice_timed_out)
        permissionDenied || error == VoiceError.Denied -> VoiceMessage(R.string.practice_voice_denied)
        error == VoiceError.NoMic -> VoiceMessage(R.string.practice_voice_no_mic)
        error == VoiceError.Network -> VoiceMessage(R.string.practice_voice_network)
        error == VoiceError.Unavailable -> VoiceMessage(R.string.practice_voice_unavailable)
        unheard || error == VoiceError.NoSpeech -> VoiceMessage(R.string.practice_voice_unheard)
        else -> VoiceMessage(R.string.practice_voice_prompt)
    }
}

@Composable
private fun VoiceMessage(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
