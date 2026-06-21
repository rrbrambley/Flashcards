import { useCallback, useEffect, useState } from 'react';
import { MultipleChoice } from '../components/MultipleChoice';
import { DiscussButton } from '../components/DiscussButton';
import { buildChoices } from '../grading/multipleChoice';
import type { PracticeModeProps } from './types';

/**
 * Multiple-choice practice: the user picks the answer from up to four options (distractors drawn from
 * other cards in the deck). On pick we reveal right/wrong, then report the outcome on Next (or Enter).
 * The runner remounts this per card, so the choices + selection reset on their own — and choices are
 * built once per mount so they don't reshuffle on re-render.
 */
export function MultipleChoiceMode({ card, cards, onResult, onDiscuss }: PracticeModeProps) {
  const [choices] = useState(() => buildChoices(card, cards));
  const correctIndex = choices.indexOf(card.answer.trim());
  const [selected, setSelected] = useState<number | null>(null);

  const proceed = useCallback(() => {
    if (selected !== null) onResult(selected === correctIndex);
  }, [selected, correctIndex, onResult]);

  // Once a choice is locked in, Enter advances.
  useEffect(() => {
    if (selected === null) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        proceed();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [selected, proceed]);

  const hasImage = card.imageUrl != null && card.imageUrl !== '';

  return (
    <div className="mc-mode">
      <div className="test-prompt">
        {card.question && <p className="practice-term">{card.question}</p>}
        {hasImage && <img src={card.imageUrl ?? ''} alt={card.question || 'card image'} className="practice-image" />}
      </div>

      <MultipleChoice
        options={choices}
        onSelect={(i) => setSelected((prev) => (prev === null ? i : prev))}
        selectedIndex={selected}
        correctIndex={selected === null ? null : correctIndex}
        disabled={selected !== null}
      />

      {selected !== null && (
        <>
          <div className="practice-actions">
            <button className="mark-correct" onClick={proceed}>
              Next
            </button>
          </div>
          {onDiscuss && <DiscussButton onClick={onDiscuss} />}
        </>
      )}
    </div>
  );
}
