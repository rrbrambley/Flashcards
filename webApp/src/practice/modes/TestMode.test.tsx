import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestMode } from './TestMode';

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
});
