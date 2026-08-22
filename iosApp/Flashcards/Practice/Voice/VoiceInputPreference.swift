import Foundation
import Speech

/// Whether the user wants to answer by speaking — a *local* preference, deliberately not a property
/// of the session.
///
/// Voice is how you're answering right now, not something about the run: grading and resume are
/// identical either way, so putting it on the session would have cost a DTO field, a backend column,
/// a Room migration and three client session-creation chains for no behavioural gain (#386).
///
/// Orthogonal to the `practice_voice_input` feature flag: the flag decides whether voice is
/// *offered*, this decides whether it's *on*.
enum VoiceInputPreference {
    private static let enabledKey = "practice.voiceInput"
    private static let noticeSeenKey = "practice.voiceInput.privacyNoticeSeen"

    static var isEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: enabledKey) }
        set { UserDefaults.standard.set(newValue, forKey: enabledKey) }
    }

    /// Whether the one-time speech-processing disclosure has been shown.
    ///
    /// It's a disclosure, not a label: repeated on every card it becomes furniture the user stops
    /// reading, which is worse for informed consent than showing it once and meaning it.
    static var privacyNoticeSeen: Bool {
        get { UserDefaults.standard.bool(forKey: noticeSeenKey) }
        set { UserDefaults.standard.set(newValue, forKey: noticeSeenKey) }
    }

    /// Whether speech recognition *and* the microphone have both already been granted.
    ///
    /// Used to decide whether a card may start listening on its own. Deliberately checks the current
    /// status rather than requesting: a card appearing is not a reason to raise a permission alert.
    @MainActor
    static var permissionsAlreadyGranted: Bool {
        SFSpeechRecognizer.authorizationStatus() == .authorized
            && AVAudioApplication.shared.recordPermission == .granted
    }
}
