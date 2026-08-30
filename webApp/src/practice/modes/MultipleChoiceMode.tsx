import { useCallback, useEffect, useState } from 'react';
import { MultipleChoice } from '../components/MultipleChoice';
import { DiscussButton } from '../components/DiscussButton';
import { PromptImage } from '../components/PromptImage';
import { buildChoices } from '../grading/multipleChoice';
import { matchSpokenChoice } from '../grading/voiceChoice';
import { VoiceAnswerInput } from '../components/VoiceAnswerInput';
import { useVoiceAutoAdvance } from '../voice/useVoiceAutoAdvance';
import { VoiceAdvanceNotice } from '../components/VoiceAdvanceNotice';
import type { PracticeModeProps } from './types';

/**
 * Multiple-choice practice: the user picks the answer from up to four options (distractors drawn from
 * other cards in the deck). On pick we reveal right/wrong, then report the outcome on Next (or Enter).
 * The runner remounts this per card, so the choices + selection reset on their own — and choices are
 * built once per mount so they don't reshuffle on re-render.
 */
export function MultipleChoiceMode({
  card,
  cards,
  onGraded,
  onAdvance,
  onDiscuss,
  onImageReady,
  voiceInput,
  remainingMs,
  onDisableVoice,
}: PracticeModeProps) {
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

  // Say the answer rather than the option letter (#388): naming "Paris" is how you'd answer a person,
  // where "option B" forces you to read all four first. Both callbacks re-run the same pure match —
  // it's deterministic on the same transcript and choices, so there's no state to keep in sync.
  /**
   * The first hypothesis that names an option wins — n-best rescoring (#390).
   *
   * Walked in the recogniser's own confidence order, so its ranking is only overridden when the
   * better-ranked hypotheses name nothing at all. [matchSpokenChoice] is called unchanged, once per
   * hypothesis: its floor and margin rules still decide every match, and the shared Kotlin port and
   * the golden fixture that pins them stay exactly as they are.
   *
   * Resolving to the *option text* (not the raw transcript) is what keeps a spoken answer
   * indistinguishable from a clicked one in the review screen and answer stats.
   */
  const interpretSpoken = useCallback(
    (transcripts: string[]) => {
      for (const transcript of transcripts) {
        const index = matchSpokenChoice(transcript, choices);
        if (index != null) return { transcript: choices[index], note: `Matched: ${choices[index]}` };
      }
      // null → the panel re-prompts. Never fall back to a best guess: an auto-submitted wrong answer
      // is recorded against the card and can't be un-graded.
      return null;
    },
    [choices],
  );

  const submitSpoken = useCallback(
    (transcript: string) => {
      const index = matchSpokenChoice(transcript, choices);
      if (index != null) pick(index);
    },
    [choices, pick],
  );

  const advancing = useVoiceAutoAdvance({
    active: selected !== null && !!voiceInput,
    correct: selected === correctIndex,
    onAdvance,
  });

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
        {hasImage && (
          <PromptImage
            src={card.imageUrl ?? ''}
            alt={card.question || 'card image'}
            className="practice-image"
            onReady={onImageReady}
          />
        )}
      </div>

      {/* Above the options, and never instead of them — a misrecognition, a blocked microphone or a
          browser with no recogniser all leave the card answerable by clicking. */}
      {voiceInput && selected === null && (
        <VoiceAnswerInput
          onSubmit={submitSpoken}
          interpret={interpretSpoken}
          onDisableVoice={onDisableVoice}
          remainingMs={remainingMs}
        />
      )}

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
