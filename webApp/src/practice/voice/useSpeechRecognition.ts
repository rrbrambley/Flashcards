import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * The slice of the Web Speech API's recogniser we use. TypeScript's `lib.dom` ships the *result*
 * types (`SpeechRecognitionEvent`, `SpeechRecognitionErrorEvent`, …) but not the recogniser itself,
 * so declare only what we touch rather than pulling in an ambient-global package.
 */
export interface SpeechRecognizer {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  start(): void;
  stop(): void;
  abort(): void;
  onstart: (() => void) | null;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
}

type SpeechRecognizerCtor = new () => SpeechRecognizer;

interface SpeechWindow {
  SpeechRecognition?: SpeechRecognizerCtor;
  webkitSpeechRecognition?: SpeechRecognizerCtor;
}

/**
 * Resolved on each call rather than captured at module scope, so a test can install a fake before
 * rendering without needing to reset the module registry.
 */
function speechRecognizerCtor(): SpeechRecognizerCtor | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as SpeechWindow;
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

/**
 * Whether this browser can do voice answers at all.
 *
 * The secure-context check matters as much as the constructor one: Chrome refuses the microphone on
 * an insecure origin and reports it as `not-allowed`, which is indistinguishable from the user having
 * blocked it. Dev servers reached by LAN IP (rather than localhost) land exactly there, so detecting
 * it up front turns a confusing permission message into an accurate one.
 */
export function isSpeechRecognitionSupported(): boolean {
  if (speechRecognizerCtor() == null) return false;
  return typeof window === 'undefined' || window.isSecureContext !== false;
}

/** Why listening stopped, reduced to the cases the UI actually distinguishes. */
export type VoiceError = 'denied' | 'no-speech' | 'no-mic' | 'network' | 'unavailable';

export interface SpeechRecognitionHandle {
  supported: boolean;
  listening: boolean;
  /** The live partial transcript while listening; '' when nothing has been heard yet. */
  interim: string;
  error: VoiceError | null;
  /** Begin listening. No-op when unsupported or already listening. */
  start: () => void;
  /** Stop and let the service flush a final result. */
  stop: () => void;
  /** Stop and discard — no final result will arrive. */
  abort: () => void;
  /** Clear the error and any partial transcript (the "try again" reset). */
  reset: () => void;
}

function toVoiceError(code: SpeechRecognitionErrorCode): VoiceError | null {
  switch (code) {
    // We caused this (unmount, or the user cancelling) — not something to report.
    case 'aborted':
      return null;
    case 'not-allowed':
    case 'service-not-allowed':
      return 'denied';
    case 'no-speech':
      return 'no-speech';
    case 'audio-capture':
      return 'no-mic';
    case 'network':
      return 'network';
    default:
      return 'unavailable';
  }
}

/**
 * Wraps the Web Speech API for one utterance at a time.
 *
 * `continuous` is deliberately false: the service then ends the session itself at end-of-utterance
 * and delivers the final result, which is exactly the "they've finished answering" signal we want.
 * Running continuously would mean inventing silence detection and holding the microphone open —
 * and its recording indicator lit — between cards.
 *
 * Note for anyone reading the privacy story: in Chrome and Safari this streams audio to the vendor's
 * speech service. It is not on-device, which is why the UI says so.
 */
export function useSpeechRecognition({
  lang,
  onFinal,
}: {
  lang?: string;
  /** Fired once per utterance with the trimmed final transcript. */
  onFinal: (transcript: string) => void;
}): SpeechRecognitionHandle {
  const [supported] = useState(isSpeechRecognitionSupported);
  const [listening, setListening] = useState(false);
  const [interim, setInterim] = useState('');
  const [error, setError] = useState<VoiceError | null>(null);

  const recognizerRef = useRef<SpeechRecognizer | null>(null);
  const startedRef = useRef(false);
  // Read through a ref so start/stop/abort stay referentially stable: a parent re-render must never
  // reattach handlers or restart recognition mid-utterance. Same idiom as `pausedRef` in useCountdown.
  const onFinalRef = useRef(onFinal);
  useEffect(() => {
    onFinalRef.current = onFinal;
  }, [onFinal]);

  const start = useCallback(() => {
    if (startedRef.current) return;
    const Ctor = speechRecognizerCtor();
    if (Ctor == null || !isSpeechRecognitionSupported()) return;

    const recognizer = new Ctor();
    recognizer.lang = lang ?? (typeof navigator === 'undefined' ? 'en-US' : navigator.language);
    recognizer.continuous = false;
    recognizer.interimResults = true;
    // One hypothesis is enough: we fuzzy-match the transcript ourselves.
    recognizer.maxAlternatives = 1;

    recognizer.onstart = () => {
      setListening(true);
      setError(null);
    };
    recognizer.onresult = (event) => {
      let final = '';
      let partial = '';
      // Normally a single result with continuous=false; iterating is cheap insurance.
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        const text = result[0]?.transcript ?? '';
        if (result.isFinal) final += text;
        else partial += text;
      }
      if (final !== '') {
        setInterim('');
        // Don't stop() here — the service ends the session itself and a manual stop races it.
        onFinalRef.current(final.trim());
      } else {
        setInterim(partial);
      }
    };
    recognizer.onerror = (event) => {
      const mapped = toVoiceError(event.error);
      if (mapped != null) setError(mapped);
    };
    recognizer.onend = () => {
      startedRef.current = false;
      setListening(false);
    };

    recognizerRef.current = recognizer;
    startedRef.current = true;
    try {
      recognizer.start();
    } catch {
      // Chrome throws InvalidStateError if the service is still winding down from a previous
      // utterance; the flag can desync from its actual state, so recover rather than surface it.
      startedRef.current = false;
    }
  }, [lang]);

  const stop = useCallback(() => {
    try {
      recognizerRef.current?.stop();
    } catch {
      /* already stopped */
    }
  }, []);

  const abort = useCallback(() => {
    try {
      recognizerRef.current?.abort();
    } catch {
      /* already stopped */
    }
  }, []);

  const reset = useCallback(() => {
    setError(null);
    setInterim('');
  }, []);

  useEffect(
    () => () => {
      const recognizer = recognizerRef.current;
      if (recognizer == null) return;
      // Detach before aborting: a late onresult after unmount would grade a card that's already gone
      // (the runner remounts the mode per card, so unmount is also the between-cards teardown).
      recognizer.onstart = null;
      recognizer.onresult = null;
      recognizer.onerror = null;
      recognizer.onend = null;
      try {
        recognizer.abort();
      } catch {
        /* already stopped */
      }
    },
    [],
  );

  return { supported, listening, interim, error, start, stop, abort, reset };
}
