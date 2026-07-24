import { useEffect, useState } from 'react';
import { TextAnswerInput } from '../components/TextAnswerInput';
import { DiscussButton } from '../components/DiscussButton';
import { PromptImage } from '../components/PromptImage';
import { SuggestAnswerButton } from '../SuggestAnswerButton';
import { gradeTextAnswer } from '../grading/textAnswer';
import type { PracticeModeProps } from './types';

/**
 * Text-entry practice: show the question, the user types an answer, and we grade it
 * (case-insensitive, typo-tolerant). After submitting, reveal the correct answer + feedback; the
 * user proceeds (Next / Enter), which reports the outcome. The runner remounts this per card, so the
 * two-phase state resets on its own.
 */
export function TestMode({ card, onGraded, onAdvance, onDiscuss, canSuggest, isGuest, onImageReady }: PracticeModeProps) {
  const [graded, setGraded] = useState<{ input: string; correct: boolean } | null>(null);

  // Once revealed, Enter advances (mirrors the submit-with-Enter flow).
  useEffect(() => {
    if (!graded) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        onAdvance();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [graded, onAdvance]);

  const hasImage = card.imageUrl != null && card.imageUrl !== '';

  return (
    <div className="test-mode">
      <div className="test-prompt">
        {card.question && <p className="practice-term">{card.question}</p>}
        {hasImage && (
          <PromptImage
            src={card.imageUrl ?? ''}
            alt={card.question || 'card image'}
            className="practice-image"
            onReady={onImageReady}
          />
        )}
      </div>

      {!graded ? (
        <TextAnswerInput
          confirmBlankSubmit
          onSubmit={(input) => {
            const correct = gradeTextAnswer(input, card.answer, card.alternativeAnswers ?? []).correct;
            setGraded({ input, correct });
            // Score it now (the verdict is on screen) so the streak badge shows on this answer.
            onGraded(correct, input);
          }}
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
          {/* Teach the full set of valid responses (FLA-131); shown on either verdict. */}
          {(card.alternativeAnswers?.length ?? 0) > 0 && (
            <p className="test-alternatives">
              Also acceptable: <strong>{card.alternativeAnswers!.join(', ')}</strong>
            </p>
          )}
          {/* "This should be correct" — propose the typed answer as an alternative (FLA-130); never
              for a blank answer (a skip can't be a valid alternative, FLA-190). */}
          {!graded.correct && graded.input.trim() !== '' && canSuggest && card.cardUid && (
            <SuggestAnswerButton cardUid={card.cardUid} answer={graded.input} isGuest={!!isGuest} />
          )}
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
