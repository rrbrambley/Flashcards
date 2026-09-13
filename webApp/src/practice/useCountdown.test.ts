import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCountdown, formatRemaining, urgencyIntensity, URGENT_MS, CRITICAL_MS } from './useCountdown';

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

describe('urgencyIntensity', () => {
  it('ramps across the critical tier, and is 0 above it', () => {
    expect(urgencyIntensity(6_000)).toBe(0);
    expect(urgencyIntensity(CRITICAL_MS)).toBeCloseTo(0.2);
    expect(urgencyIntensity(4_000)).toBeCloseTo(0.4);
    // The last 3s cross the line where the mobile clients switch to the heavier haptic.
    expect(urgencyIntensity(3_000)).toBeCloseTo(0.6);
    expect(urgencyIntensity(2_000)).toBeCloseTo(0.8);
    expect(urgencyIntensity(1_000)).toBeCloseTo(1);
  });

  it('steps with the displayed second, not the raw milliseconds', () => {
    // Both sides round UP, so the whole span that displays "0:05" shares one step — the ramp can
    // never disagree with the number on screen.
    expect(formatRemaining(5_001)).toBe('0:06');
    expect(urgencyIntensity(5_001)).toBe(0); // still showing 0:06 → not critical yet
    expect(formatRemaining(4_001)).toBe('0:05');
    expect(urgencyIntensity(5_000)).toBeCloseTo(0.2);
    expect(urgencyIntensity(4_001)).toBeCloseTo(0.2); // same displayed second, same step
    expect(urgencyIntensity(4_000)).toBeCloseTo(0.4); // ticks over to 0:04
  });

  it('stays clamped at or below zero', () => {
    // The component gates on `> 0`, but an alpha multiplier above 1 would render, not throw.
    expect(urgencyIntensity(0)).toBe(1);
    expect(urgencyIntensity(-2_000)).toBe(1);
  });

  it('matches the shared Kotlin tiers', () => {
    // Mirrors TimedUrgency in shared/…/PresentationHelpers.kt (#443/#444) — same steps, same order.
    expect(URGENT_MS).toBe(10_000);
    expect(CRITICAL_MS).toBe(5_000);
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
