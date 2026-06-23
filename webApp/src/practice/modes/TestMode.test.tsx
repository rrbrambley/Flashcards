import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestMode } from './TestMode';

const card = { question: 'Capital of France?', answer: 'Paris' };

describe('TestMode', () => {
  it('a correct answer reveals feedback, then advances as correct', async () => {
    const onResult = vi.fn();
    render(<TestMode card={card} cards={[card]} onResult={onResult} />);

    await userEvent.type(screen.getByLabelText('Your answer'), 'paris');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.getByText('✓ Correct')).toBeInTheDocument();
    expect(onResult).not.toHaveBeenCalled(); // not until the user proceeds

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onResult).toHaveBeenCalledWith(true);
  });

  it('accepts a near-miss within typo tolerance', async () => {
    const onResult = vi.fn();
    render(<TestMode card={{ question: 'Longest river?', answer: 'Mississippi' }} cards={[]} onResult={onResult} />);

    await userEvent.type(screen.getByLabelText('Your answer'), 'mississipi');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.getByText('✓ Correct')).toBeInTheDocument();
  });

  it('a wrong answer reveals the correct answer and advances as incorrect', async () => {
    const onResult = vi.fn();
    render(<TestMode card={card} cards={[card]} onResult={onResult} />);

    await userEvent.type(screen.getByLabelText('Your answer'), 'Berlin');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.getByText('✗ Incorrect')).toBeInTheDocument();
    expect(screen.getByText('Berlin')).toBeInTheDocument(); // the typed answer stays in place
    expect(screen.getByText('Paris')).toBeInTheDocument(); // the correct answer is revealed

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onResult).toHaveBeenCalledWith(false);
  });

  it('reveals "Also acceptable" alternatives after answering', async () => {
    render(
      <TestMode
        card={{ question: 'Capital of France?', answer: 'Paris', alternativeAnswers: ['Lutetia', 'City of Light'] }}
        cards={[]}
        onResult={vi.fn()}
      />,
    );

    await userEvent.type(screen.getByLabelText('Your answer'), 'paris');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.getByText(/Also acceptable/)).toBeInTheDocument();
    expect(screen.getByText('Lutetia, City of Light')).toBeInTheDocument();
  });

  it('omits "Also acceptable" when the card has no alternatives', async () => {
    render(<TestMode card={card} cards={[card]} onResult={vi.fn()} />);

    await userEvent.type(screen.getByLabelText('Your answer'), 'Berlin');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(screen.queryByText(/Also acceptable/)).not.toBeInTheDocument();
  });
});
