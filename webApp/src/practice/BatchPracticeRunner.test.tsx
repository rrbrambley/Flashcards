import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import { BatchPracticeRunner } from './BatchPracticeRunner';
import type { PracticeMode } from './modes/types';
import type { FlashcardDto } from '../api/types';

/**
 * The timed start gate (#372). A batch run puts the whole deck on screen at once, so the countdown
 * has to hold until the opening prompt images have drawn — otherwise a slow connection eats the
 * budget before anything is answerable.
 *
 * The mobile clients get this from `BatchPracticeController`; the web runner is a separate
 * implementation, so the behaviour is duplicated by design (as grading is) and tested on both sides.
 */
describe('BatchPracticeRunner timed start gate', () => {
  const testMode = { key: 'test' } as PracticeMode;

  const card = (uid: string, imageUrl?: string): FlashcardDto =>
    ({ cardUid: uid, question: '', answer: `a-${uid}`, imageUrl }) as FlashcardDto;

  const renderRunner = (cards: FlashcardDto[], deadline = 60_000) =>
    render(
      <BatchPracticeRunner
        sessionId={1}
        cards={cards}
        mode={testMode}
        isGlobal={false}
        isGuest
        deadline={deadline}
        timeLimitSeconds={60}
        onCompleted={() => {}}
        onAgain={() => {}}
        onExit={() => {}}
      />,
    );

  const clock = () => screen.getByLabelText('time remaining').textContent;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(0);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('holds the clock until the opening prompt images settle, then credits the wait back', () => {
    renderRunner([card('a', 'http://img/a.svg'), card('b', 'http://img/b.svg'), card('c')]);
    expect(clock()).toContain('1:00');

    act(() => vi.advanceTimersByTime(5_000));
    expect(clock()).toContain('1:00'); // loading time is not charged

    const images = screen.getAllByRole('presentation', { hidden: true });
    act(() => {
      images.forEach((img) => fireEvent.load(img));
    });
    act(() => vi.advanceTimersByTime(10_000));

    // 10s of answering elapsed since release; the 5s of loading was credited back.
    expect(clock()).toContain('0:50');
  });

  it('never shows more than the configured budget', () => {
    // The deadline derives from the session's stored createdAt — the server's clock — so a client
    // running behind it computes a remainder above the budget, which the ceiling rounds up to "1:01".
    // Held, that wrong value would sit on screen for the whole load (#374 review).
    renderRunner([card('a', 'http://img/a.svg')], 60_800);
    expect(clock()).toContain('1:00');
  });

  it('starts immediately when the opening cards have no images', () => {
    renderRunner([card('a'), card('b')]);
    act(() => vi.advanceTimersByTime(4_000));
    expect(clock()).toContain('0:56');
  });

  it('counts a failed image as settled, so a broken URL cannot freeze the run', () => {
    renderRunner([card('a', 'http://img/broken.svg')]);
    act(() => vi.advanceTimersByTime(3_000));
    expect(clock()).toContain('1:00');

    act(() => {
      fireEvent.error(screen.getAllByRole('presentation', { hidden: true })[0]);
    });
    act(() => vi.advanceTimersByTime(4_000));
    expect(clock()).toContain('0:56');
  });

  it('releases the hold on its own if an image never reports at all', () => {
    renderRunner([card('a', 'http://img/hangs.svg')]);

    act(() => vi.advanceTimersByTime(10_000)); // the bounded wait lapses
    expect(clock()).toContain('1:00');

    act(() => vi.advanceTimersByTime(3_000));
    expect(clock()).toContain('0:57');
  });
});
