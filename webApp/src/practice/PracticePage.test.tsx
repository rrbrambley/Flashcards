import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { PracticePage } from './PracticePage';
import { api } from '../api/client';
import type { FlashcardDto, PracticeSessionDto } from '../api/types';

vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  api: {
    createSession: vi.fn(),
    getDeck: vi.fn(),
    getCatalogDeck: vi.fn(),
    updateProgress: vi.fn(),
    completeSession: vi.fn(),
    register: vi.fn(),
  },
}));

// Default: signed in. Guest tests set mockToken = null. applyAuth/token come from the auth context.
let mockToken: string | null = 'test-token';
const applyAuth = vi.fn();
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({ token: mockToken, applyAuth }),
}));
vi.mock('../auth/token', () => ({ setTokens: vi.fn() }));

const session = (over: Partial<PracticeSessionDto> = {}): PracticeSessionDto => ({
  id: 1,
  deckId: 5,
  deckTitle: 'Spanish',
  currentCardIndex: 0,
  numCorrect: 0,
  numIncorrect: 0,
  isCompleted: false,
  mode: 'flashcards',
  createdAtMillis: 0,
  updatedAtMillis: 0,
  ...over,
});

function setup(cards: FlashcardDto[], sessionOver: Partial<PracticeSessionDto> = {}) {
  vi.mocked(api.createSession).mockResolvedValue(session(sessionOver));
  vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: true, flashcards: cards });
  vi.mocked(api.updateProgress).mockResolvedValue(session());
  vi.mocked(api.completeSession).mockResolvedValue(session({ isCompleted: true }));
  render(
    <MemoryRouter initialEntries={['/decks/5/practice?mode=flashcards']}>
      <Routes>
        <Route path="/decks/:id/practice" element={<PracticePage />} />
        <Route path="/" element={<div>library</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

const threeCards: FlashcardDto[] = [
  { question: 'Q1', answer: 'A1' },
  { question: 'Q2', answer: 'A2' },
  { question: 'Q3', answer: 'A3' },
];

describe('PracticePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockToken = 'test-token';
  });

  it('starts a session in the default (classic) mode and shows the (resumed) current card', async () => {
    setup(threeCards, { currentCardIndex: 1 });
    expect(await screen.findByText('Q2')).toBeInTheDocument();
    expect(api.createSession).toHaveBeenCalledWith(5, 'flashcards');
  });

  it('clicking the card flips it', async () => {
    setup(threeCards);
    const card = await screen.findByRole('button', { name: 'Show answer' });
    await userEvent.click(card);
    expect(screen.getByRole('button', { name: 'Show question' })).toBeInTheDocument();
  });

  it('marking correct advances and persists progress', async () => {
    setup(threeCards);
    await screen.findByText('Q1');

    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));

    expect(await screen.findByText('Q2')).toBeInTheDocument();
    expect(api.updateProgress).toHaveBeenCalledWith(1, { currentCardIndex: 1, numCorrect: 1, numIncorrect: 0 });
  });

  it('the right arrow key marks correct', async () => {
    setup(threeCards);
    await screen.findByText('Q1');

    fireEvent.keyDown(document.body, { key: 'ArrowRight' });

    expect(await screen.findByText('Q2')).toBeInTheDocument();
  });

  it('marking the last card completes the session', async () => {
    setup([{ question: 'Only', answer: 'Card' }]);
    await screen.findByText('Only');

    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));

    expect(await screen.findByText('Practice complete')).toBeInTheDocument();
    // Completion records the device timezone for streaks (FLA-105).
    expect(api.completeSession).toHaveBeenCalledWith(1, expect.any(String));
  });

  it('shows the mode chooser when no mode is selected', async () => {
    render(
      <MemoryRouter initialEntries={['/decks/5/practice']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('Choose a mode')).toBeInTheDocument();
    expect(screen.getByText('Test')).toBeInTheDocument();
  });

  it('runs the test mode end-to-end when ?mode=test', async () => {
    vi.mocked(api.createSession).mockResolvedValue(session({ mode: 'test' }));
    vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: true, flashcards: threeCards });
    vi.mocked(api.updateProgress).mockResolvedValue(session());
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=test']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('Q1');
    expect(api.createSession).toHaveBeenCalledWith(5, 'test');

    await userEvent.type(screen.getByLabelText('Your answer'), 'A1');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByText('Q2')).toBeInTheDocument();
    expect(api.updateProgress).toHaveBeenCalledWith(1, { currentCardIndex: 1, numCorrect: 1, numIncorrect: 0 });
  });

  it('runs multiple-choice mode end-to-end when ?mode=multiple_choice', async () => {
    vi.mocked(api.createSession).mockResolvedValue(session({ mode: 'multiple_choice' }));
    vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: true, flashcards: threeCards });
    vi.mocked(api.updateProgress).mockResolvedValue(session());
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=multiple_choice']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('Q1');
    expect(api.createSession).toHaveBeenCalledWith(5, 'multiple_choice');

    await userEvent.click(screen.getByRole('button', { name: /A1/ })); // the correct option
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByText('Q2')).toBeInTheDocument();
    expect(api.updateProgress).toHaveBeenCalledWith(1, { currentCardIndex: 1, numCorrect: 1, numIncorrect: 0 });
  });

  it('shows an error when the deck has no cards', async () => {
    setup([]);
    expect(await screen.findByText(/no cards to practice/i)).toBeInTheDocument();
  });

  it('shows an error when starting the session fails', async () => {
    vi.mocked(api.createSession).mockRejectedValue(new Error('offline'));
    vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', flashcards: threeCards });
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=flashcards']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('offline')).toBeInTheDocument();
  });

  describe('guest mode (no account)', () => {
    function guestSetup(cards: FlashcardDto[]) {
      mockToken = null;
      vi.mocked(api.getCatalogDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: false, flashcards: cards });
      render(
        <MemoryRouter initialEntries={['/decks/5/practice?mode=flashcards']}>
          <Routes>
            <Route path="/decks/:id/practice" element={<PracticePage />} />
            <Route path="/" element={<div>catalog</div>} />
          </Routes>
        </MemoryRouter>,
      );
    }

    it('practices session-less: loads via the public catalog and never creates/persists a session', async () => {
      guestSetup(threeCards);
      await screen.findByText('Q1');

      expect(api.getCatalogDeck).toHaveBeenCalledWith(5);
      expect(api.createSession).not.toHaveBeenCalled();

      await userEvent.click(screen.getByRole('button', { name: /Got it/ }));

      expect(await screen.findByText('Q2')).toBeInTheDocument();
      expect(api.updateProgress).not.toHaveBeenCalled();
    });

    it('prompts to save when leaving an in-progress session', async () => {
      guestSetup(threeCards);
      await screen.findByText('Q1');
      // Advance one card so there is progress to lose.
      await userEvent.click(screen.getByRole('button', { name: /Got it/ }));
      await screen.findByText('Q2');

      await userEvent.click(screen.getByRole('button', { name: /Catalog/ }));

      expect(await screen.findByText('Save your progress?')).toBeInTheDocument();
    });

    it('"Leave without saving" abandons progress and returns to the catalog', async () => {
      guestSetup(threeCards);
      await screen.findByText('Q1');
      await userEvent.click(screen.getByRole('button', { name: /Got it/ }));
      await screen.findByText('Q2');

      await userEvent.click(screen.getByRole('button', { name: /Catalog/ }));
      await userEvent.click(await screen.findByRole('button', { name: 'Leave without saving' }));

      expect(await screen.findByText('catalog')).toBeInTheDocument();
    });

    it('save-on-signup: registers, creates the session, and pushes the current progress', async () => {
      guestSetup(threeCards);
      vi.mocked(api.register).mockResolvedValue({ accessToken: 'a', refreshToken: 'r', userId: 1, permissions: [] });
      vi.mocked(api.createSession).mockResolvedValue(session({ id: 99 }));
      vi.mocked(api.updateProgress).mockResolvedValue(session({ id: 99 }));
      await screen.findByText('Q1');
      await userEvent.click(screen.getByRole('button', { name: /Got it/ })); // now on card index 1
      await screen.findByText('Q2');

      await userEvent.click(screen.getByRole('button', { name: /Catalog/ }));
      await userEvent.type(screen.getByLabelText('Email'), 'new@user.com');
      await userEvent.type(screen.getByLabelText('Password'), 'password1');
      await userEvent.click(screen.getByRole('button', { name: 'Create account & save' }));

      await vi.waitFor(() => expect(api.register).toHaveBeenCalledWith('new@user.com', 'password1'));
      expect(api.createSession).toHaveBeenCalledWith(5, 'flashcards');
      expect(api.updateProgress).toHaveBeenCalledWith(99, { currentCardIndex: 1, numCorrect: 1, numIncorrect: 0 });
      expect(applyAuth).toHaveBeenCalled();
    });

    it('does not prompt when leaving before answering anything', async () => {
      guestSetup(threeCards);
      await screen.findByText('Q1');

      await userEvent.click(screen.getByRole('button', { name: /Catalog/ }));

      expect(await screen.findByText('catalog')).toBeInTheDocument();
      expect(screen.queryByText('Save your progress?')).not.toBeInTheDocument();
    });
  });
});
