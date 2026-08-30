import { useCallback, useEffect, useState } from 'react';
import { TextAnswerInput } from '../components/TextAnswerInput';
import { VoiceAnswerInput } from '../components/VoiceAnswerInput';
import { useVoiceAutoAdvance } from '../voice/useVoiceAutoAdvance';
import { VoiceAdvanceNotice } from '../components/VoiceAdvanceNotice';
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
export function TestMode({
  card,
  onGraded,
  onAdvance,
  onDiscuss,
  canSuggest,
  isGuest,
  onImageReady,
  voiceInput,
  remainingMs,
  onDisableVoice,
}: PracticeModeProps) {
  const [graded, setGraded] = useState<{ input: string; correct: boolean } | null>(null);

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

  /**
   * Picks which hypothesis to grade — n-best rescoring (#390).
   *
   * The recogniser ranks by a general-purpose language model biased toward everyday words, which is
   * why proper nouns lose: a country name is outscored by whatever common phrase it sounds like. We
   * know something it doesn't — this card's answer — so its own list is re-ranked with it.
   *
   * Note what this is *not*: [gradeTextAnswer] is untouched and still decides, at the same
   * threshold, so a spoken and a typed string grade identically. What changes is which string gets
   * graded. That does make voice more forgiving than typing — any hypothesis can win, and only the
   * recogniser proposed them — which is the point, since the mis-hear was never the user's mistake.
   *
   * Falls back to the top hypothesis rather than re-prompting: a wrong answer still has to be
   * recordable, and it should be recorded as what they most likely said.
   */
  const interpretSpoken = useCallback(
    (transcripts: string[]) => {
      const matched = transcripts.find(
        (t) => gradeTextAnswer(t, card.answer, card.alternativeAnswers ?? []).correct,
      );
      return { transcript: matched ?? transcripts[0] };
    },
    [card],
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

  const advancing = useVoiceAutoAdvance({
    active: graded != null && !!voiceInput,
    correct: graded?.correct ?? false,
    onAdvance,
  });

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
          {voiceInput && (
            <VoiceAnswerInput
              onSubmit={submit}
              interpret={interpretSpoken}
              onDisableVoice={onDisableVoice}
              remainingMs={remainingMs}
            />
          )}
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
            {advancing ? (
              <VoiceAdvanceNotice />
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
