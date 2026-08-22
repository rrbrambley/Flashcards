import AVFoundation
import Speech
import SwiftUI

/// How long a pause counts as "they've finished answering".
///
/// `SFSpeechAudioBufferRecognitionRequest` does **not** detect end-of-utterance: it emits a final
/// result only once the audio stream ends, so without this the recogniser transcribes happily and
/// never submits (#409 review). The web (`continuous = false`) and Android's `SpeechRecognizer` both
/// provide that signal themselves — iOS makes us find it.
///
/// 1.2s is long enough to survive a mid-answer breath and short enough that "Paris" doesn't feel
/// like it hung. Combined with the 1.5s grace window that's ~2.7s from speech to grade, comparable
/// to the web's ~1s recogniser lag plus the same grace.
private let silenceWindow: Duration = .milliseconds(1200)

/// How long to wait for *any* speech before giving up, so a card can't sit listening forever with
/// the recording indicator lit.
private let noSpeechWindow: Duration = .seconds(6)

/// Why listening stopped, reduced to the cases the UI actually distinguishes.
///
/// Mirrors the web's `VoiceError` and Android's, so the UX states are one product across platforms
/// even though the three recognisers underneath share nothing.
enum VoiceError: Equatable {
    /// Speech recognition or the microphone was refused. Retrying can't help — the user must grant it.
    case denied
    /// Heard nothing usable. Offer an explicit retry; never restart on our own.
    case noSpeech
    /// Audio capture couldn't start.
    case noMic
    /// The recognition service is unreachable.
    case network
    /// Anything else — recognition can't serve this request right now.
    case unavailable
}

/// Wraps `SFSpeechRecognizer` + `AVAudioEngine` for one utterance at a time.
///
/// Exposes the same surface as the web hook and the Android wrapper — `supported / listening /
/// interim / error` plus `start / cancel / reset` — which is what lets the six UX states port even
/// though the recogniser doesn't.
///
/// One utterance, not continuous: `start` runs until the recogniser reports a final result or the
/// user stops. Holding the mic open between cards would keep the system's recording indicator lit
/// and give us nothing, since we want the end-of-answer signal anyway.
@MainActor
final class VoiceRecognizer: ObservableObject {
    @Published private(set) var listening = false
    /// The live partial transcript while listening; empty when nothing has been heard yet.
    @Published private(set) var interim = ""
    @Published private(set) var error: VoiceError?

    /// Whether this device can recognise speech at all. False → offer nothing, like Firefox on web.
    let supported: Bool

    private let recognizer: SFSpeechRecognizer?
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var onFinal: ((String) -> Void)?
    /// Fires when the user stops talking; ending the audio is what makes iOS deliver a final result.
    private var silenceTask: Task<Void, Never>?

    init(locale: Locale = .current) {
        // A recogniser is nil for an unsupported locale; `isAvailable` covers a temporary outage.
        let recognizer = SFSpeechRecognizer(locale: locale)
        self.recognizer = recognizer
        self.supported = recognizer != nil
    }

    deinit {
        // Not `cancel()` — deinit is nonisolated and tearing down the engine here would hop actors
        // during deallocation. The tap and engine are released with the object.
        silenceTask?.cancel()
        task?.cancel()
    }

    /// Requests speech + microphone permission, then starts listening. Both are separate grants on
    /// iOS and either can be refused independently.
    ///
    /// Called only from a deliberate user action or from a card appearing *after* permission is
    /// already held — never speculatively, so the system alerts arrive when they're motivated.
    func start(onFinal: @escaping (String) -> Void) {
        guard supported, !listening else { return }
        self.onFinal = onFinal

        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            Task { @MainActor in
                guard let self else { return }
                guard status == .authorized else {
                    self.error = .denied
                    return
                }
                await self.requestMicrophoneThenListen()
            }
        }
    }

    private func requestMicrophoneThenListen() async {
        let granted = await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { continuation.resume(returning: $0) }
        }
        guard granted else {
            error = .denied
            return
        }
        beginListening()
    }

    private func beginListening() {
        guard let recognizer, recognizer.isAvailable else {
            error = .unavailable
            return
        }

        do {
            let session = AVAudioSession.sharedInstance()
            // `.duckOthers` rather than interrupting: practice may be running alongside music, and
            // silencing it outright for a two-second answer is heavy-handed.
            try session.setCategory(.record, mode: .measurement, options: [.duckOthers])
            try session.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            self.error = .noMic
            return
        }

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        // Keep the audio on the device when the locale supports it — a materially better privacy
        // story than the web's, where Chrome streams it to Google. Falls back automatically.
        if recognizer.supportsOnDeviceRecognition {
            request.requiresOnDeviceRecognition = true
        }
        self.request = request

        let input = audioEngine.inputNode
        let format = input.outputFormat(forBus: 0)
        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            request.append(buffer)
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
        } catch {
            self.error = .noMic
            teardownAudio()
            return
        }

        listening = true
        self.error = nil
        interim = ""
        // Nothing heard yet, so allow the longer window before giving up entirely.
        restartSilenceTimer(heardSomething: false)

        task = recognizer.recognitionTask(with: request) { [weak self] result, taskError in
            Task { @MainActor in
                guard let self else { return }
                if let result {
                    let transcript = result.bestTranscription.formattedString
                    if result.isFinal {
                        self.finish(with: transcript)
                        return
                    }
                    self.interim = transcript
                    // Still talking — push the end-of-answer deadline back.
                    self.restartSilenceTimer(heardSomething: true)
                }
                if let taskError {
                    self.handle(taskError)
                }
            }
        }
    }

    /// Ends the audio stream after a pause, which is the only thing that makes `SFSpeechRecognizer`
    /// deliver `isFinal`. Restarted on every partial result, so a slow speaker is never cut off.
    private func restartSilenceTimer(heardSomething: Bool) {
        silenceTask?.cancel()
        silenceTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: heardSomething ? silenceWindow : noSpeechWindow)
            guard let self, !Task.isCancelled, self.listening else { return }
            self.request?.endAudio()
        }
    }

    /// Stops listening without reporting anything — the user's own "Stop", or moving on.
    func cancel() {
        silenceTask?.cancel()
        task?.cancel()
        task = nil
        finishRequest()
        teardownAudio()
        listening = false
        interim = ""
    }

    /// Clears a previous failure so a retry starts from a clean slate.
    func reset() {
        error = nil
        interim = ""
    }

    private func finish(with transcript: String) {
        silenceTask?.cancel()
        finishRequest()
        teardownAudio()
        listening = false
        interim = ""
        let trimmed = transcript.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            error = .noSpeech
        } else {
            onFinal?(trimmed)
        }
    }

    private func handle(_ taskError: Error) {
        silenceTask?.cancel()
        finishRequest()
        teardownAudio()
        listening = false
        let nsError = taskError as NSError
        // Cancellation is ours (the user stopped, or the card moved on) — reporting it would flash
        // an error on every advance. Same reason web ignores `aborted` and Android ERROR_CLIENT.
        if nsError.code == 203 || nsError.domain == NSCocoaErrorDomain && nsError.code == NSUserCancelledError {
            return
        }
        // "No speech detected" is a re-promptable outcome, not a malfunction — the same thing the
        // other platforms report as no-speech rather than as an error the user can't act on.
        if nsError.domain == "kAFAssistantErrorDomain" && nsError.code == 1110 {
            error = .noSpeech
            return
        }
        error = nsError.domain == NSURLErrorDomain ? .network : .unavailable
    }

    private func finishRequest() {
        request?.endAudio()
        request = nil
    }

    private func teardownAudio() {
        if audioEngine.isRunning { audioEngine.stop() }
        audioEngine.inputNode.removeTap(onBus: 0)
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
