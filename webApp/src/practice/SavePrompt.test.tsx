import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SavePrompt } from './SavePrompt';
import { api, ApiError } from '../api/client';
import { setTokens } from '../auth/token';
import type { PracticeSessionDto } from '../api/types';

vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: { register: vi.fn(), createSession: vi.fn(), updateProgress: vi.fn() },
}));

const applyAuth = vi.fn();
vi.mock('../auth/auth-context', () => ({ useAuth: () => ({ applyAuth }) }));
vi.mock('../auth/token', () => ({ setTokens: vi.fn() }));

const navigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => navigate,
}));

const auth = { accessToken: 'a', refreshToken: 'r', userId: 1, permissions: [] };
const sessionDto: PracticeSessionDto = {
  id: 42,
  deckId: 5,
  deckTitle: 'Spanish',
  currentCardIndex: 3,
  numCorrect: 2,
  numIncorrect: 1,
  isCompleted: false,
  mode: 'flashcards',
  createdAtMillis: 0,
  updatedAtMillis: 0,
  shuffle: true,
  shuffleSeed: 0,
  questionCount: null,
  gradeAtEnd: false,
};

const props = {
  deckId: 5,
  mode: 'flashcards',
  shuffle: true,
  questionCount: null,
  progress: { currentCardIndex: 3, numCorrect: 2, numIncorrect: 1 },
  onCancel: vi.fn(),
  onLeave: vi.fn(),
};

beforeEach(() => vi.clearAllMocks());

describe('SavePrompt', () => {
  it('requires an email and a password before submitting', async () => {
    render(<SavePrompt {...props} />);

    await userEvent.click(screen.getByRole('button', { name: /Create account & save/ }));

    expect(screen.getByText('Enter your email and password.')).toBeInTheDocument();
    expect(api.register).not.toHaveBeenCalled();
  });

  it('registers, persists the session before flipping auth, then navigates home', async () => {
    vi.mocked(api.register).mockResolvedValue(auth);
    vi.mocked(api.createSession).mockResolvedValue(sessionDto);
    vi.mocked(api.updateProgress).mockResolvedValue(sessionDto);

    render(<SavePrompt {...props} />);
    await userEvent.type(screen.getByLabelText('Email'), '  new@example.com  ');
    await userEvent.type(screen.getByLabelText('Password'), 'password1');
    await userEvent.click(screen.getByRole('button', { name: /Create account & save/ }));

    expect(api.register).toHaveBeenCalledWith('new@example.com', 'password1'); // email trimmed
    expect(setTokens).toHaveBeenCalledWith('a', 'r'); // bearer set before the authed calls
    expect(api.createSession).toHaveBeenCalledWith(5, 'flashcards', true, null); // deckId / mode / shuffle / count
    expect(api.updateProgress).toHaveBeenCalledWith(42, props.progress);
    expect(applyAuth).toHaveBeenCalledWith(auth); // in-memory auth flips only after persistence
    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('shows a friendly message when the email is already taken (409) and does not flip auth', async () => {
    vi.mocked(api.register).mockRejectedValue(new ApiError(409, 'conflict'));

    render(<SavePrompt {...props} />);
    await userEvent.type(screen.getByLabelText('Email'), 'taken@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'password1');
    await userEvent.click(screen.getByRole('button', { name: /Create account & save/ }));

    expect(await screen.findByText(/already exists/)).toBeInTheDocument();
    expect(applyAuth).not.toHaveBeenCalled();
  });

  it('leave and cancel invoke their callbacks without saving', async () => {
    render(<SavePrompt {...props} />);

    await userEvent.click(screen.getByRole('button', { name: 'Leave without saving' }));
    expect(props.onLeave).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(props.onCancel).toHaveBeenCalledTimes(1);
    expect(api.register).not.toHaveBeenCalled();
  });
});
