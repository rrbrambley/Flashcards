import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MultipleChoiceMode } from './MultipleChoiceMode';
import type { FlashcardDto } from '../../api/types';
import { FakeSpeechRecognition, installFakeSpeechRecognition } from '../../test/fakeSpeechRecognition';
import { VOICE_SUBMIT_DELAY_MS } from '../components/VoiceAnswerInput';

// 4 cards → for card[0] the 3 distractors (Tokyo/Rome/Madrid) are all included, so every option's
// text is predictable even though their order is random.
const deck: FlashcardDto[] = [
  { question: 'Capital of France?', answer: 'Paris' },
  { question: 'Capital of Japan?', answer: 'Tokyo' },
  { question: 'Capital of Italy?', answer: 'Rome' },
  { question: 'Capital of Spain?', answer: 'Madrid' },
];

// All three are required by the mode contract; tests pass no-ops for the ones they don't assert.
const noopProps = { onResult: vi.fn(), onGraded: vi.fn(), onAdvance: vi.fn() };

describe('MultipleChoiceMode', () => {
  it('grades a correct pick on selection, then advances on Next', async () => {
    const onGraded = vi.fn();
    const onAdvance = vi.fn();
    render(<MultipleChoiceMode card={deck[0]} cards={deck} {...noopProps} onGraded={onGraded} onAdvance={onAdvance} />);

    await userEvent.click(screen.getByRole('button', { name: /Paris/ }));
    // Scored on the pick (so the streak badge shows on the revealed answer), before advancing.
    expect(onGraded).toHaveBeenCalledWith(true, 'Paris');
    expect(onAdvance).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onAdvance).toHaveBeenCalled();
  });

  it('a wrong pick highlights the correct option and grades it as incorrect', async () => {
    const onGraded = vi.fn();
    const onAdvance = vi.fn();
    render(<MultipleChoiceMode card={deck[0]} cards={deck} {...noopProps} onGraded={onGraded} onAdvance={onAdvance} />);

    await userEvent.click(screen.getByRole('button', { name: /Tokyo/ }));
    expect(screen.getByRole('button', { name: /Paris/ })).toHaveClass('correct');
    expect(screen.getByRole('button', { name: /Tokyo/ })).toHaveClass('incorrect');
    expect(onGraded).toHaveBeenCalledWith(false, 'Tokyo');

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onAdvance).toHaveBeenCalled();
  });

  it('locks the answer after the first pick (grades exactly once)', async () => {
    const onGraded = vi.fn();
    render(<MultipleChoiceMode card={deck[0]} cards={deck} {...noopProps} onGraded={onGraded} />);

    await userEvent.click(screen.getByRole('button', { name: /Tokyo/ }));
    expect(screen.getByRole('button', { name: /Paris/ })).toBeDisabled();
    // A second click on another option must not re-grade.
    await userEvent.click(screen.getByRole('button', { name: /Paris/ }));
    expect(onGraded).toHaveBeenCalledTimes(1);
  });

  describe('answering by voice (#388)', () => {
    let uninstall: () => void;
    beforeEach(() => {
      uninstall = installFakeSpeechRecognition();
      vi.useFakeTimers();
    });
    afterEach(() => {
      vi.useRealTimers();
      uninstall();
    });

    const speak = (transcript: string) => {
      act(() => FakeSpeechRecognition.last.say(transcript));
      act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));
    };

    /**
     * `submittedText` is the *option text*, never the raw transcript — so the review screen, the
     * batch recap and the answer-stats grouping look identical whether the user clicked or spoke.
     */
    it('reports the option text, not what was actually said', () => {
      const onGraded = vi.fn();
      render(<MultipleChoiceMode card={deck[0]} cards={deck} {...noopProps} onGraded={onGraded} voiceInput />);

      speak('  tokyo  ');

      expect(onGraded).toHaveBeenCalledExactlyOnceWith(false, 'Tokyo');
    });

    it('grades a correct spoken answer', () => {
      const onGraded = vi.fn();
      render(<MultipleChoiceMode card={deck[0]} cards={deck} {...noopProps} onGraded={onGraded} voiceInput />);

      speak('paris');

      expect(onGraded).toHaveBeenCalledExactlyOnceWith(true, 'Paris');
    });

    // A transcript matching nothing must re-prompt, never land on a definite option the user
    // didn't say — that answer would be recorded with no way to un-grade it.
    it('grades nothing when the transcript matches no option', () => {
      const onGraded = vi.fn();
      render(<MultipleChoiceMode card={deck[0]} cards={deck} {...noopProps} onGraded={onGraded} voiceInput />);

      speak('banana');

      expect(onGraded).not.toHaveBeenCalled();
      expect(screen.getByText(/Didn't catch that/)).toBeInTheDocument();
    });

    it('shows which option it matched before committing to it', () => {
      render(<MultipleChoiceMode card={deck[0]} cards={deck} {...noopProps} voiceInput />);

      act(() => FakeSpeechRecognition.last.say('paris'));

      // Inside the grace window: named, not yet graded, still cancellable.
      expect(screen.getByText('Matched: Paris')).toBeInTheDocument();
    });

    it('advances without a Next button once the answer is graded', () => {
      const onAdvance = vi.fn();
      render(<MultipleChoiceMode card={deck[0]} cards={deck} {...noopProps} onAdvance={onAdvance} voiceInput />);

      speak('paris');

      expect(screen.getByText('Next question…')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument();

      act(() => vi.advanceTimersByTime(1500));
      expect(onAdvance).toHaveBeenCalledOnce();
    });

    it('leaves the options clickable while the mic is up', () => {
      const onGraded = vi.fn();
      render(<MultipleChoiceMode card={deck[0]} cards={deck} {...noopProps} onGraded={onGraded} voiceInput />);

      act(() => {
        fireEvent.click(screen.getByRole('button', { name: /Paris/ }));
      });

      expect(onGraded).toHaveBeenCalledExactlyOnceWith(true, 'Paris');
    });

    // Guards every pre-existing test in this file.
    it('renders no voice control without the prop', () => {
      render(<MultipleChoiceMode card={deck[0]} cards={deck} {...noopProps} />);

      expect(screen.queryByText(/Speech is processed/)).not.toBeInTheDocument();
      expect(FakeSpeechRecognition.instances).toHaveLength(0);
    });
  });
});
