import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCountdown, formatRemaining } from './useCountdown';

describe('useCountdown', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(0);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('is inert when untimed (null deadline), even if paused', () => {
    const { result } = renderHook(() => useCountdown(null, true));
    expect(result.current.remainingMs).toBe(Infinity);
    expect(result.current.expired).toBe(false);
  });

  it('ticks down toward the deadline', () => {
    const { result } = renderHook(() => useCountdown(10_000));
    expect(result.current.remainingMs).toBe(10_000);
    act(() => vi.advanceTimersByTime(3_000));
    expect(result.current.remainingMs).toBe(7_000);
    expect(result.current.expired).toBe(false);
  });

  it('expires once the deadline passes', () => {
    const { result } = renderHook(() => useCountdown(2_000));
    act(() => vi.advanceTimersByTime(2_000));
    expect(result.current.remainingMs).toBe(0);
    expect(result.current.expired).toBe(true);
  });

  it('freezes while paused and cannot expire mid-pause (#317)', () => {
    const { result, rerender } = renderHook(({ paused }) => useCountdown(3_000, paused), {
      initialProps: { paused: false },
    });
    act(() => vi.advanceTimersByTime(1_000)); // 2s left
    expect(result.current.remainingMs).toBe(2_000);

    rerender({ paused: true });
    // Wall-clock runs well past the original 3s deadline while paused…
    act(() => vi.advanceTimersByTime(10_000));
    expect(result.current.remainingMs).toBe(2_000); // …but the remainder is frozen
    expect(result.current.expired).toBe(false); // …and it doesn't auto-expire

    rerender({ paused: false });
    act(() => vi.advanceTimersByTime(1_000)); // resumes from where it left off
    expect(result.current.remainingMs).toBe(1_000);
  });

  it('resumes and can still expire after the paused span is credited back', () => {
    const { result, rerender } = renderHook(({ paused }) => useCountdown(2_000, paused), {
      initialProps: { paused: true },
    });
    act(() => vi.advanceTimersByTime(5_000)); // paused the whole time — nothing lost
    expect(result.current.remainingMs).toBe(2_000);
    expect(result.current.expired).toBe(false);

    rerender({ paused: false });
    act(() => vi.advanceTimersByTime(2_000));
    expect(result.current.expired).toBe(true);
  });
});

describe('formatRemaining', () => {
  it('formats m:ss, rounding up to the shown second', () => {
    expect(formatRemaining(90_000)).toBe('1:30');
    expect(formatRemaining(5_000)).toBe('0:05');
    expect(formatRemaining(0)).toBe('0:00');
    expect(formatRemaining(-1_000)).toBe('0:00');
  });
});
