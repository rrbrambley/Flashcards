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

/** What a final transcript means, when the caller needs to interpret it (Multiple Choice). */
data class VoiceInterpretation(val note: String? = null)

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
     * Whether a transcript is usable, and what to show for it. `null` means "didn't catch that" —
     * the panel re-prompts instead of submitting. Default: any non-blank transcript is accepted.
     */
    interpret: ((String) -> VoiceInterpretation?)? = null,
    /** Offered when the mic is unusable, so a stuck user can switch voice off without navigating away. */
    onDisableVoice: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var heard by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var unheard by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val recognizer = rememberVoiceRecognizer { transcript ->
        val interpretation = if (interpret != null) interpret(transcript) else VoiceInterpretation()
        if (interpretation == null) {
            unheard = true
        } else {
            unheard = false
            heard = transcript to interpretation.note
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

    // The grace window: submit unless the user intervenes first.
    val pending = heard
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        delay(VOICE_SUBMIT_DELAY_MS)
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
                            if (unheard || recognizer.error != null) {
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

            if (pending == null && recognizer.error == null && !permissionDenied) {
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
