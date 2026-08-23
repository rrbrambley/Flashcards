package com.rrbrambley.flashcards.practice.voice

import android.speech.SpeechRecognizer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The decision half of #403 — which is the part that *can* be tested off-device. Whether the
 * on-device engine actually works can only be learned by trying it on real hardware; what we do
 * with that answer is ordinary logic, and worth pinning.
 */
class OnDeviceRecognitionTest {

    @Before
    @After
    fun resetProcessState() {
        OnDeviceRecognition.resetForTests()
    }

    @Test
    fun `on-device is tried when the platform offers it`() {
        assertTrue(OnDeviceRecognition.shouldTry(available = true))
    }

    @Test
    fun `on-device is not tried when the platform doesn't offer it`() {
        assertFalse(OnDeviceRecognition.shouldTry(available = false))
    }

    /**
     * The whole point of the retry: the cost of discovering a missing language pack is paid once,
     * not on every card. Without this the user would speak into a dead engine repeatedly.
     */
    @Test
    fun `once proven unusable, on-device is skipped even though the platform still offers it`() {
        OnDeviceRecognition.markUnusable()

        assertFalse(OnDeviceRecognition.shouldTry(available = true))
    }

    /**
     * Deliberately process-scoped rather than persisted: someone who installs the language pack
     * later should get on-device recognition back on the next app start, not be written off
     * permanently by a one-time probe.
     */
    @Test
    fun `the verdict does not outlive the process`() {
        OnDeviceRecognition.markUnusable()
        assertFalse(OnDeviceRecognition.shouldTry(available = true))

        // resetForTests stands in for a fresh process.
        OnDeviceRecognition.resetForTests()

        assertTrue(OnDeviceRecognition.shouldTry(available = true))
    }

    @Test
    fun `errors meaning 'this engine can never serve this' disqualify on-device`() {
        assertTrue(isOnDeviceUnusableError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
        assertTrue(isOnDeviceUnusableError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertTrue(isOnDeviceUnusableError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT))
    }

    /**
     * These are about the *attempt*, not the engine. Treating them as a verdict would abandon
     * on-device — and its privacy benefit — because the user happened to say nothing once.
     */
    @Test
    fun `errors about the attempt do not disqualify on-device`() {
        assertFalse(isOnDeviceUnusableError(SpeechRecognizer.ERROR_NO_MATCH))
        assertFalse(isOnDeviceUnusableError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertFalse(isOnDeviceUnusableError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertFalse(isOnDeviceUnusableError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertFalse(isOnDeviceUnusableError(SpeechRecognizer.ERROR_NETWORK))
        assertFalse(isOnDeviceUnusableError(SpeechRecognizer.ERROR_AUDIO))
    }

    /**
     * The #411 case. A hollow on-device engine can report ERROR_NO_MATCH instead of going silent,
     * which looks exactly like a user who said nothing — and treating it as an ordinary miss leaves
     * them stuck on "Didn't catch that" forever, because every retry fails identically.
     *
     * `onBeginningOfSpeech` separates them: the engine heard someone start talking and still
     * produced nothing, which a working recogniser doesn't do.
     */
    @Test
    fun `hearing speech and transcribing nothing is the engine's failure, not the speaker's`() {
        assertTrue(
            onDeviceProvedHollow(
                code = SpeechRecognizer.ERROR_NO_MATCH,
                detectedSpeech = true,
                producedTranscript = false,
            ),
        )
        assertTrue(
            onDeviceProvedHollow(
                code = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                detectedSpeech = true,
                producedTranscript = false,
            ),
        )
    }

    /** Nobody spoke, so there's nothing to conclude about the engine. */
    @Test
    fun `a miss with no speech detected is just a miss`() {
        assertFalse(
            onDeviceProvedHollow(
                code = SpeechRecognizer.ERROR_NO_MATCH,
                detectedSpeech = false,
                producedTranscript = false,
            ),
        )
    }

    /** It transcribed something at some point, so it demonstrably works. */
    @Test
    fun `an engine that produced a transcript is never written off`() {
        assertFalse(
            onDeviceProvedHollow(
                code = SpeechRecognizer.ERROR_NO_MATCH,
                detectedSpeech = true,
                producedTranscript = true,
            ),
        )
    }

    /** The engine reporting its own incapacity needs no corroboration from the speaker. */
    @Test
    fun `an explicit unusable error stands on its own`() {
        assertTrue(
            onDeviceProvedHollow(
                code = SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                detectedSpeech = false,
                producedTranscript = false,
            ),
        )
    }

    /** A refused permission or a dead network says nothing about the engine's model. */
    @Test
    fun `environmental failures never disqualify the engine`() {
        for (code in listOf(
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        )) {
            assertFalse(
                "code $code should not disqualify on-device",
                onDeviceProvedHollow(code = code, detectedSpeech = true, producedTranscript = false),
            )
        }
    }
}
