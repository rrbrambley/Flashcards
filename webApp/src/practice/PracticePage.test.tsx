import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { PracticePage } from './PracticePage';
import { api } from '../api/client';
import { orderCards } from './shuffle';
import type { FlashcardDto, PracticeSessionDto } from '../api/types';

vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  api: {
    createSession: vi.fn(),
    getDeck: vi.fn(),
    getCatalogDeck: vi.fn(),
    updateProgress: vi.fn(),
    completeSession: vi.fn(),
    recordAnswers: vi.fn(),
    getAnswers: vi.fn(),
    getStreaks: vi.fn(),
    register: vi.fn(),
    getDiscussionThread: vi.fn(),
    getDiscussionMessages: vi.fn(),
  },
}));

// Default: signed in. Guest tests set mockToken = null. applyAuth/token come from the auth context.
let mockToken: string | null = 'test-token';
const applyAuth = vi.fn();
let mockCan = false;
// The `discussions` kill switch (FLA-180); default on so the discuss surface shows.
let mockDiscussionsFlag = true;
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({ token: mockToken, applyAuth, can: () => mockCan, isEnabled: () => mockDiscussionsFlag }),
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
  shuffle: false,
  shuffleSeed: 0,
  ...over,
});

function setup(cards: FlashcardDto[], sessionOver: Partial<PracticeSessionDto> = {}) {
  vi.mocked(api.createSession).mockResolvedValue(session(sessionOver));
  vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: true, flashcards: cards });
  vi.mocked(api.updateProgress).mockResolvedValue(session());
  vi.mocked(api.completeSession).mockResolvedValue(session({ isCompleted: true }));
  vi.mocked(api.recordAnswers).mockResolvedValue(session());
  vi.mocked(api.getAnswers).mockResolvedValue([]);
  vi.mocked(api.getStreaks).mockResolvedValue({ overall: { current: 3, longest: 5 }, decks: [] });
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
    mockCan = false;
    mockDiscussionsFlag = true;
  });

  it('starts a session in the default (classic) mode and shows the (resumed) current card', async () => {
    setup(threeCards, { currentCardIndex: 1 });
    expect(await screen.findByText('Q2')).toBeInTheDocument();
    // No `shuffle=` in the route → the toggle defaults On (FLA-200), so the new session is created shuffled.
    expect(api.createSession).toHaveBeenCalledWith(5, 'flashcards', true);
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

  it('records each answer and shows the in-session streak after 2 in a row (FLA-99)', async () => {
    setup([
      { question: 'Q1', answer: 'A1', cardUid: 'c1' },
      { question: 'Q2', answer: 'A2', cardUid: 'c2' },
      { question: 'Q3', answer: 'A3', cardUid: 'c3' },
    ]);
    await screen.findByText('Q1');

    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));
    // The answer is logged with its cardUid, outcome, and 0-based play order.
    expect(api.recordAnswers).toHaveBeenCalledWith(1, [
      expect.objectContaining({ cardUid: 'c1', correct: true, sequence: 0 }),
    ]);
    // One correct isn't a streak yet.
    expect(screen.queryByText(/in a row/)).not.toBeInTheDocument();

    await screen.findByText('Q2');
    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));

    // Two consecutive correct surfaces the streak badge.
    expect(await screen.findByText(/2 in a row/)).toBeInTheDocument();
  });

  it('a wrong answer resets the in-session streak', async () => {
    setup([
      { question: 'Q1', answer: 'A1', cardUid: 'c1' },
      { question: 'Q2', answer: 'A2', cardUid: 'c2' },
      { question: 'Q3', answer: 'A3', cardUid: 'c3' },
      { question: 'Q4', answer: 'A4', cardUid: 'c4' },
    ]);
    await screen.findByText('Q1');
    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));
    await screen.findByText('Q2');
    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));
    expect(await screen.findByText(/2 in a row/)).toBeInTheDocument();

    await screen.findByText('Q3');
    await userEvent.click(screen.getByRole('button', { name: /Still learning/ }));

    // Still practicing (Q4), but the streak badge is gone (reset to 0).
    expect(await screen.findByText('Q4')).toBeInTheDocument();
    expect(screen.queryByText(/in a row/)).not.toBeInTheDocument();
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
    // The just-earned overall streak is read after completing and shown (FLA-106).
    expect(await screen.findByText(/3 day streak/)).toBeInTheDocument();
    expect(api.getStreaks).toHaveBeenCalledWith(expect.any(String));
  });

  it('shows a per-card review of the run on completion (FLA-149)', async () => {
    setup([
      { question: 'Q1', answer: 'A1', cardUid: 'c1' },
      { question: 'Q2', answer: 'A2', cardUid: 'c2' },
    ]);
    // The session's answer log (read after completion), joined to the cards by cardUid.
    vi.mocked(api.getAnswers).mockResolvedValue([
      { answerUid: 'a1', cardUid: 'c1', correct: true, sequence: 0, answeredAtMillis: 0, submittedText: null },
      { answerUid: 'a2', cardUid: 'c2', correct: false, sequence: 1, answeredAtMillis: 0, submittedText: 'manzana' },
    ]);

    await screen.findByText('Q1');
    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));
    await screen.findByText('Q2');
    await userEvent.click(screen.getByRole('button', { name: /Still learning/ }));

    expect(await screen.findByText('Practice complete')).toBeInTheDocument();
    // The review reads the session's answer log after completion.
    expect(await screen.findByText('Review')).toBeInTheDocument();
    expect(api.getAnswers).toHaveBeenCalledWith(1);
    // Each card's correct answer + the submitted text (Test/MC) appear; outcomes are labelled.
    expect(screen.getByText('A1')).toBeInTheDocument();
    expect(screen.getByText('A2')).toBeInTheDocument();
    expect(screen.getByText('You answered: manzana')).toBeInTheDocument();
    expect(screen.getByLabelText('correct')).toBeInTheDocument();
    expect(screen.getByLabelText('incorrect')).toBeInTheDocument();
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
    expect(api.createSession).toHaveBeenCalledWith(5, 'test', true);

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
    expect(api.createSession).toHaveBeenCalledWith(5, 'multiple_choice', true);

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

  it('reveals the discuss control after flipping and opens the discussion panel (FLA-116)', async () => {
    vi.mocked(api.createSession).mockResolvedValue(session());
    vi.mocked(api.getDeck).mockResolvedValue({
      id: 5,
      title: 'Spanish',
      editable: true,
      discussionsEnabled: true,
      flashcards: [{ question: 'Q1', answer: 'A1', cardUid: 'uid-1' }],
    });
    vi.mocked(api.updateProgress).mockResolvedValue(session());
    vi.mocked(api.getDiscussionThread).mockResolvedValue({ cardUid: 'uid-1', isLocked: false, messageCount: 1 });
    vi.mocked(api.getDiscussionMessages).mockResolvedValue({
      items: [{ id: 1, authorDisplayName: 'Alice', content: 'Nice card', parentMessageId: null, createdAtMillis: Date.now() }],
      nextCursor: null,
    });
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=flashcards']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
        </Routes>
      </MemoryRouter>,
    );

    // The control only appears once the answer is revealed.
    await screen.findByText('Q1');
    expect(screen.queryByRole('button', { name: /Discuss this card/ })).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Show answer' }));
    await userEvent.click(screen.getByRole('button', { name: /Discuss this card/ }));

    expect(await screen.findByText('Nice card')).toBeInTheDocument();
    expect(api.getDiscussionThread).toHaveBeenCalledWith('uid-1');
  });

  it('hides the discuss control when the discussions flag is off (FLA-180)', async () => {
    mockDiscussionsFlag = false; // kill switch off for this signed-in user
    vi.mocked(api.createSession).mockResolvedValue(session());
    vi.mocked(api.getDeck).mockResolvedValue({
      id: 5,
      title: 'Spanish',
      editable: true,
      discussionsEnabled: true, // deck allows it, but the flag gates it off
      flashcards: [{ question: 'Q1', answer: 'A1', cardUid: 'uid-1' }],
    });
    vi.mocked(api.updateProgress).mockResolvedValue(session());
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=flashcards']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('Q1');
    await userEvent.click(screen.getByRole('button', { name: 'Show answer' }));
    // Even revealed + deck-enabled, the discuss control is absent because the flag is off.
    expect(screen.queryByRole('button', { name: /Discuss this card/ })).toBeNull();
  });

  describe('guest mode (no account)', () => {
    function guestSetup(cards: FlashcardDto[]) {
      mockToken = null;
      vi.mocked(api.getCatalogDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: false, flashcards: cards });
      render(
        // shuffle=0 keeps the catalog order so these tests can assert on Q1/Q2/Q3 (a dedicated test
        // below covers the guest shuffle path).
        <MemoryRouter initialEntries={['/decks/5/practice?mode=flashcards&shuffle=0']}>
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

    it('shuffle on (default): applies a randomized in-memory order (FLA-200)', async () => {
      // Pin the guest seed via Math.random so the order is deterministic, then assert the first card
      // shown is the shuffle's first card — verifying the wiring (URL shuffle → orderCards).
      vi.spyOn(Math, 'random').mockReturnValue(0.42);
      const seed = Math.floor(0.42 * (2 ** 31 - 1)) + 1;
      const expectedFirst = orderCards(threeCards, true, seed)[0].question;

      mockToken = null;
      vi.mocked(api.getCatalogDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: false, flashcards: threeCards });
      render(
        <MemoryRouter initialEntries={['/decks/5/practice?mode=flashcards&shuffle=1']}>
          <Routes>
            <Route path="/decks/:id/practice" element={<PracticePage />} />
          </Routes>
        </MemoryRouter>,
      );

      expect(await screen.findByText(expectedFirst)).toBeInTheDocument();
      expect(api.createSession).not.toHaveBeenCalled();
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
      // Guest route carried shuffle=0, so the saved session is created unshuffled.
      expect(api.createSession).toHaveBeenCalledWith(5, 'flashcards', false);
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
