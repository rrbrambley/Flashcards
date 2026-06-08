import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MultipleChoiceMode } from './MultipleChoiceMode';
import type { FlashcardDto } from '../../api/types';

// 4 cards → for card[0] the 3 distractors (Tokyo/Rome/Madrid) are all included, so every option's
// text is predictable even though their order is random.
const deck: FlashcardDto[] = [
  { question: 'Capital of France?', answer: 'Paris' },
  { question: 'Capital of Japan?', answer: 'Tokyo' },
  { question: 'Capital of Italy?', answer: 'Rome' },
  { question: 'Capital of Spain?', answer: 'Madrid' },
];

describe('MultipleChoiceMode', () => {
  it('a correct pick advances as correct', async () => {
    const onResult = vi.fn();
    render(<MultipleChoiceMode card={deck[0]} cards={deck} onResult={onResult} />);

    await userEvent.click(screen.getByRole('button', { name: /Paris/ }));
    expect(onResult).not.toHaveBeenCalled(); // not until proceeding

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onResult).toHaveBeenCalledWith(true);
  });

  it('a wrong pick highlights the correct option and advances as incorrect', async () => {
    const onResult = vi.fn();
    render(<MultipleChoiceMode card={deck[0]} cards={deck} onResult={onResult} />);

    await userEvent.click(screen.getByRole('button', { name: /Tokyo/ }));
    expect(screen.getByRole('button', { name: /Paris/ })).toHaveClass('correct');
    expect(screen.getByRole('button', { name: /Tokyo/ })).toHaveClass('incorrect');

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(onResult).toHaveBeenCalledWith(false);
  });

  it('locks the answer after the first pick', async () => {
    const onResult = vi.fn();
    render(<MultipleChoiceMode card={deck[0]} cards={deck} onResult={onResult} />);

    await userEvent.click(screen.getByRole('button', { name: /Tokyo/ }));
    expect(screen.getByRole('button', { name: /Paris/ })).toBeDisabled();
  });
});
