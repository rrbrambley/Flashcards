import SwiftUI

/// How long a heard answer is shown before it's submitted.
///
/// This window is the whole reason retry is possible. Auto-submitting is the point of voice — speak,
/// it grades, next card — but once the answer is graded it's recorded and there's no un-grading it.
/// So the correction has to happen *before* submission, not after. Matches web and Android.
let voiceSubmitDelay: Duration = .milliseconds(1500)

/// What a final transcript means, when the caller needs to interpret it (Multiple Choice).
struct VoiceInterpretation {
    /// Shown alongside the transcript, e.g. the option that was matched.
    var note: String?
}

/// Answer by speaking. Owns the recogniser and the states around it; owns no grading — the
/// transcript goes to `onSubmit` and the mode grades it exactly as it would a typed answer.
///
/// Rendered *alongside* a mode's normal input, never instead of it. That's what makes every failure
/// mode survivable: an unsupported locale, a refused microphone, or a misrecognition all leave the
/// card fully answerable by typing or tapping.
struct VoiceAnswerPanel: View {
    let onSubmit: (String) -> Void
    /// Whether a transcript is usable, and what to show for it. `nil` means "didn't catch that" —
    /// the panel re-prompts instead of submitting. Default: any non-blank transcript is accepted.
    var interpret: ((String) -> VoiceInterpretation?)?
    /// Offered when the mic is unusable, so a stuck user can switch voice off without leaving practice.
    var onDisableVoice: (() -> Void)?
    /// Whether the one-time speech-processing disclosure still needs showing.
    var showPrivacyNotice = false
    /// Called once the disclosure has actually been on screen for a card.
    var onPrivacyNoticeShown: () -> Void = {}

    @StateObject private var recognizer = VoiceRecognizer()
    @State private var heard: Heard?
    @State private var unheard = false
    @State private var submitTask: Task<Void, Never>?

    private struct Heard {
        let transcript: String
        let note: String?
    }

    var body: some View {
        Group {
            if recognizer.supported {
                panel
            } else {
                Text("Voice answers aren't supported on this device.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        }
        .task {
            // Start as soon as the card appears, but only when both permissions are already held.
            // Tapping to speak on every card is the tap voice exists to remove; raising a permission
            // alert just because a card appeared would be asking before it's needed.
            if recognizer.supported, VoiceInputPreference.permissionsAlreadyGranted {
                listen()
            }
        }
        .onDisappear {
            submitTask?.cancel()
            recognizer.cancel()
            // Mark the disclosure seen when the card is done with, not the moment it renders:
            // flipping it mid-card would make the text vanish under the user as they read it.
            if showPrivacyNotice { onPrivacyNoticeShown() }
        }
    }

    private var panel: some View {
        VStack(spacing: Spacing.sm) {
            status
            actions
            if showPrivacyNotice, heard == nil, recognizer.error == nil {
                Text("Speech is processed by your device's speech service.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.md)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
    }

    @ViewBuilder
    private var status: some View {
        if let heard {
            Text("Heard: “\(heard.transcript)”")
                .font(.headline)
                .multilineTextAlignment(.center)
            if let note = heard.note {
                // Runtime value, so it can't be a literal — localized by the caller that built it.
                Text(note)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        } else if recognizer.listening {
            Text(recognizer.interim.isEmpty ? String(localized: "Listening…") : recognizer.interim)
                .font(.headline)
                .multilineTextAlignment(.center)
        } else if let error = recognizer.error {
            message(for: error)
        } else if unheard {
            secondary("Didn't catch that.")
        } else {
            secondary("Say your answer")
        }
    }

    @ViewBuilder
    private var actions: some View {
        if let heard {
            HStack(spacing: Spacing.sm) {
                Button("Submit now") { submitNow(heard.transcript) }
                    .buttonStyle(.secondary)
                Button("Retry") {
                    submitTask?.cancel()
                    self.heard = nil
                    listen()
                }
                .buttonStyle(.secondary)
            }
        } else if recognizer.listening {
            Button("Stop") { recognizer.cancel() }
                .buttonStyle(.secondary)
        } else if blocked {
            // Retrying a refused permission can't succeed, so offer the way out instead of a button
            // that does nothing. iOS can only be re-granted from Settings.
            if let onDisableVoice {
                Button("Turn off voice answers", action: onDisableVoice)
                    .buttonStyle(.secondary)
            }
        } else {
            Button(unheard || recognizer.error != nil ? "Try again" : "Speak answer") { listen() }
                .buttonStyle(.secondary)
        }
    }

    private var blocked: Bool {
        recognizer.error == .denied || recognizer.error == .noMic
    }

    @ViewBuilder
    private func message(for error: VoiceError) -> some View {
        switch error {
        case .denied:
            secondary("Microphone or speech access is off. Turn it on in Settings, or answer without voice.")
        case .noMic:
            secondary("Couldn't start recording.")
        case .network:
            secondary("Speech service unreachable — answer without voice.")
        case .unavailable:
            secondary("Voice answers aren't available right now.")
        case .noSpeech:
            secondary("Didn't catch that.")
        }
    }

    private func secondary(_ text: LocalizedStringKey) -> some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }

    private func listen() {
        unheard = false
        recognizer.reset()
        recognizer.start { transcript in
            let interpretation = interpret.map { $0(transcript) } ?? VoiceInterpretation()
            guard let interpretation else {
                // nil re-prompts. Never fall back to a best guess: an auto-submitted wrong answer is
                // recorded against the card and can't be un-graded.
                unheard = true
                return
            }
            unheard = false
            heard = Heard(transcript: transcript, note: interpretation.note)
            startGraceWindow(transcript)
        }
    }

    private func startGraceWindow(_ transcript: String) {
        submitTask?.cancel()
        submitTask = Task {
            try? await Task.sleep(for: voiceSubmitDelay)
            guard !Task.isCancelled else { return }
            submitNow(transcript)
        }
    }

    private func submitNow(_ transcript: String) {
        submitTask?.cancel()
        heard = nil
        // Keep the payload sane whatever the recogniser produced; the server clamps too (#391).
        onSubmit(String(transcript.prefix(200)))
    }
}
