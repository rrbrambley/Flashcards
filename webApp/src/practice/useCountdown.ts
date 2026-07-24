import { useEffect, useRef, useState } from 'react';

/**
 * A ticking countdown to a wall-clock [deadline] (epoch millis), for timed practice sessions (#289).
 * Ticks ~1×/sec and returns the remaining time + whether it's expired. When [deadline] is null (an
 * untimed session) it's inert: infinite remaining, never expired. The deadline is derived from the
 * session's stored `createdAt + timeLimitSeconds` (proctored-test style — the clock keeps running
 * while you're away), so resuming after it passed reports expired at once.
 *
 * When [paused] is true the countdown freezes — used to stop the clock while a card's prompt image is
 * still loading (#317, the web counterpart of #311/#312). Each pause span is accumulated and folded
 * back on resume, effectively shifting the deadline forward so no time is lost (mirrors the shared
 * `PracticeSessionController.pauseTimer()`/`resumeTimer()`). It's wall-clock-safe because timed runs
 * are single-sitting (#306): the accumulator lives only for this run and is never persisted.
 */
export function useCountdown(deadline: number | null, paused = false): { remainingMs: number; expired: boolean } {
  const [now, setNow] = useState(() => Date.now());
  // Time spent paused so far (ms). Added to the deadline, so a pause effectively extends it — the
  // remainder is `deadline + pausedMs - now`. Advanced in lock-step with `now` while paused (both grow
  // by the same span each tick), which holds the remainder frozen; left alone while running.
  const [pausedMs, setPausedMs] = useState(0);
  // Written outside render only (allowed): the live pause flag for the tick, and the last tick instant.
  const pausedRef = useRef(paused);
  const lastTickRef = useRef(now);

  useEffect(() => {
    pausedRef.current = paused;
  }, [paused]);

  useEffect(() => {
    if (deadline == null) return;
    lastTickRef.current = Date.now();
    const id = setInterval(() => {
      const t = Date.now();
      const span = t - lastTickRef.current;
      lastTickRef.current = t;
      setNow(t);
      // While paused, credit the elapsed span back so `deadline + pausedMs - now` stays put (#317).
      if (pausedRef.current) setPausedMs((p) => p + span);
    }, 1000);
    return () => clearInterval(id);
  }, [deadline]);

  if (deadline == null) return { remainingMs: Infinity, expired: false };
  const remainingMs = Math.max(0, deadline + pausedMs - now);
  return { remainingMs, expired: remainingMs <= 0 };
}

/** Formats a remaining duration as `m:ss` (e.g. 90000 → "1:30", 5000 → "0:05"). */
export function formatRemaining(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}
