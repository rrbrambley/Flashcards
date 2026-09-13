import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PracticeTimer } from './PracticeTimer';

/**
 * The countdown's urgency tiers (#443). Driven directly rather than through a runner so the final
 * instant — `remainingMs` exactly 0 — is observable: in a real run that value auto-submits and
 * unmounts the whole timer, which is precisely why the tier has to stop short of it.
 */
describe('PracticeTimer', () => {
  const chip = () => screen.getByLabelText(/ remaining$/);
  const glow = (c: HTMLElement) => c.querySelector('.practice-urgency-glow');
  const ramp = (el: Element | null) => el?.getAttribute('style')?.match(/--urgency:\s*([\d.]+)/)?.[1];

  it('reads the time out in its accessible name', () => {
    render(<PracticeTimer remainingMs={65_000} />);
    // A bare "time remaining" label would replace the text content in the accessible name, so a
    // screen reader would announce the phrase and never the number.
    expect(chip()).toHaveAccessibleName('1:05 remaining');
  });

  it('is plain above 10s, red at 10s, escalated at 5s', () => {
    const { rerender, container } = render(<PracticeTimer remainingMs={11_000} />);
    expect(chip().className).toBe('practice-timer');
    expect(glow(container)).toBeNull();

    rerender(<PracticeTimer remainingMs={10_000} />);
    expect(chip().className).toContain('urgent');
    expect(chip().className).not.toContain('critical');
    expect(glow(container)).toBeNull();

    rerender(<PracticeTimer remainingMs={5_000} />);
    expect(chip().className).toContain('critical');
    expect(glow(container)).not.toBeNull();
  });

  it('escalates the ramp each second through the critical tier', () => {
    const { rerender, container } = render(<PracticeTimer remainingMs={5_000} />);
    expect(ramp(glow(container))).toBe('0.2');

    rerender(<PracticeTimer remainingMs={3_000} />);
    expect(ramp(glow(container))).toBe('0.6');

    rerender(<PracticeTimer remainingMs={1_000} />);
    expect(ramp(glow(container))).toBe('1');
  });

  it('restarts the flash by remounting it, keyed on the displayed second', () => {
    const { rerender, container } = render(<PracticeTimer remainingMs={5_000} />);
    const first = glow(container);

    // Same displayed second (both show 0:05) — nothing remounts, so a mid-second re-render can't
    // retrigger the animation.
    rerender(<PracticeTimer remainingMs={4_400} />);
    expect(glow(container)).toBe(first);

    // New second — React swaps the node, which is what replays the CSS animation from 0.
    rerender(<PracticeTimer remainingMs={4_000} />);
    expect(glow(container)).not.toBe(first);
  });

  it('drops the escalation at 0, where the run is already leaving for the time-up screen', () => {
    const { rerender, container } = render(<PracticeTimer remainingMs={1_000} />);
    expect(glow(container)).not.toBeNull();

    rerender(<PracticeTimer remainingMs={0} />);
    expect(chip().className).not.toContain('critical');
    expect(glow(container)).toBeNull();
    expect(chip()).toHaveAccessibleName('0:00 remaining');
  });

  it('announces each tier once, not every tick', () => {
    // The regression that matters: the chip's text changes every second, so a live region tied to
    // the reading would interrupt itself ten times over and drown out the card.
    const { rerender } = render(<PracticeTimer remainingMs={11_000} />);
    const status = screen.getByRole('status');
    expect(status).toHaveTextContent('');

    rerender(<PracticeTimer remainingMs={10_000} />);
    expect(status).toHaveTextContent('10 seconds remaining');

    for (const ms of [9_000, 8_000, 7_000, 6_000]) {
      rerender(<PracticeTimer remainingMs={ms} />);
      expect(status).toHaveTextContent('10 seconds remaining'); // unchanged → not re-announced
    }

    rerender(<PracticeTimer remainingMs={5_000} />);
    expect(status).toHaveTextContent('5 seconds remaining');

    for (const ms of [4_000, 3_000, 2_000, 1_000]) {
      rerender(<PracticeTimer remainingMs={ms} />);
      expect(status).toHaveTextContent('5 seconds remaining');
    }

    // Still critical at 0 even though the styling has dropped: were the announcement gated on `> 0`
    // too, the tier would fall back to urgent here and re-announce "10 seconds remaining".
    rerender(<PracticeTimer remainingMs={0} />);
    expect(status).toHaveTextContent('5 seconds remaining');
  });

  it('keeps the glow and flash out of the accessibility tree', () => {
    const { container } = render(<PracticeTimer remainingMs={3_000} />);
    expect(glow(container)).toHaveAttribute('aria-hidden', 'true');
    expect(container.querySelector('.practice-timer-flash')).toHaveAttribute('aria-hidden', 'true');
  });
});
