import { useEffect, useState } from 'react';
import { PracticeCard } from '../PracticeCard';
import { DiscussButton } from '../components/DiscussButton';
import type { PracticeModeProps } from './types';

/**
 * The original tap-to-flip + swipe practice, now a pluggable mode. Owns the flip state and the
 * classic keyboard shortcuts (← / → mark, space/enter flip); the runner remounts it per card, so
 * the flip resets on its own. Self-reported correctness via swipe or the buttons.
 */
export function ClassicMode({ card, onResult, onDiscuss, onImageReady }: PracticeModeProps) {
  const [isFlipped, setIsFlipped] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight') onResult(true);
      else if (e.key === 'ArrowLeft') onResult(false);
      else if (e.key === ' ' || e.key === 'Enter') {
        e.preventDefault();
        setIsFlipped((f) => !f);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onResult]);

  return (
    <>
      <PracticeCard
        card={card}
        isFlipped={isFlipped}
        onFlip={() => setIsFlipped((f) => !f)}
        onSwipeLeft={() => onResult(false)}
        onSwipeRight={() => onResult(true)}
        onImageReady={onImageReady}
      />

      <p className="muted practice-hint">Tap to flip · swipe or ← / → (or the buttons) to mark</p>

      <div className="practice-actions">
        <button className="secondary mark-incorrect" onClick={() => onResult(false)}>
          ✗ Still learning
        </button>
        <button className="mark-correct" onClick={() => onResult(true)}>
          ✓ Got it
        </button>
      </div>

      {/* Discussion is offered once the answer has been seen (FLA-116). */}
      {isFlipped && onDiscuss && <DiscussButton onClick={onDiscuss} />}
    </>
  );
}
