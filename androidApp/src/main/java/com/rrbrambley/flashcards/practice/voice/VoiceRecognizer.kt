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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

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

    // Tear down with the card. The runner recomposes the mode per card, so leaving a recogniser
    // alive would hold the mic open — and a late result would grade a card that's already gone.
    DisposableEffect(Unit) {
        onDispose {
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
            startListening = {
                if (supported && !listening.value) {
                    val instance = recognizer.value ?: createRecognizer(context).also { recognizer.value = it }
                    instance.setRecognitionListener(
                        listenerFor(
                            onReady = {
                                listening.value = true
                                error.value = null
                            },
                            onPartial = { interim.value = it },
                            onFinal = { transcript ->
                                interim.value = ""
                                listening.value = false
                                currentOnFinal(transcript)
                            },
                            onError = { code ->
                                listening.value = false
                                voiceErrorFor(code)?.let { error.value = it }
                            },
                            onDone = { listening.value = false },
                        ),
                    )
                    instance.startListening(recognitionIntent(context))
                }
            },
            cancelListening = {
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
 * On-device recognition keeps the audio on the phone, which is a materially better privacy story
 * than the web's (where Chrome streams it to Google). It needs API 33+, though, and `minSdk` is 26 —
 * so fall back to the service-backed recogniser, which is what the web effectively always uses.
 */
private fun createRecognizer(context: Context): SpeechRecognizer =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    ) {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    } else {
        SpeechRecognizer.createSpeechRecognizer(context)
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
