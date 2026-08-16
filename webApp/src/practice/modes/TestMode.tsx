import { useCallback, useEffect, useState } from 'react';
import { TextAnswerInput } from '../components/TextAnswerInput';
import { VoiceAnswerInput } from '../components/VoiceAnswerInput';
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
/**
 * How long a verdict stays up before a voice run moves on by itself.
 *
 * A wrong answer dwells longer: the correct answer only just appeared, and reading it is the entire
 * value of getting it wrong. A right answer has nothing left to read.
 */
const ADVANCE_DELAY_MS = 1500;
const ADVANCE_DELAY_INCORRECT_MS = 4000;

export function TestMode({
  card,
  onGraded,
  onAdvance,
  onDiscuss,
  canSuggest,
  isGuest,
  onImageReady,
  voiceInput,
  onDisableVoice,
}: PracticeModeProps) {
  const [graded, setGraded] = useState<{ input: string; correct: boolean } | null>(null);
  // Set when the user takes over during the auto-advance dwell, which pins the card until they act.
  const [advanceCancelled, setAdvanceCancelled] = useState(false);

  // One grading call site, so a spoken answer and a typed one are scored by exactly the same code.
  const submit = useCallback(
    (input: string) => {
      const correct = gradeTextAnswer(input, card.answer, card.alternativeAnswers ?? []).correct;
      setGraded({ input, correct });
      // Score it now (the verdict is on screen) so the streak badge shows on this answer.
      onGraded(correct, input);
    },
    [card, onGraded],
  );

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

  // Auto-advance past the verdict, but only in a voice run (#387). Reaching for "Next" is exactly
  // the reach the feature exists to avoid — the point is answering from across the room without
  // touching anything. A typed run leaves it alone: that user's hand is already on the keyboard, and
  // taking the pause away from them would be a change nobody asked for.
  useEffect(() => {
    if (!graded || !voiceInput || advanceCancelled) return;
    const id = setTimeout(onAdvance, graded.correct ? ADVANCE_DELAY_MS : ADVANCE_DELAY_INCORRECT_MS);
    return () => clearTimeout(id);
  }, [graded, voiceInput, advanceCancelled, onAdvance]);

  // Any deliberate interaction means they want to stay on this card — reading the right answer,
  // or reaching for "This should be correct". Cancelling restores the normal Next button.
  useEffect(() => {
    if (!graded || !voiceInput || advanceCancelled) return;
    const cancel = () => setAdvanceCancelled(true);
    window.addEventListener('pointerdown', cancel);
    return () => window.removeEventListener('pointerdown', cancel);
  }, [graded, voiceInput, advanceCancelled]);

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
        <>
          {voiceInput && <VoiceAnswerInput onSubmit={submit} onDisableVoice={onDisableVoice} />}
          <TextAnswerInput confirmBlankSubmit onSubmit={submit} />
        </>
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
            {voiceInput && !advanceCancelled ? (
              // Not a button: pressing it is the thing we're removing. Announced politely so a
              // screen-reader user knows the card is about to change rather than being surprised.
              <p className="test-advancing" aria-live="polite">
                Next question…
              </p>
            ) : (
              <button className="mark-correct" onClick={() => onAdvance()}>
                Next
              </button>
            )}
          </div>
          {onDiscuss && <DiscussButton onClick={onDiscuss} />}
        </>
      )}
    </div>
  );
}
