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
export function MultipleChoiceMode({ card, cards, onGraded, onAdvance, onDiscuss }: PracticeModeProps) {
  const [choices] = useState(() => buildChoices(card, cards));
  const correctIndex = choices.indexOf(card.answer.trim());
  const [selected, setSelected] = useState<number | null>(null);

  // First pick locks the answer and grades it now (the streak badge shows on the revealed answer).
  const pick = useCallback(
    (i: number) => {
      if (selected !== null) return;
      setSelected(i);
      onGraded(i === correctIndex, choices[i]);
    },
    [selected, correctIndex, choices, onGraded],
  );

  // Once a choice is locked in, Enter advances.
  useEffect(() => {
    if (selected === null) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        onAdvance();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [selected, onAdvance]);

  const hasImage = card.imageUrl != null && card.imageUrl !== '';

  return (
    <div className="mc-mode">
      <div className="test-prompt">
        {card.question && <p className="practice-term">{card.question}</p>}
        {hasImage && <img src={card.imageUrl ?? ''} alt={card.question || 'card image'} className="practice-image" />}
      </div>

      <MultipleChoice
        options={choices}
        onSelect={pick}
        selectedIndex={selected}
        correctIndex={selected === null ? null : correctIndex}
        disabled={selected !== null}
      />

      {selected !== null && (
        <>
          <div className="practice-actions">
            <button className="mark-correct" onClick={() => onAdvance()}>
              Next
            </button>
          </div>
          {onDiscuss && <DiscussButton onClick={onDiscuss} />}
        </>
      )}
    </div>
  );
}
