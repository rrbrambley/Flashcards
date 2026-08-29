import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useSpeechRecognition, isSpeechRecognitionSupported, VOICE_MAX_ALTERNATIVES } from './useSpeechRecognition';
import { FakeSpeechRecognition, installFakeSpeechRecognition } from '../../test/fakeSpeechRecognition';

describe('useSpeechRecognition', () => {
  describe('without a recogniser (Firefox)', () => {
    it('reports unsupported and start() does nothing', () => {
      const onFinal = vi.fn();
      const { result } = renderHook(() => useSpeechRecognition({ onFinal }));
      expect(result.current.supported).toBe(false);
      act(() => result.current.start());
      expect(result.current.listening).toBe(false);
      expect(onFinal).not.toHaveBeenCalled();
    });
  });

  describe('with a recogniser', () => {
    let uninstall: () => void;
    beforeEach(() => {
      uninstall = installFakeSpeechRecognition();
    });
    afterEach(() => uninstall());

    const start = (onFinal = vi.fn(), lang?: string) => {
      const view = renderHook(() => useSpeechRecognition({ onFinal, lang }));
      act(() => view.result.current.start());
      return { view, onFinal };
    };

    it('configures the recogniser for one utterance with live partials', () => {
      start(vi.fn(), 'en-GB');
      const recognizer = FakeSpeechRecognition.last;
      // continuous=false is what gives us end-of-utterance detection (and auto-submit) for free.
      expect(recognizer.continuous).toBe(false);
      expect(recognizer.interimResults).toBe(true);
      expect(recognizer.lang).toBe('en-GB');
    });

    it('surfaces partial results without submitting them', () => {
      const { view, onFinal } = start();
      act(() => FakeSpeechRecognition.last.say('par', false));
      expect(view.result.current.interim).toBe('par');
      expect(onFinal).not.toHaveBeenCalled();
    });

    it('reports a final result once, trimmed', () => {
      const { onFinal } = start();
      act(() => FakeSpeechRecognition.last.say('  paris  '));
      expect(onFinal).toHaveBeenCalledExactlyOnceWith(['paris']);
    });

    it('asks the recogniser for more than one hypothesis', () => {
      start();
      // One hypothesis can't be re-ranked, and re-ranking is the whole of #390.
      expect(FakeSpeechRecognition.last.maxAlternatives).toBe(VOICE_MAX_ALTERNATIVES);
    });

    it('reports every hypothesis, best-ranked first', () => {
      const { onFinal } = start();
      act(() => FakeSpeechRecognition.last.sayAll(['jibooty', 'djibouti']));
      expect(onFinal).toHaveBeenCalledExactlyOnceWith(['jibooty', 'djibouti']);
    });

    it('drops blank and duplicate hypotheses', () => {
      const { onFinal } = start();
      act(() => FakeSpeechRecognition.last.sayAll(['chad', '  ', 'chad', ' chad ']));
      expect(onFinal).toHaveBeenCalledExactlyOnceWith(['chad']);
    });

    it.each([
      ['not-allowed', 'denied'],
      ['service-not-allowed', 'denied'],
      ['no-speech', 'no-speech'],
      ['audio-capture', 'no-mic'],
      ['network', 'network'],
      ['language-not-supported', 'unavailable'],
    ] as const)('maps %s to %s', (code, expected) => {
      const { view } = start();
      act(() => FakeSpeechRecognition.last.fail(code));
      expect(view.result.current.error).toBe(expected);
    });

    it('does not surface an abort as an error — we caused it', () => {
      const { view } = start();
      act(() => FakeSpeechRecognition.last.fail('aborted'));
      expect(view.result.current.error).toBeNull();
    });

    /**
     * Safari doesn't reliably fire `onend` after `abort()` (#396). Waiting for it left `listening`
     * true, so the panel kept showing "Stop" and the button looked dead while the mic stayed live.
     */
    it('stops listening on abort even when the browser never fires onend', () => {
      const { view } = start();
      FakeSpeechRecognition.last.endsOnAbort = false;

      act(() => view.result.current.abort());

      expect(view.result.current.listening).toBe(false);
      expect(view.result.current.interim).toBe('');
    });

    it('can start again after an abort the browser never acknowledged', () => {
      const { view } = start();
      const first = FakeSpeechRecognition.last;
      first.endsOnAbort = false;
      act(() => view.result.current.abort());

      act(() => view.result.current.start());

      // A fresh recogniser, actually listening — not the stuck one.
      expect(FakeSpeechRecognition.last).not.toBe(first);
      expect(view.result.current.listening).toBe(true);
    });

    it('ignores a result from a recogniser that was aborted', () => {
      const { view, onFinal } = start();
      const recognizer = FakeSpeechRecognition.last;
      recognizer.endsOnAbort = false;
      act(() => view.result.current.abort());

      act(() => recognizer.say('too late'));

      expect(onFinal).not.toHaveBeenCalled();
    });

    it('survives a double start rather than throwing', () => {
      const { view } = start();
      expect(() => act(() => view.result.current.start())).not.toThrow();
    });

    /**
     * The runner remounts the mode per card, so unmount *is* the between-cards teardown. A late result
     * arriving after it would grade a card that's already gone.
     */
    it('ignores a result that arrives after unmount', () => {
      const { view, onFinal } = start();
      const recognizer = FakeSpeechRecognition.last;
      view.unmount();

      recognizer.onresult?.({
        resultIndex: 0,
        results: Object.assign([Object.assign([{ transcript: 'late', confidence: 1 }], { isFinal: true })], {}),
      } as unknown as SpeechRecognitionEvent);

      expect(onFinal).not.toHaveBeenCalled();
      expect(recognizer.aborted).toBe(true);
    });
  });

  describe('isSpeechRecognitionSupported', () => {
    it('is false on an insecure origin, where the mic fails as a confusing permission error', () => {
      const uninstall = installFakeSpeechRecognition();
      const original = window.isSecureContext;
      Object.defineProperty(window, 'isSecureContext', { value: false, configurable: true });
      expect(isSpeechRecognitionSupported()).toBe(false);
      Object.defineProperty(window, 'isSecureContext', { value: original, configurable: true });
      uninstall();
    });
  });
});
