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
});
