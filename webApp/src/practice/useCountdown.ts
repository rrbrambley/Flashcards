import { useEffect, useState } from 'react';

/**
 * A ticking countdown to a wall-clock [deadline] (epoch millis), for timed practice sessions (#289).
 * Ticks ~1×/sec and returns the remaining time + whether it's expired. When [deadline] is null (an
 * untimed session) it's inert: infinite remaining, never expired. The deadline is derived from the
 * session's stored `createdAt + timeLimitSeconds` (proctored-test style — the clock keeps running
 * while you're away), so resuming after it passed reports expired at once.
 */
export function useCountdown(deadline: number | null): { remainingMs: number; expired: boolean } {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (deadline == null) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [deadline]);

  if (deadline == null) return { remainingMs: Infinity, expired: false };
  const remainingMs = Math.max(0, deadline - now);
  return { remainingMs, expired: remainingMs <= 0 };
}

/** Formats a remaining duration as `m:ss` (e.g. 90000 → "1:30", 5000 → "0:05"). */
export function formatRemaining(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}
