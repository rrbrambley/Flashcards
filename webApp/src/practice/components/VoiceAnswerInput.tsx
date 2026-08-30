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

/**
 * Slack allowed for the countdown being behind reality.
 *
 * `useCountdown` ticks once a second, so the remaining time we're handed can overstate what's left by
 * nearly a full tick. Anything tighter than this and the window could still outlive the clock.
 */
export const VOICE_DEADLINE_SLACK_MS = 1000;

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
  remainingMs = Infinity,
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
  /** Time left in a timed run (#289); `Infinity` when untimed. See the grace-window effect (#426). */
  remainingMs?: number;
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

  /**
   * Read through a ref so the ticking clock can't be an effect dependency.
   *
   * The countdown re-renders this component once a second. With `remainingMs` in the deps below, each
   * tick tore down the pending timeout and started a fresh one — and since a tick (1s) lands inside
   * the window (1.5s), it could never finish. The answer just sat there, never submitting, in every
   * timed run. Same idiom as `pausedRef` in useCountdown and `onFinalRef` above.
   */
  const remainingMsRef = useRef(remainingMs);
  useEffect(() => {
    remainingMsRef.current = remainingMs;
  }, [remainingMs]);

  /**
   * The grace window — unless the clock would beat it (#426).
   *
   * In a timed run this delay is *our* pause, not the user's. If it outlives the deadline the answer
   * is never submitted, and the run ends scoring the card unanswered — a spoken, correct, in-time
   * answer marked wrong. So when the window no longer fits, the answer goes in at once.
   *
   * Nothing is lost by that: retrying needs time to re-speak *and* be re-recognised, so in the last
   * couple of seconds Retry was never really on offer. This drops an affordance that couldn't have
   * been used, to keep an answer that would otherwise have been thrown away.
   *
   * Decided once, when the transcript arrives — which is also the only moment it matters.
   */
  useEffect(() => {
    if (heard == null) return;
    const fits = remainingMsRef.current > VOICE_SUBMIT_DELAY_MS + VOICE_DEADLINE_SLACK_MS;
    // Still scheduled rather than called outright when it doesn't fit: submitting from inside the
    // effect body would set state synchronously during the commit.
    const id = setTimeout(submitNow, fits ? VOICE_SUBMIT_DELAY_MS : 0);
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
