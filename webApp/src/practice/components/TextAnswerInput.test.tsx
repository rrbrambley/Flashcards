import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TextAnswerInput } from './TextAnswerInput';

describe('TextAnswerInput', () => {
  it('submits the typed value on Enter', async () => {
    const onSubmit = vi.fn();
    render(<TextAnswerInput onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText('Your answer'), 'hello{Enter}');

    expect(onSubmit).toHaveBeenCalledWith('hello');
  });

  // The blank-submit guard (FLA-179) is opt-in; without it a blank submit passes straight through.
  it('submits a blank answer immediately when confirmBlankSubmit is off', async () => {
    const onSubmit = vi.fn();
    render(<TextAnswerInput onSubmit={onSubmit} />);

    await userEvent.click(screen.getByRole('button', { name: 'Check' }));

    expect(onSubmit).toHaveBeenCalledWith('');
    expect(screen.queryByText(/skip this card\?/i)).not.toBeInTheDocument();
  });

  it('confirms a blank submit when confirmBlankSubmit is on, and a second Enter does not double-submit', async () => {
    const onSubmit = vi.fn();
    render(<TextAnswerInput onSubmit={onSubmit} confirmBlankSubmit />);

    const input = screen.getByLabelText('Your answer');
    await userEvent.type(input, '{Enter}');
    expect(screen.getByText(/skip this card\?/i)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();

    // A second Enter while the confirm is up must not re-trigger or submit.
    await userEvent.type(input, '{Enter}');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('dismisses the confirm when the user resumes typing', async () => {
    const onSubmit = vi.fn();
    render(<TextAnswerInput onSubmit={onSubmit} confirmBlankSubmit />);

    await userEvent.click(screen.getByRole('button', { name: 'Check' }));
    expect(screen.getByText(/skip this card\?/i)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Your answer'), 'a');
    expect(screen.queryByText(/skip this card\?/i)).not.toBeInTheDocument();
  });

  it('treats a whitespace-only answer as blank', async () => {
    const onSubmit = vi.fn();
    render(<TextAnswerInput onSubmit={onSubmit} confirmBlankSubmit />);

    await userEvent.type(screen.getByLabelText('Your answer'), '   {Enter}');

    expect(screen.getByText(/skip this card\?/i)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
