package com.rrbrambley.flashcards.practice.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * How long to give the on-device recogniser to show any sign of life before falling back (#403).
 *
 * Short, because it's a dead loss when it fires: the user has spoken into an engine that will never
 * answer, and every extra second is time they spend wondering. Long enough that a slow cold start
 * isn't mistaken for a missing language pack.
 */
private const val ON_DEVICE_PROBE_MS = 4_000L

/** What the voice panel needs to know about the recogniser, and the handful of things it can ask of it. */
class VoiceRecognizerState internal constructor(
    /** Whether this device can recognise speech at all — no service installed → offer nothing. */
    val supported: Boolean,
    private val listeningState: State<Boolean>,
    private val interimState: State<String>,
    private val errorState: State<VoiceError?>,
    private val startListening: () -> Unit,
    private val cancelListening: () -> Unit,
    private val clearError: () -> Unit,
) {
    val listening: Boolean get() = listeningState.value

    /** The live partial transcript while listening; blank when nothing has been heard yet. */
    val interim: String get() = interimState.value
    val error: VoiceError? get() = errorState.value

    fun start() = startListening()
    fun cancel() = cancelListening()
    fun reset() = clearError()
}

/**
 * Wraps [SpeechRecognizer] for one utterance at a time, exposing the same shape as the web's
 * `useSpeechRecognition` hook (`supported / listening / interim / error` + `start / cancel / reset`)
 * — which is what lets the six UX states be identical across platforms.
 *
 * One utterance, not continuous: the service then detects end-of-speech itself and delivers a final
 * result, which is exactly the "they've finished answering" signal we want. Running continuously
 * would mean inventing silence detection and holding the microphone — and its recording indicator —
 * open between cards.
 *
 * The recogniser is created lazily on the first [VoiceRecognizerState.start] rather than on
 * composition: constructing it is what makes the system treat the app as wanting the mic, and we
 * only want that once the user actually asks to speak.
 *
 * [onFinal] is read through [rememberUpdatedState] so a recomposition never tears down a live
 * recognition mid-utterance.
 */
@Composable
fun rememberVoiceRecognizer(onFinal: (String) -> Unit): VoiceRecognizerState {
    val context = LocalContext.current
    val currentOnFinal by rememberUpdatedState(onFinal)

    val supported = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    val listening = remember { mutableStateOf(false) }
    val interim = remember { mutableStateOf("") }
    val error = remember { mutableStateOf<VoiceError?>(null) }
    val recognizer = remember { mutableStateOf<SpeechRecognizer?>(null) }
    // Whether the live recogniser is the on-device one, which changes how a silent attempt reads (#403).
    val usingOnDevice = remember { mutableStateOf(false) }
    // Any partial or result proves the engine works, so we stop second-guessing it.
    val heardSomething = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val probe = remember { mutableStateOf<Job?>(null) }

    // Tear down with the card. The runner recomposes the mode per card, so leaving a recogniser
    // alive would hold the mic open — and a late result would grade a card that's already gone.
    DisposableEffect(Unit) {
        onDispose {
            probe.value?.cancel()
            recognizer.value?.destroy()
            recognizer.value = null
        }
    }

    return remember(supported, context) {
        VoiceRecognizerState(
            supported = supported,
            listeningState = listening,
            interimState = interim,
            errorState = error,
            startListening = object : () -> Unit {
                override fun invoke() {
                    if (!supported || listening.value) return
                    if (recognizer.value == null) {
                        val (instance, onDevice) = createRecognizer(context)
                        recognizer.value = instance
                        usingOnDevice.value = onDevice
                    }
                    val instance = recognizer.value ?: return

                    // Give up on the on-device engine and retry at once on the service-backed one.
                    // Bounded by [OnDeviceRecognition.markUnusable], so the next create can't pick
                    // on-device again: this happens at most once per process. It is NOT the
                    // auto-restart-after-silence anti-pattern, which would loop the microphone.
                    fun fallBackToService() {
                        probe.value?.cancel()
                        OnDeviceRecognition.markUnusable()
                        runCatching { instance.destroy() }
                        recognizer.value = null
                        usingOnDevice.value = false
                        listening.value = false
                        interim.value = ""
                        invoke()
                    }

                    instance.setRecognitionListener(
                        listenerFor(
                            onReady = {
                                listening.value = true
                                error.value = null
                            },
                            onPartial = {
                                heardSomething.value = true
                                interim.value = it
                            },
                            onFinal = { transcript ->
                                heardSomething.value = true
                                probe.value?.cancel()
                                interim.value = ""
                                listening.value = false
                                currentOnFinal(transcript)
                            },
                            onError = { code ->
                                listening.value = false
                                // "This engine can't serve this at all" — as opposed to "this
                                // utterance didn't work" — disqualifies on-device, not the attempt.
                                if (usingOnDevice.value && isOnDeviceUnusableError(code)) {
                                    fallBackToService()
                                    return@listenerFor
                                }
                                probe.value?.cancel()
                                voiceErrorFor(code)?.let { error.value = it }
                            },
                            onDone = { listening.value = false },
                        ),
                    )

                    heardSomething.value = false
                    probe.value?.cancel()
                    // The signature failure has no error code at all: on-device reports ready and
                    // then never calls back (#402 review). Only a timer can catch that.
                    if (usingOnDevice.value) {
                        probe.value = scope.launch {
                            delay(ON_DEVICE_PROBE_MS)
                            if (!heardSomething.value) fallBackToService()
                        }
                    }
                    instance.startListening(recognitionIntent(context))
                }
            },
            cancelListening = {
                probe.value?.cancel()
                listening.value = false
                interim.value = ""
                recognizer.value?.cancel()
            },
            clearError = {
                error.value = null
                interim.value = ""
            },
        )
    }
}

/**
 * The on-device recogniser when it's worth trying, the service-backed one otherwise (#403).
 *
 * On-device keeps practice audio on the phone — better than the web, where Chrome streams it to
 * Google. It can't be trusted blind, though: `isOnDeviceRecognitionAvailable()` reports the
 * *service*, not whether a language pack was ever downloaded, and with no pack it accepts the
 * request and then goes silent (#402 review). So the caller watches the first attempt and falls
 * back for the rest of the process if it comes to nothing.
 *
 * Returns whether the result is the on-device one, since that decides how a silent or failed
 * attempt should be read.
 */
private fun createRecognizer(context: Context): Pair<SpeechRecognizer, Boolean> {
    val available = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    return if (OnDeviceRecognition.shouldTry(available)) {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context) to true
    } else {
        SpeechRecognizer.createSpeechRecognizer(context) to false
    }
}

private fun recognitionIntent(context: Context): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    // Decks carry no language, so this follows the device — the same known gap as the web (#390).
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    // One hypothesis is enough: we fuzzy-match the transcript ourselves.
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
}

private fun listenerFor(
    onReady: () -> Unit,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onError: (Int) -> Unit,
    onDone: () -> Unit,
) = object : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) = onReady()
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = onDone()
    override fun onError(error: Int) = onError(error)

    override fun onResults(results: Bundle?) {
        val transcript = results?.transcript()
        if (transcript.isNullOrBlank()) onDone() else onFinal(transcript.trim())
    }

    override fun onPartialResults(partialResults: Bundle?) {
        onPartial(partialResults?.transcript()?.trim().orEmpty())
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

private fun Bundle.transcript(): String? = getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
