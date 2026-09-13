import type { CSSProperties } from 'react';
import { CRITICAL_MS, URGENT_MS, formatRemaining, urgencyIntensity } from '../useCountdown';

interface PracticeTimerProps {
  /** Time left in the run. Callers pass the value straight from `useCountdown`. */
  remainingMs: number;
}

/**
 * The timed-run countdown (#289) and its urgency escalation (#443). Shared by the card-by-card
 * runner and the grade-at-the-end batch runner, which rendered identical copies of this markup
 * before.
 *
 * The chip goes red at 10s. In the final 5 it also amplifies, flashes once per second, and lights a
 * vignette at the edges of the viewport — peripheral, so it never covers the card being answered.
 *
 * **The flash is a one-shot animation restarted by `key={secondsLeft}`, not a CSS loop.** Remounting
 * the (empty, decorative) node replays its animation from 0 in the same commit that changes the
 * digit, so the pulse is in phase with the countdown rather than free-running at an arbitrary offset
 * the way the old `animation: timer-pulse 1s infinite` did. It also means the pulse stops on its own
 * while the clock is paused for a prompt-image load (#317): `remainingMs` is frozen, so `secondsLeft`
 * is frozen, so nothing remounts. The static styling stays — time really is low; only motion stops.
 *
 * The `key` belongs on the decorative nodes only. Remounting the chip itself would risk a screen
 * reader re-announcing it every second, which is exactly what the tier-keyed live region below
 * avoids.
 */
export function PracticeTimer({ remainingMs }: PracticeTimerProps) {
  const secondsLeft = Math.ceil(remainingMs / 1000);
  const urgent = remainingMs <= URGENT_MS;
  // At 0 the run is already transitioning to the time-up screen, so a final flash would land on a
  // view that's unmounting. The critical *styling* stops short of it (the shared `> 0` gate, #443)…
  const critical = remainingMs <= CRITICAL_MS && remainingMs > 0;
  // …but the *announcement* tier doesn't, so it only ever deepens. Were it gated on `> 0` too, the
  // tier would fall back from critical to urgent at 0 and re-announce "10 seconds remaining".
  const announcedTier = remainingMs <= CRITICAL_MS ? 'critical' : urgent ? 'urgent' : 'none';
  const ramp = urgencyIntensity(remainingMs);
  const rampStyle = { '--urgency': String(ramp) } as CSSProperties;

  // Announced once per tier crossing, never per tick: the chip's own text changes every second, so a
  // live region on it would interrupt itself ten times over and drown out the card.
  const announcement =
    announcedTier === 'critical'
      ? `${CRITICAL_MS / 1000} seconds remaining`
      : announcedTier === 'urgent'
        ? `${URGENT_MS / 1000} seconds remaining`
        : '';

  return (
    <>
      {critical && (
        <div key={secondsLeft} className="practice-urgency-glow" aria-hidden="true" style={rampStyle} />
      )}
      <div className="practice-timer-row">
        <span
          className={`practice-timer${urgent ? ' urgent' : ''}${critical ? ' critical' : ''}`}
          // Spells out the time: a bare "time remaining" label would *replace* the text content in
          // the accessible name, so a screen reader would never read the number. Matches iOS.
          aria-label={`${formatRemaining(remainingMs)} remaining`}
        >
          ⏱ {formatRemaining(remainingMs)}
          {critical && (
            <span key={secondsLeft} className="practice-timer-flash" aria-hidden="true" style={rampStyle} />
          )}
        </span>
        <span className="sr-only" role="status">
          {announcement}
        </span>
      </div>
    </>
  );
}
