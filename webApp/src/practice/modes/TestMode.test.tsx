import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestMode } from './TestMode';
import { FakeSpeechRecognition, installFakeSpeechRecognition } from '../../test/fakeSpeechRecognition';
import { VOICE_SUBMIT_DELAY_MS } from '../components/VoiceAnswerInput';

// Stub the suggest button (it needs auth context); we only assert whether it renders.
vi.mock('../SuggestAnswerButton', () => ({
  SuggestAnswerButton: () => <div>This should be correct</div>,
}));

const card = { question: 'Capital of France?', answer: 'Paris' };

// All three are required by the mode contract; tests pass no-ops for the ones they don't assert.
const noopProps = { onResult: vi.fn(), onGraded: vi.fn(), onAdvance: vi.fn() };

describe('TestMode', () => {
  it('grades on the verdict, then advances on Next', async () => {
    const onGraded = vi.fn();
    const onAdvance = vi.fn();
    render(<TestMode card={card} cards={[card]} onResult={vi.fn()} onGraded={onGraded} onAdvance={onAdvance} />);

    await userEvent.type(screen.getByLabelText('Your answer'), 'paris');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.getByText('✓ Correct')).toBeInTheDocument();
    // Scored as soon as the verdict shows (so the streak badge appears here), before advancing.
    expect(onGraded).toHaveBeenCalledWith(true, 'paris');
    expect(onAdvance).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onAdvance).toHaveBeenCalled();
  });

  it('accepts a near-miss within typo tolerance', async () => {
    render(<TestMode card={{ question: 'Longest river?', answer: 'Mississippi' }} cards={[]} {...noopProps} />);

    await userEvent.type(screen.getByLabelText('Your answer'), 'mississipi');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.getByText('✓ Correct')).toBeInTheDocument();
  });

  it('a wrong answer reveals the correct answer and grades it as incorrect', async () => {
    const onGraded = vi.fn();
    const onAdvance = vi.fn();
    render(<TestMode card={card} cards={[card]} onResult={vi.fn()} onGraded={onGraded} onAdvance={onAdvance} />);

    await userEvent.type(screen.getByLabelText('Your answer'), 'Berlin');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.getByText('✗ Incorrect')).toBeInTheDocument();
    expect(screen.getByText('Berlin')).toBeInTheDocument(); // the typed answer stays in place
    expect(screen.getByText('Paris')).toBeInTheDocument(); // the correct answer is revealed
    expect(onGraded).toHaveBeenCalledWith(false, 'Berlin');

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onAdvance).toHaveBeenCalled();
  });

  it('reveals "Also acceptable" alternatives after answering', async () => {
    render(
      <TestMode
        card={{ question: 'Capital of France?', answer: 'Paris', alternativeAnswers: ['Lutetia', 'City of Light'] }}
        cards={[]}
        {...noopProps}
      />,
    );

    await userEvent.type(screen.getByLabelText('Your answer'), 'paris');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.getByText(/Also acceptable/)).toBeInTheDocument();
    expect(screen.getByText('Lutetia, City of Light')).toBeInTheDocument();
  });

  it('omits "Also acceptable" when the card has no alternatives', async () => {
    render(<TestMode card={card} cards={[card]} {...noopProps} />);

    await userEvent.type(screen.getByLabelText('Your answer'), 'Berlin');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.queryByText(/Also acceptable/)).not.toBeInTheDocument();
  });

  // FLA-179: an accidental empty Enter/Check must not silently grade the card wrong.
  it('confirms before grading a blank Enter instead of scoring it', async () => {
    const onGraded = vi.fn();
    render(<TestMode card={card} cards={[card]} onResult={vi.fn()} onGraded={onGraded} onAdvance={vi.fn()} />);

    await userEvent.type(screen.getByLabelText('Your answer'), '{Enter}');

    expect(screen.getByText(/skip this one\?/i)).toBeInTheDocument();
    expect(onGraded).not.toHaveBeenCalled();
    expect(screen.queryByText('✗ Incorrect')).not.toBeInTheDocument();
  });

  it('confirms before grading a blank Check click', async () => {
    const onGraded = vi.fn();
    render(<TestMode card={card} cards={[card]} onResult={vi.fn()} onGraded={onGraded} onAdvance={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.getByText(/skip this one\?/i)).toBeInTheDocument();
    expect(onGraded).not.toHaveBeenCalled();
  });

  it('Confirm submits the blank answer and grades it incorrect', async () => {
    const onGraded = vi.fn();
    render(<TestMode card={card} cards={[card]} onResult={vi.fn()} onGraded={onGraded} onAdvance={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Check' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(onGraded).toHaveBeenCalledWith(false, '');
    expect(screen.getByText('✗ Incorrect')).toBeInTheDocument();
    expect(screen.getByText('(blank)')).toBeInTheDocument();
  });

  it('resuming typing dismisses the blank confirm', async () => {
    render(<TestMode card={card} cards={[card]} {...noopProps} />);

    await userEvent.click(screen.getByRole('button', { name: 'Check' }));
    expect(screen.getByText(/skip this one\?/i)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Your answer'), 'p');
    expect(screen.queryByText(/skip this one\?/i)).not.toBeInTheDocument();
  });

  it('a non-blank answer submits immediately with no confirm', async () => {
    const onGraded = vi.fn();
    render(<TestMode card={card} cards={[card]} onResult={vi.fn()} onGraded={onGraded} onAdvance={vi.fn()} />);

    await userEvent.type(screen.getByLabelText('Your answer'), 'paris{Enter}');

    expect(screen.queryByText(/skip this one\?/i)).not.toBeInTheDocument();
    expect(onGraded).toHaveBeenCalledWith(true, 'paris');
  });

  // FLA-190: a blank answer can't be proposed as an acceptable alternative.
  it('shows the suggest action on a wrong non-blank answer (global card)', async () => {
    const globalCard = { ...card, cardUid: 'card-1' };
    render(<TestMode card={globalCard} cards={[globalCard]} canSuggest {...noopProps} />);

    await userEvent.type(screen.getByLabelText('Your answer'), 'Berlin');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.getByText('This should be correct')).toBeInTheDocument();
  });

  it('hides the suggest action after confirming a blank answer', async () => {
    const globalCard = { ...card, cardUid: 'card-1' };
    render(<TestMode card={globalCard} cards={[globalCard]} canSuggest {...noopProps} />);

    await userEvent.click(screen.getByRole('button', { name: 'Check' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(screen.getByText('✗ Incorrect')).toBeInTheDocument();
    expect(screen.queryByText('This should be correct')).not.toBeInTheDocument();
  });

  describe('answering by voice (#387)', () => {
    let uninstall: () => void;
    beforeEach(() => {
      uninstall = installFakeSpeechRecognition();
      vi.useFakeTimers();
    });
    afterEach(() => {
      vi.useRealTimers();
      uninstall();
    });

    /**
     * The point of the whole design: a transcript is just text, so it rides the *existing* grading
     * path. If this passes, spoken and typed answers cannot grade differently.
     */
    it('grades a spoken answer through the same path as a typed one', () => {
      const onGraded = vi.fn();
      render(<TestMode card={card} cards={[card]} {...noopProps} onGraded={onGraded} voiceInput />);

      act(() => FakeSpeechRecognition.last.say('paris'));
      act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));

      expect(onGraded).toHaveBeenCalledWith(true, 'paris');
      expect(screen.getByText('✓ Correct')).toBeInTheDocument();
    });

    /**
     * n-best rescoring (#390). The recogniser's own ranking favours everyday words, so a proper noun
     * loses to whatever common phrase it sounds like — the failure reported from a real session on
     * the geography decks. The right answer is often present, just not ranked first.
     */
    it('grades a lower-ranked hypothesis that matches, over a top-ranked one that does not', () => {
      const onGraded = vi.fn();
      render(<TestMode card={card} cards={[card]} {...noopProps} onGraded={onGraded} voiceInput />);

      act(() => FakeSpeechRecognition.last.sayAll(['pear iss', 'paris']));
      act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));

      expect(onGraded).toHaveBeenCalledWith(true, 'paris');
      expect(screen.getByText('✓ Correct')).toBeInTheDocument();
    });

    /**
     * Rescoring must not become "keep looking until something is right". A genuinely wrong answer
     * stays wrong, and is recorded as what they most likely said — the top-ranked hypothesis — so
     * the review screen and answer stats show the real utterance.
     */
    it('records the top hypothesis when no hypothesis is correct', () => {
      const onGraded = vi.fn();
      render(<TestMode card={card} cards={[card]} {...noopProps} onGraded={onGraded} voiceInput />);

      act(() => FakeSpeechRecognition.last.sayAll(['lyon', 'leon', 'lion']));
      act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));

      expect(onGraded).toHaveBeenCalledWith(false, 'lyon');
    });

    // The recogniser's ranking is only overridden when it has to be: a correct top hypothesis wins.
    it('keeps the top hypothesis when it is already correct', () => {
      const onGraded = vi.fn();
      render(<TestMode card={card} cards={[card]} {...noopProps} onGraded={onGraded} voiceInput />);

      act(() => FakeSpeechRecognition.last.sayAll(['paris', 'parris']));
      act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));

      expect(onGraded).toHaveBeenCalledWith(true, 'paris');
    });

    /**
     * The whole point: you can answer from across the room. Reaching for "Next" is exactly the reach
     * the feature exists to remove, so the verdict clears itself.
     */
    it('moves to the next card by itself, with no Next button to press', () => {
      const onAdvance = vi.fn();
      render(<TestMode card={card} cards={[card]} {...noopProps} onAdvance={onAdvance} voiceInput />);

      act(() => FakeSpeechRecognition.last.say('paris'));
      act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));

      expect(screen.getByText('Next question…')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument();
      expect(onAdvance).not.toHaveBeenCalled();

      act(() => vi.advanceTimersByTime(1500));
      expect(onAdvance).toHaveBeenCalledOnce();
    });

    // Reading the right answer is the entire value of getting it wrong, so it can't flash past.
    it('dwells longer on a wrong answer than on a right one', () => {
      const onAdvance = vi.fn();
      render(<TestMode card={card} cards={[card]} {...noopProps} onAdvance={onAdvance} voiceInput />);

      act(() => FakeSpeechRecognition.last.say('lyon'));
      act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));
      expect(screen.getByText('Paris')).toBeInTheDocument();

      // The delay a correct answer would have used isn't enough here.
      act(() => vi.advanceTimersByTime(1500));
      expect(onAdvance).not.toHaveBeenCalled();

      act(() => vi.advanceTimersByTime(2500));
      expect(onAdvance).toHaveBeenCalledOnce();
    });

    // Auto-advance must never yank a card away from someone who's reaching for it — to read the
    // answer, or to hit "This should be correct".
    it('stops advancing and restores Next once the user interacts', () => {
      const onAdvance = vi.fn();
      render(<TestMode card={card} cards={[card]} {...noopProps} onAdvance={onAdvance} voiceInput />);

      act(() => FakeSpeechRecognition.last.say('paris'));
      act(() => vi.advanceTimersByTime(VOICE_SUBMIT_DELAY_MS));
      act(() => {
        fireEvent.pointerDown(document.body);
      });

      act(() => vi.advanceTimersByTime(10_000));
      expect(onAdvance).not.toHaveBeenCalled();
      expect(screen.getByRole('button', { name: 'Next' })).toBeInTheDocument();
    });

    // A typed run's user already has a hand on the keyboard; taking the pause away is a change
    // nobody asked for.
    it('leaves a typed run to advance at its own pace', async () => {
      vi.useRealTimers();
      const onAdvance = vi.fn();
      render(<TestMode card={card} cards={[card]} {...noopProps} onAdvance={onAdvance} />);

      await userEvent.type(screen.getByLabelText('Your answer'), 'paris');
      await userEvent.click(screen.getByRole('button', { name: 'Check' }));

      expect(screen.getByRole('button', { name: 'Next' })).toBeInTheDocument();
      expect(screen.queryByText('Next question…')).not.toBeInTheDocument();
    });

    it('keeps the text field, so a misrecognition or a blocked mic is still answerable', () => {
      render(<TestMode card={card} cards={[card]} {...noopProps} voiceInput />);

      expect(screen.getByLabelText('Your answer')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Check' })).toBeInTheDocument();
    });

    // Guards every pre-existing test in this file: without the prop, nothing about the card changes.
    it('renders no voice control at all without the prop', () => {
      render(<TestMode card={card} cards={[card]} {...noopProps} />);

      expect(screen.queryByText(/Speech is processed/)).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /🎤/ })).not.toBeInTheDocument();
      expect(FakeSpeechRecognition.instances).toHaveLength(0);
    });
  });
});
