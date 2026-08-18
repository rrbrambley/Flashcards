import { useEffect, useState } from 'react';

/**
 * How long a verdict stays up before a voice run moves on by itself.
 *
 * A wrong answer dwells longer: the correct answer only just appeared, and reading it is the entire
 * value of getting it wrong. A right answer has nothing left to read.
 */
export const ADVANCE_DELAY_MS = 1500;
export const ADVANCE_DELAY_INCORRECT_MS = 4000;

/**
 * Clears the verdict by itself so a voice run needs no clicks at all (#387).
 *
 * Reaching for "Next" is exactly the reach voice exists to remove — the point is answering from
 * across the room. Only ever enabled for a voice run: a typed or clicked run leaves the pause alone,
 * because that user's hand is already on the keyboard and taking it away helps nobody.
 *
 * Returns whether the card is currently counting down, which the caller renders in place of its
 * Next button. Any pointer interaction cancels for good and restores the button — auto-advance must
 * never yank a card away from someone reaching to read the answer or to dispute the grade.
 */
export function useVoiceAutoAdvance({
  active,
  correct,
  onAdvance,
}: {
  /** True once the verdict is showing *and* this is a voice run. */
  active: boolean;
  correct: boolean;
  onAdvance: () => void;
}): boolean {
  const [cancelled, setCancelled] = useState(false);
  const advancing = active && !cancelled;

  useEffect(() => {
    if (!advancing) return;
    const id = setTimeout(onAdvance, correct ? ADVANCE_DELAY_MS : ADVANCE_DELAY_INCORRECT_MS);
    return () => clearTimeout(id);
  }, [advancing, correct, onAdvance]);

  useEffect(() => {
    if (!advancing) return;
    const cancel = () => setCancelled(true);
    window.addEventListener('pointerdown', cancel);
    return () => window.removeEventListener('pointerdown', cancel);
  }, [advancing]);

  return advancing;
}
