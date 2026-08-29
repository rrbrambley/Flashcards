import { useCallback, useEffect, useRef, useState } from 'react';
import { useSpeechRecognition } from '../voice/useSpeechRecognition';

/**
 * How long a heard answer is shown before it's submitted.
 *
 * This window is the whole reason retry is possible. Auto-submitting is the point of voice — speak,
 * it grades, next card — but once the answer is graded it's recorded and there's no un-grading it.
 * So the correction has to happen *before* submission, not after.
 */
export const VOICE_SUBMIT_DELAY_MS = 1500;

/** What a final transcript means, when the caller needs to interpret it (Multiple Choice, #388). */
export interface VoiceInterpretation {
  /** The hypothesis to show and submit — the caller's pick from the ones offered. */
  transcript: string;
  /** Shown alongside the transcript, e.g. "Matched: Paris". */
  note?: string;
}

/**
 * Answer by speaking. Owns the recogniser and the states around it; owns no grading — the transcript
 * goes to [onSubmit] and the mode grades it exactly as it would a typed answer.
 *
 * Rendered *alongside* a mode's normal input, never instead of it. That's what makes every failure
 * mode survivable: unsupported browser, denied microphone, or a misrecognition all leave typing right
 * there.
 */
export function VoiceAnswerInput({
  onSubmit,
  interpret,
  lang,
  onDisableVoice,
}: {
  onSubmit: (transcript: string) => void;
  /**
   * Picks which of the recogniser's hypotheses to use, and what to show for it. `null` means "didn't
   * catch that" — the panel re-prompts instead of submitting.
   *
   * The list arrives best-ranked first and is usually one entry. Callers that know what a right
   * answer looks like can re-rank it (#390); the default simply takes the recogniser's own pick.
   */
  interpret?: (transcripts: string[]) => VoiceInterpretation | null;
  lang?: string;
  /** Offered on an unrecoverable error, so a stuck user can switch voice off without navigating away. */
  onDisableVoice?: () => void;
}) {
  const [heard, setHeard] = useState<{ transcript: string; note?: string } | null>(null);
  const [unheard, setUnheard] = useState(false);
  const panelRef = useRef<HTMLDivElement | null>(null);

  const onFinal = useCallback(
    (transcripts: string[]) => {
      const heardAnything = transcripts.length > 0;
      // Not `interpret?.(t) ?? fallback` — `??` would turn a deliberate null ("didn't understand
      // that") back into an accepted answer, which is exactly the case this exists to catch.
      const interpretation = !heardAnything
        ? null
        : interpret
          ? interpret(transcripts)
          : { transcript: transcripts[0] };
      if (interpretation == null || interpretation.transcript.trim() === '') {
        setUnheard(true);
        return;
      }
      setUnheard(false);
      setHeard({ transcript: interpretation.transcript.trim(), note: interpretation.note });
    },
    [interpret],
  );

  const { supported, listening, interim, error, start, abort, reset } = useSpeechRecognition({ lang, onFinal });

  // Start on mount, i.e. once per card. Both ways in ("Start practice", then "Next") follow a recent
  // click, so the browser's permission prompt is well motivated; requiring a tap per card would be a
  // click-tax on the entire feature.
  useEffect(() => {
    start();
  }, [start]);

  const submitNow = useCallback(() => {
    if (heard == null) return;
    setHeard(null);
    // Keep the payload sane regardless of what the recogniser produced; the server clamps too (#391).
    onSubmit(heard.transcript.slice(0, 200));
  }, [heard, onSubmit]);

  const retry = useCallback(() => {
    setHeard(null);
    setUnheard(false);
    reset();
    start();
  }, [reset, start]);

  // The grace window.
  useEffect(() => {
    if (heard == null) return;
    const id = setTimeout(submitNow, VOICE_SUBMIT_DELAY_MS);
    return () => clearTimeout(id);
  }, [heard, submitNow]);

  // Taking over with the keyboard cancels a pending submit — but Enter means "submit now" rather than
  // "cancel", or in Test mode it would reach the empty text field and raise the blank-skip confirm
  // instead of the answer just spoken.
  useEffect(() => {
    if (heard == null) return;
    const onKey = (e: KeyboardEvent) => {
      // The panel's own buttons are activated by key events; those must not count as taking over.
      // (`contains` throws in jsdom when the target isn't a Node — a window-level key event.)
      if (e.target instanceof Node && panelRef.current?.contains(e.target)) return;
      if (e.key === 'Enter') {
        e.preventDefault();
        submitNow();
      } else if (e.key === 'Escape') {
        retry();
      } else {
        setHeard(null);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [heard, submitNow, retry]);

  if (!supported) {
    return <p className="voice-input voice-unsupported">Voice answers aren't supported in this browser.</p>;
  }

  return (
    <div className="voice-input" ref={panelRef}>
      <div className="voice-status" aria-live="polite">
        {heard != null && (
          <>
            <span className="voice-heard">Heard: “{heard.transcript}”</span>
            {heard.note && <span className="voice-note">{heard.note}</span>}
          </>
        )}
        {heard == null && listening && (
          <span className="voice-interim">{interim === '' ? 'Listening…' : interim}</span>
        )}
        {heard == null && unheard && <span className="voice-error">Didn't catch that.</span>}
        {heard == null && error === 'no-speech' && !unheard && <span className="voice-error">Didn't catch that.</span>}
        {error === 'denied' && (
          <span className="voice-error">
            Microphone blocked. Allow access from your browser's address bar, or type your answer.
          </span>
        )}
        {error === 'no-mic' && <span className="voice-error">No microphone found.</span>}
        {error === 'network' && <span className="voice-error">Speech service unreachable — type your answer.</span>}
        {error === 'unavailable' && <span className="voice-error">Voice answers aren't available right now.</span>}
      </div>

      <div className="voice-actions">
        {heard != null ? (
          <>
            <button type="button" onClick={submitNow}>
              Submit now
            </button>
            <button type="button" className="secondary" onClick={retry}>
              Retry
            </button>
          </>
        ) : listening ? (
          <button type="button" className="secondary voice-mic listening" aria-pressed onClick={abort}>
            🎤 Stop
          </button>
        ) : (
          // Retrying after a denial can't succeed — Chrome fails a re-start silently — so offer the
          // way out instead of a button that does nothing.
          error !== 'denied' &&
          error !== 'no-mic' && (
            <button type="button" className="secondary voice-mic" aria-pressed={false} onClick={retry}>
              🎤 {unheard || error != null ? 'Try again' : 'Speak answer'}
            </button>
          )
        )}
        {(error === 'denied' || error === 'no-mic') && onDisableVoice && (
          <button type="button" className="link-btn" onClick={onDisableVoice}>
            Turn off voice answers
          </button>
        )}
      </div>

      {heard == null && error == null && (
        <p className="voice-privacy">🔒 Speech is processed by your browser's speech service.</p>
      )}
    </div>
  );
}
