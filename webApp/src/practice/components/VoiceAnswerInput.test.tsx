import { StrictMode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import { VoiceAnswerInput, VOICE_SUBMIT_DELAY_MS } from './VoiceAnswerInput';
import { FakeSpeechRecognition, installFakeSpeechRecognition } from '../../test/fakeSpeechRecognition';

describe('VoiceAnswerInput', () => {
  let uninstall: () => void;

  beforeEach(() => {
    uninstall = installFakeSpeechRecognition();
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
    uninstall();
  });

  // fireEvent rather than user-event, matching BatchPracticeRunner.test.tsx: user-event's clicks
  // never resolve against these fake timers, and nothing here needs a realistic pointer sequence.
  const click = (name: string | RegExp) =>
    act(() => {
      fireEvent.click(screen.getByRole('button', { name }));
    });

  const speak = (transcript: string) => act(() => FakeSpeechRecognition.last.say(transcript));

  /**
   * The app runs in StrictMode, which mounts → cleans up → mounts again on the same fiber, so refs
   * survive. The unmount cleanup detaches `onend` (a late result would grade a card that's gone),
   * and `onend` is what clears the "already started" flag — so leaving it set made the second
   * start() a no-op and the panel sat idle, never listening. Every other test here mounts once and
   * cannot see it.
   */
  it('listens after a StrictMode double-mount, not just the first one', () => {
    render(<VoiceAnswerInput onSubmit={vi.fn()} />, { wrapper: StrictMode });

    expect(FakeSpeechRecognition.last.started).toBe(true);
    expect(screen.getByText('Listening…')).toBeInTheDocument();
  });

  it('shows the live partial transcript while listening', () => {
    render(<VoiceAnswerInput onSubmit={vi.fn()} />);
    act(() => FakeSpeechRecognition.last.say('par', false));
    expect(screen.getByText('par')).toBeInTheDocument();
  });

  it('holds a heard answer briefly, then submits it', () => {
    const onSubmit = vi.fn();
    render(<VoiceAnswerInput onSubmit={onSubmit} />);
    speak('paris');

    // The pause is the only window in which a misrecognition can be corrected — once submitted the
    // answer is graded and recorded, and there's no un-grading it.
    expect(screen.getByText(/Heard/)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();

    act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));
    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('paris');
  });

  it('submits immediately on "Submit now"', () => {
    const onSubmit = vi.fn();
    render(<VoiceAnswerInput onSubmit={onSubmit} />);
    speak('paris');
    click('Submit now');
    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('paris');
  });

  it('cancels the pending submit and listens again on Retry', () => {
    const onSubmit = vi.fn();
    render(<VoiceAnswerInput onSubmit={onSubmit} />);
    speak('paris');
    click('Retry');

    act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS * 2));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('lets the keyboard take over, but Enter means submit rather than cancel', () => {
    const onSubmit = vi.fn();
    const { unmount } = render(<VoiceAnswerInput onSubmit={onSubmit} />);
    speak('paris');
    // Enter must not cancel: in Test mode it would reach the empty text field and raise the
    // blank-skip confirm instead of the answer just spoken.
    act(() => {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    });
    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('paris');
    unmount();

    // Any other key means they've started typing — drop the spoken answer.
    onSubmit.mockClear();
    render(<VoiceAnswerInput onSubmit={onSubmit} />);
    speak('lyon');
    act(() => {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'p', bubbles: true }));
    });
    act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('re-prompts instead of submitting when the caller can\'t interpret the transcript', () => {
    const onSubmit = vi.fn();
    render(<VoiceAnswerInput onSubmit={onSubmit} interpret={() => null} />);
    speak('banana');

    expect(screen.getByText(/Didn't catch that/)).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('shows the caller\'s note alongside the transcript', () => {
    render(
      <VoiceAnswerInput onSubmit={vi.fn()} interpret={() => ({ transcript: 'paris', note: 'Matched: Paris' })} />,
    );
    speak('paris');
    expect(screen.getByText('Matched: Paris')).toBeInTheDocument();
  });

  /**
   * The caller re-ranks the recogniser's hypotheses (#390), so the panel must show and submit the
   * one it chose — not the one that happened to rank first.
   */
  it('shows and submits the hypothesis the caller picked, not the top-ranked one', () => {
    const onSubmit = vi.fn();
    render(
      <VoiceAnswerInput onSubmit={onSubmit} interpret={(transcripts) => ({ transcript: transcripts[1] })} />,
    );

    act(() => FakeSpeechRecognition.last.sayAll(['jibooty', 'djibouti']));

    expect(screen.getByText(/djibouti/)).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));
    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('djibouti');
  });

  /**
   * #426: in a timed run the grace window is our delay, not the user's. Left to run past the
   * deadline it loses the answer entirely and the card is scored unanswered — a correct, in-time
   * spoken answer marked wrong.
   */
  it('submits at once when the grace window would outlive the deadline', () => {
    const onSubmit = vi.fn();
    render(<VoiceAnswerInput onSubmit={onSubmit} remainingMs={VOICE_SUBMIT_DELAY_MS} />);

    speak('paris');
    act(() => vi.advanceTimersByTime(0));

    // Submitted without the window running at all.
    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('paris');
  });

  it('keeps the grace window when there is time for it', () => {
    const onSubmit = vi.fn();
    render(<VoiceAnswerInput onSubmit={onSubmit} remainingMs={60_000} />);

    speak('paris');
    // The same instant that submits above leaves this one still waiting.
    act(() => vi.advanceTimersByTime(0));
    expect(onSubmit).not.toHaveBeenCalled();

    act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));
    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('paris');
  });

  /**
   * The countdown re-renders this component once a second. While the remaining time was an effect
   * dependency, every tick tore down the pending timeout and started a new one — and a tick (1s)
   * lands inside the window (1.5s), so it never finished: the answer sat there unsubmitted for the
   * rest of the run. Only reproducible with a *changing* clock, which is why the fixed-prop tests
   * above all passed while timed runs were broken.
   */
  it('submits even though the clock ticks during the window', () => {
    const onSubmit = vi.fn();
    const { rerender } = render(<VoiceAnswerInput onSubmit={onSubmit} remainingMs={60_000} />);

    speak('paris');
    act(() => vi.advanceTimersByTime(1000));
    rerender(<VoiceAnswerInput onSubmit={onSubmit} remainingMs={59_000} />);
    act(() => vi.advanceTimersByTime(500));

    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('paris');
  });

  // An untimed run has no deadline to lose the answer to, so the window always applies.
  it('keeps the grace window in an untimed run', () => {
    const onSubmit = vi.fn();
    render(<VoiceAnswerInput onSubmit={onSubmit} />);

    speak('paris');
    act(() => vi.advanceTimersByTime(0));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('offers every hypothesis to the caller', () => {
    const interpret = vi.fn(() => ({ transcript: 'chad' }));
    render(<VoiceAnswerInput onSubmit={vi.fn()} interpret={interpret} />);

    act(() => FakeSpeechRecognition.last.sayAll(['shad', 'chad', 'chat']));

    expect(interpret).toHaveBeenCalledExactlyOnceWith(['shad', 'chad', 'chat']);
  });

  it('explains a blocked microphone and offers a way out instead of a dead retry', () => {
    const onDisableVoice = vi.fn();
    render(<VoiceAnswerInput onSubmit={vi.fn()} onDisableVoice={onDisableVoice} />);
    act(() => FakeSpeechRecognition.last.fail('not-allowed'));

    expect(screen.getByText(/Microphone blocked/)).toBeInTheDocument();
    // Restarting a denied recogniser fails silently, so a retry button would be a lie.
    expect(screen.queryByRole('button', { name: /Try again|Speak answer/ })).not.toBeInTheDocument();

    click('Turn off voice answers');
    expect(onDisableVoice).toHaveBeenCalled();
  });

  it('offers an explicit retry after silence rather than restarting on its own', () => {
    render(<VoiceAnswerInput onSubmit={vi.fn()} />);
    act(() => FakeSpeechRecognition.last.fail('no-speech'));

    expect(screen.getByText(/Didn't catch that/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Try again/ })).toBeInTheDocument();
    // Auto-restarting would loop the mic and keep the recording indicator lit.
    expect(FakeSpeechRecognition.instances).toHaveLength(1);
  });

  describe('without a recogniser (Firefox)', () => {
    it('says so and submits nothing', () => {
      uninstall();
      const onSubmit = vi.fn();
      render(<VoiceAnswerInput onSubmit={onSubmit} />);
      expect(screen.getByText(/aren't supported in this browser/)).toBeInTheDocument();
      expect(onSubmit).not.toHaveBeenCalled();
    });
  });
});
