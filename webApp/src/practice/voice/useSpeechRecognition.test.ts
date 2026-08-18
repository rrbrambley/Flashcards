import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useSpeechRecognition, isSpeechRecognitionSupported } from './useSpeechRecognition';
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
      expect(onFinal).toHaveBeenCalledExactlyOnceWith('paris');
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
