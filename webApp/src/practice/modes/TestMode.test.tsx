import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestMode } from './TestMode';

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
});
