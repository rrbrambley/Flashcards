import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ClassicMode } from './ClassicMode';

const card = { question: 'Capital of France?', answer: 'Paris' };
// onGraded/onAdvance are required by the mode contract but unused by Classic (it grades-and-advances
// in one step via onResult); pass no-ops.
const baseProps = { card, cards: [card], onGraded: vi.fn(), onAdvance: vi.fn() };

describe('ClassicMode', () => {
  it('marks correct / incorrect via the buttons', async () => {
    const onResult = vi.fn();
    render(<ClassicMode {...baseProps} onResult={onResult} />);

    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));
    expect(onResult).toHaveBeenLastCalledWith(true);

    await userEvent.click(screen.getByRole('button', { name: /Still learning/ }));
    expect(onResult).toHaveBeenLastCalledWith(false);
  });

  it('marks correct / incorrect via the → / ← keys', () => {
    const onResult = vi.fn();
    render(<ClassicMode {...baseProps} onResult={onResult} />);

    fireEvent.keyDown(window, { key: 'ArrowRight' });
    expect(onResult).toHaveBeenLastCalledWith(true);

    fireEvent.keyDown(window, { key: 'ArrowLeft' });
    expect(onResult).toHaveBeenLastCalledWith(false);
  });

  it('flips to reveal the answer on space / enter', () => {
    render(<ClassicMode {...baseProps} onResult={vi.fn()} />);

    // The card affordance advertises the *next* action: "Show answer" until it's flipped.
    expect(screen.getByRole('button', { name: 'Show answer' })).toBeInTheDocument();
    fireEvent.keyDown(window, { key: ' ' });
    expect(screen.getByRole('button', { name: 'Show question' })).toBeInTheDocument();
  });

  it('offers Discuss only after the answer is revealed, and invokes onDiscuss', () => {
    const onDiscuss = vi.fn();
    render(<ClassicMode {...baseProps} onResult={vi.fn()} onDiscuss={onDiscuss} />);

    expect(screen.queryByRole('button', { name: /Discuss this card/ })).not.toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Enter' });

    const discuss = screen.getByRole('button', { name: /Discuss this card/ });
    fireEvent.click(discuss);
    expect(onDiscuss).toHaveBeenCalledTimes(1);
  });

  it('never shows Discuss when onDiscuss is not provided', () => {
    render(<ClassicMode {...baseProps} onResult={vi.fn()} />);

    fireEvent.keyDown(window, { key: ' ' });
    expect(screen.queryByRole('button', { name: /Discuss this card/ })).not.toBeInTheDocument();
  });
});
