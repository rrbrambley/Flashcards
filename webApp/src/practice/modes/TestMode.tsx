import { useEffect, useState } from 'react';
import { TextAnswerInput } from '../components/TextAnswerInput';
import { gradeTextAnswer } from '../grading/textAnswer';
import type { PracticeModeProps } from './types';

/**
 * Text-entry practice: show the question, the user types an answer, and we grade it
 * (case-insensitive, typo-tolerant). After submitting, reveal the correct answer + feedback; the
 * user proceeds (Next / Enter), which reports the outcome. The runner remounts this per card, so the
 * two-phase state resets on its own.
 */
export function TestMode({ card, onResult }: PracticeModeProps) {
  const [graded, setGraded] = useState<{ input: string; correct: boolean } | null>(null);

  // Once revealed, Enter advances (mirrors the submit-with-Enter flow).
  useEffect(() => {
    if (!graded) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        onResult(graded.correct);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [graded, onResult]);

  const hasImage = card.imageUrl != null && card.imageUrl !== '';

  return (
    <div className="test-mode">
      <div className="test-prompt">
        {card.question && <p className="practice-term">{card.question}</p>}
        {hasImage && <img src={card.imageUrl ?? ''} alt={card.question || 'card image'} className="practice-image" />}
      </div>

      {!graded ? (
        <TextAnswerInput
          onSubmit={(input) =>
            setGraded({ input, correct: gradeTextAnswer(input, card.answer, card.alternativeAnswers ?? []).correct })
          }
        />
      ) : (
        <>
          {/* Keep the typed answer where the input was, with the verdict beside it. */}
          <div className="test-submitted">
            <span className="test-submitted-answer">{graded.input.trim() || '(blank)'}</span>
            <span className={`test-verdict ${graded.correct ? 'correct' : 'incorrect'}`}>
              {graded.correct ? '✓ Correct' : '✗ Incorrect'}
            </span>
          </div>
          {!graded.correct && (
            <p className="test-answer">
              Answer: <strong>{card.answer}</strong>
            </p>
          )}
          <div className="practice-actions">
            <button className="mark-correct" onClick={() => onResult(graded.correct)}>
              Next
            </button>
          </div>
        </>
      )}
    </div>
  );
}
