import type { SpeechRecognizer } from '../practice/voice/useSpeechRecognition';

/**
 * A stand-in for the Web Speech API's recogniser, driven by hand from a test.
 *
 * Installed per test file rather than in `setup.ts` on purpose: putting it in global setup would make
 * every existing practice test run as though the browser supported voice, silently rendering UI they
 * never meant to exercise.
 */
export class FakeSpeechRecognition implements SpeechRecognizer {
  static instances: FakeSpeechRecognition[] = [];

  /** The recogniser the component is currently driving. */
  static get last(): FakeSpeechRecognition {
    const instance = FakeSpeechRecognition.instances.at(-1);
    if (instance == null) throw new Error('No FakeSpeechRecognition was constructed');
    return instance;
  }

  lang = '';
  continuous = false;
  interimResults = false;
  maxAlternatives = 1;
  started = false;
  aborted = false;

  onstart: (() => void) | null = null;
  onresult: ((event: SpeechRecognitionEvent) => void) | null = null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null = null;
  onend: (() => void) | null = null;

  constructor() {
    FakeSpeechRecognition.instances.push(this);
  }

  start(): void {
    // Chrome throws if you start an already-running session; the hook has to survive that.
    if (this.started) throw new DOMException('already started', 'InvalidStateError');
    this.started = true;
    this.onstart?.();
  }

  stop(): void {
    this.started = false;
    this.onend?.();
  }

  abort(): void {
    this.aborted = true;
    this.started = false;
    this.onend?.();
  }

  /** Emit a result. A final one also ends the session, as the real API does with `continuous = false`. */
  say(transcript: string, isFinal = true): void {
    this.onresult?.(resultEvent(transcript, isFinal));
    if (isFinal) {
      this.started = false;
      this.onend?.();
    }
  }

  /** Emit an error, followed by the `onend` the real API always fires after one. */
  fail(code: SpeechRecognitionErrorCode): void {
    this.onerror?.({ error: code } as SpeechRecognitionErrorEvent);
    this.started = false;
    this.onend?.();
  }
}

/**
 * jsdom can't construct a `SpeechRecognitionResultList`, and the hook indexes it, reads `.length` and
 * checks `isFinal` — so build it from real arrays and let the array carry the extra members.
 */
function resultEvent(transcript: string, isFinal: boolean): SpeechRecognitionEvent {
  const alternative = { transcript, confidence: 0.9 };
  const result = Object.assign([alternative], { isFinal, item: () => alternative });
  const results = Object.assign([result], { item: () => result });
  return { resultIndex: 0, results } as unknown as SpeechRecognitionEvent;
}

/** Makes the browser look voice-capable. Returns the uninstaller. */
export function installFakeSpeechRecognition(): () => void {
  FakeSpeechRecognition.instances = [];
  const had = 'SpeechRecognition' in window;
  Object.defineProperty(window, 'SpeechRecognition', {
    value: FakeSpeechRecognition,
    configurable: true,
    writable: true,
  });
  return () => {
    FakeSpeechRecognition.instances = [];
    // `restoreMocks` restores spies, not defined globals — so put it back by hand, or the
    // unsupported-browser cases in the same file would still see a recogniser.
    if (had) return;
    Reflect.deleteProperty(window, 'SpeechRecognition');
  };
}
