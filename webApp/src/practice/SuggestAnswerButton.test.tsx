import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SuggestAnswerButton } from './SuggestAnswerButton';
import { api } from '../api/client';

vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: { suggestAnswer: vi.fn(), register: vi.fn(), login: vi.fn() },
}));

const applyAuth = vi.fn();
vi.mock('../auth/auth-context', () => ({ useAuth: () => ({ applyAuth }) }));
vi.mock('../auth/token', () => ({ setTokens: vi.fn() }));

describe('SuggestAnswerButton', () => {
  beforeEach(() => vi.clearAllMocks());

  it('submits the answer for a signed-in user and confirms', async () => {
    vi.mocked(api.suggestAnswer).mockResolvedValue(undefined);
    render(<SuggestAnswerButton cardUid="c1" answer=" Paris, France " isGuest={false} />);

    await userEvent.click(screen.getByRole('button', { name: 'This should be correct' }));

    await waitFor(() => expect(api.suggestAnswer).toHaveBeenCalledWith('c1', 'Paris, France'));
    expect(await screen.findByText(/sent for review/)).toBeInTheDocument();
  });

  it('treats a 409 (already suggested) as success', async () => {
    const { ApiError } = await import('../api/client');
    vi.mocked(api.suggestAnswer).mockRejectedValue(new ApiError(409, 'duplicate'));
    render(<SuggestAnswerButton cardUid="c1" answer="Paris" isGuest={false} />);

    await userEvent.click(screen.getByRole('button', { name: 'This should be correct' }));

    expect(await screen.findByText(/sent for review/)).toBeInTheDocument();
  });

  it('routes a guest through the sign-in conversion (no submit until authed)', async () => {
    render(<SuggestAnswerButton cardUid="c1" answer="Paris" isGuest />);

    await userEvent.click(screen.getByRole('button', { name: 'This should be correct' }));

    expect(await screen.findByText('Suggest an answer')).toBeInTheDocument();
    expect(api.suggestAnswer).not.toHaveBeenCalled();
  });

  it('guest conversion: registers, submits the suggestion, then applies auth', async () => {
    vi.mocked(api.register).mockResolvedValue({ accessToken: 'a', refreshToken: 'r', userId: 1, permissions: [] });
    vi.mocked(api.suggestAnswer).mockResolvedValue(undefined);
    render(<SuggestAnswerButton cardUid="c1" answer="Paris" isGuest />);

    await userEvent.click(screen.getByRole('button', { name: 'This should be correct' }));
    await userEvent.type(screen.getByLabelText('Email'), 'new@user.com');
    await userEvent.type(screen.getByLabelText('Password'), 'password1');
    await userEvent.click(screen.getByRole('button', { name: 'Create account & suggest' }));

    await waitFor(() => expect(api.register).toHaveBeenCalledWith('new@user.com', 'password1'));
    expect(api.suggestAnswer).toHaveBeenCalledWith('c1', 'Paris');
    expect(applyAuth).toHaveBeenCalled();
  });
});
