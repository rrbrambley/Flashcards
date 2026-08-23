import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { PracticePage } from './PracticePage';
import { api } from '../api/client';
import { installFakeSpeechRecognition } from '../test/fakeSpeechRecognition';
import { VOICE_INPUT_KEY } from './voice/preference';
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
    suggestAnswer: vi.fn(),
    getDiscussionThread: vi.fn(),
    getDiscussionMessages: vi.fn(),
  },
}));

// Default: signed in. Guest tests set mockToken = null. applyAuth/token come from the auth context.
let mockToken: string | null = 'test-token';
const applyAuth = vi.fn();
let mockCan = false;
// Keyed per flag, and fail-closed like the real `isEnabled` — one shared boolean would have made a
// test that turns the `discussions` kill switch off silently turn every other flag off too.
let mockFlags: Record<string, boolean> = {};
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({
    token: mockToken,
    applyAuth,
    can: () => mockCan,
    isEnabled: (key: string) => mockFlags[key] === true,
  }),
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
  questionCount: null,
  gradeAtEnd: false,
  timeLimitSeconds: null,
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
    // What a real signed-in user gets: the default-ON flags on. `practice_voice_input` is absent
    // because it's default-OFF (#387) — voice tests opt in explicitly.
    mockFlags = {
      discussions: true,
      practice_mode_classic: true,
      practice_mode_test: true,
      practice_mode_multiple_choice: true,
    };
  });

  it('starts a session in the default (classic) mode and shows the (resumed) current card', async () => {
    setup(threeCards, { currentCardIndex: 1 });
    expect(await screen.findByText('Q2')).toBeInTheDocument();
    // No `shuffle=` in the route → the toggle defaults On (FLA-200), so the new session is created shuffled.
    expect(api.createSession).toHaveBeenCalledWith(5, 'flashcards', true, null, false, null);
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

  it('resumes the in-session streak from the answer log (FLA-99)', async () => {
    // Resume mid-session (index 1) whose answer log ends in two corrects.
    setup(
      [
        { question: 'Q1', answer: 'A1', cardUid: 'c1' },
        { question: 'Q2', answer: 'A2', cardUid: 'c2' },
        { question: 'Q3', answer: 'A3', cardUid: 'c3' },
      ],
      { currentCardIndex: 1, numCorrect: 2, numIncorrect: 0 },
    );
    vi.mocked(api.getAnswers).mockResolvedValue([
      { answerUid: 'a1', cardUid: 'c1', correct: true, sequence: 0, answeredAtMillis: 0, submittedText: null },
      { answerUid: 'a2', cardUid: 'c2', correct: true, sequence: 1, answeredAtMillis: 0, submittedText: null },
    ]);

    // Resumes at Q2. Answering it correctly continues the streak to 3 — proving it restored from the
    // log (a fresh reset would leave it at 1, which doesn't even surface the badge).
    await screen.findByText('Q2');
    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));

    expect(await screen.findByText(/3 in a row/)).toBeInTheDocument();
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

  it('limits the run to the question count and passes it to createSession (FLA-219)', async () => {
    vi.mocked(api.createSession).mockResolvedValue(session({ questionCount: 2 }));
    vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: true, flashcards: threeCards });
    vi.mocked(api.updateProgress).mockResolvedValue(session());
    vi.mocked(api.completeSession).mockResolvedValue(session({ isCompleted: true }));
    vi.mocked(api.recordAnswers).mockResolvedValue(session());
    vi.mocked(api.getAnswers).mockResolvedValue([]);
    vi.mocked(api.getStreaks).mockResolvedValue({ overall: { current: 3, longest: 5 }, decks: [] });
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=flashcards&shuffle=0&questions=2']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
          <Route path="/" element={<div>library</div>} />
        </Routes>
      </MemoryRouter>,
    );

    await screen.findByText('Q1');
    // The URL count is sent to the server (resume-authoritative for the subset).
    expect(api.createSession).toHaveBeenCalledWith(5, 'flashcards', false, 2, false, null);

    // Only 2 of the 3 cards play: answering both completes the run and Q3 never appears.
    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));
    await screen.findByText('Q2');
    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));

    expect(await screen.findByText('Practice complete')).toBeInTheDocument();
    expect(screen.queryByText('Q3')).not.toBeInTheDocument();
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
    // The chooser fetches the deck for its "Practice <deck>" title.
    vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: true, flashcards: threeCards });
    render(
      <MemoryRouter initialEntries={['/decks/5/practice']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('Choose a mode')).toBeInTheDocument();
    expect(screen.getByText('Test')).toBeInTheDocument();
    // Start lives on the mode's settings step now, not alongside the mode list (#410).
    expect(screen.queryByRole('button', { name: 'Start practice' })).not.toBeInTheDocument();
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
    expect(api.createSession).toHaveBeenCalledWith(5, 'test', true, null, false, null);

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
    expect(api.createSession).toHaveBeenCalledWith(5, 'multiple_choice', true, null, false, null);

    await userEvent.click(screen.getByRole('button', { name: /A1/ })); // the correct option
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByText('Q2')).toBeInTheDocument();
    expect(api.updateProgress).toHaveBeenCalledWith(1, { currentCardIndex: 1, numCorrect: 1, numIncorrect: 0 });
  });

  it('grade-at-the-end shows all cards at once, then grades on submit (#293)', async () => {
    vi.mocked(api.createSession).mockResolvedValue(session({ mode: 'test', gradeAtEnd: true }));
    vi.mocked(api.getDeck).mockResolvedValue({
      id: 5,
      title: 'Spanish',
      editable: true,
      flashcards: [
        { question: 'Q1', answer: 'A1', cardUid: 'c1' },
        { question: 'Q2', answer: 'A2', cardUid: 'c2' },
      ],
    });
    vi.mocked(api.recordAnswers).mockResolvedValue(session());
    vi.mocked(api.completeSession).mockResolvedValue(session({ isCompleted: true }));
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=test&shuffle=0&gradeAtEnd=1']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
          <Route path="/" element={<div>library</div>} />
        </Routes>
      </MemoryRouter>,
    );

    // The whole session is created grade-at-end, and every card shows at once (not one at a time).
    expect(await screen.findByText('Q1')).toBeInTheDocument();
    expect(screen.getByText('Q2')).toBeInTheDocument();
    expect(api.createSession).toHaveBeenCalledWith(5, 'test', false, null, true, null);

    // Answer Q1 right and Q2 wrong, then submit.
    await userEvent.type(screen.getByLabelText('Answer for question 1'), 'A1');
    await userEvent.type(screen.getByLabelText('Answer for question 2'), 'nope');
    await userEvent.click(screen.getByRole('button', { name: /Submit/ }));

    // Results screen revealed (#298): the shared "Practice complete" recap, not an inline grade;
    // the whole batch was logged and the session completed.
    expect(await screen.findByText('Practice complete')).toBeInTheDocument();
    expect(screen.getByText('You reviewed 2 cards.')).toBeInTheDocument();
    expect(api.recordAnswers).toHaveBeenCalled();
    expect(api.completeSession).toHaveBeenCalled();
  });

  it('offers "this should be correct" on a wrong Test row of a global-deck batch recap (#338)', async () => {
    vi.mocked(api.createSession).mockResolvedValue(session({ mode: 'test', gradeAtEnd: true }));
    vi.mocked(api.getDeck).mockResolvedValue({
      id: 5,
      title: 'Capitals',
      editable: false,
      isGlobal: true,
      flashcards: [
        { question: 'Q1', answer: 'A1', cardUid: 'c1' },
        { question: 'Q2', answer: 'A2', cardUid: 'c2' },
      ],
    });
    vi.mocked(api.recordAnswers).mockResolvedValue(session());
    vi.mocked(api.completeSession).mockResolvedValue(session({ isCompleted: true }));
    vi.mocked(api.suggestAnswer).mockResolvedValue(undefined);
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=test&shuffle=0&gradeAtEnd=1']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
          <Route path="/" element={<div>library</div>} />
        </Routes>
      </MemoryRouter>,
    );

    // Answer Q1 right and Q2 wrong, then submit.
    await userEvent.type(await screen.findByLabelText('Answer for question 1'), 'A1');
    await userEvent.type(screen.getByLabelText('Answer for question 2'), 'nope');
    await userEvent.click(screen.getByRole('button', { name: /Submit/ }));

    // Only the one wrong row gets the affordance — the correct row doesn't.
    expect(await screen.findByText('Practice complete')).toBeInTheDocument();
    const suggestButtons = screen.getAllByRole('button', { name: 'This should be correct' });
    expect(suggestButtons).toHaveLength(1);

    // It suggests the typed answer for the wrong card.
    await userEvent.click(suggestButtons[0]);
    expect(await screen.findByText(/sent for review/)).toBeInTheDocument();
    expect(api.suggestAnswer).toHaveBeenCalledWith('c2', 'nope');
  });

  it('offers "this should be correct" on the card-by-card (non-batch) completion review too (#361)', async () => {
    vi.mocked(api.createSession).mockResolvedValue(session({ mode: 'test' }));
    vi.mocked(api.getDeck).mockResolvedValue({
      id: 5,
      title: 'Capitals',
      editable: false,
      isGlobal: true,
      flashcards: [{ question: 'Q1', answer: 'A1', cardUid: 'c1' }],
    });
    vi.mocked(api.updateProgress).mockResolvedValue(session());
    vi.mocked(api.completeSession).mockResolvedValue(session({ isCompleted: true }));
    vi.mocked(api.getAnswers).mockResolvedValue([
      { answerUid: 'a1', cardUid: 'c1', correct: false, sequence: 0, answeredAtMillis: 0, submittedText: 'wrong' },
    ]);
    vi.mocked(api.suggestAnswer).mockResolvedValue(undefined);
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=test']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
        </Routes>
      </MemoryRouter>,
    );

    // Answer the single card wrong, then advance past it to complete the (card-by-card) session.
    await userEvent.type(await screen.findByLabelText('Your answer'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    // The completion review (not grade-at-the-end) offers the suggestion on the wrong Test row.
    expect(await screen.findByText('Practice complete')).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: 'This should be correct' }));
    expect(await screen.findByText(/sent for review/)).toBeInTheDocument();
    expect(api.suggestAnswer).toHaveBeenCalledWith('c1', 'wrong');
  });

  /**
   * The reported bug (#398): the recap listed the same card twice — once ✓ with the user's answer,
   * once ✗ with none. The window isn't a millisecond race: Test grades when the verdict appears and
   * only advances on "Next", so the runner sits on an answered card for as long as it's being read.
   */
  it('does not record a second answer when the clock expires on an already-answered card (#398)', async () => {
    // 2s limit from now: long enough to answer, short enough to expire while the verdict is up.
    vi.mocked(api.createSession).mockResolvedValue(
      session({ mode: 'test', createdAtMillis: Date.now(), timeLimitSeconds: 2 }),
    );
    vi.mocked(api.getDeck).mockResolvedValue({
      id: 5,
      title: 'Spanish',
      editable: true,
      flashcards: [{ cardUid: 'c1', question: 'Q1', answer: 'A1' }],
    });
    vi.mocked(api.completeSession).mockResolvedValue(session({ isCompleted: true }));
    vi.mocked(api.getAnswers).mockResolvedValue([]);
    vi.mocked(api.getStreaks).mockResolvedValue({ overall: { current: 1, longest: 1 }, decks: [] });
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=test&shuffle=0&timeLimit=2']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
          <Route path="/" element={<div>library</div>} />
        </Routes>
      </MemoryRouter>,
    );

    await userEvent.type(await screen.findByLabelText('Your answer'), 'a1');
    await userEvent.click(screen.getByRole('button', { name: 'Check' }));
    expect(await screen.findByText('✓ Correct')).toBeInTheDocument();
    expect(api.recordAnswers).toHaveBeenCalledTimes(1);

    // Let the clock run out while the verdict is still on screen.
    await waitFor(() => expect(screen.queryByLabelText('time remaining')).not.toBeInTheDocument(), {
      timeout: 4000,
    });

    // Exactly one answer for this card, and it keeps the verdict the user actually earned.
    expect(api.recordAnswers).toHaveBeenCalledTimes(1);
    expect(api.recordAnswers).toHaveBeenCalledWith(1, [expect.objectContaining({ cardUid: 'c1', correct: true })]);
  });

  it('auto-completes a timed session whose deadline has already passed (#289)', async () => {
    // Created at epoch 0 with a 1s limit → deadline is in 1970, so it's expired the moment it loads.
    vi.mocked(api.createSession).mockResolvedValue(
      session({ mode: 'test', createdAtMillis: 0, timeLimitSeconds: 1 }),
    );
    vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: true, flashcards: threeCards });
    vi.mocked(api.completeSession).mockResolvedValue(session({ isCompleted: true }));
    vi.mocked(api.getAnswers).mockResolvedValue([]);
    vi.mocked(api.getStreaks).mockResolvedValue({ overall: { current: 3, longest: 5 }, decks: [] });
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=test&timeLimit=1']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
          <Route path="/" element={<div>library</div>} />
        </Routes>
      </MemoryRouter>,
    );

    // The expired countdown ends the run at once → the completion screen, and the session is completed.
    expect(await screen.findByText('Practice complete')).toBeInTheDocument();
    expect(api.createSession).toHaveBeenCalledWith(5, 'test', true, null, false, 1);
    expect(api.completeSession).toHaveBeenCalled();
  });

  it('hides the back button while an in-progress single-sitting (timed) run is going (#307)', async () => {
    // A far-future deadline → the timed run stays in progress (not auto-completed).
    vi.mocked(api.createSession).mockResolvedValue(
      session({ mode: 'test', createdAtMillis: Date.now(), timeLimitSeconds: 300 }),
    );
    vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: true, flashcards: threeCards });
    render(
      <MemoryRouter initialEntries={['/decks/5/practice?mode=test&timeLimit=300']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
          <Route path="/" element={<div>library</div>} />
        </Routes>
      </MemoryRouter>,
    );

    // The countdown is running (in progress) → the "← Back" control is gone so there's no casual exit.
    await screen.findByLabelText('time remaining');
    expect(screen.queryByText(/←/)).not.toBeInTheDocument();
  });

  describe('answering by voice (#387)', () => {
    let uninstall: () => void;
    beforeEach(() => {
      uninstall = installFakeSpeechRecognition();
      localStorage.setItem(VOICE_INPUT_KEY, 'true');
      mockFlags = { ...mockFlags, practice_voice_input: true };
    });
    afterEach(() => {
      localStorage.removeItem(VOICE_INPUT_KEY);
      uninstall();
    });

    const startTestRun = (search: string) => {
      vi.mocked(api.createSession).mockResolvedValue(
        session({
          mode: 'test',
          createdAtMillis: Date.now(),
          timeLimitSeconds: search.includes('timeLimit') ? 300 : null,
        }),
      );
      const deck = { id: 5, title: 'Spanish', editable: true, flashcards: threeCards };
      vi.mocked(api.getDeck).mockResolvedValue(deck);
      // Guests read the public catalog and run the session locally.
      vi.mocked(api.getCatalogDeck).mockResolvedValue({ ...deck, editable: false });
      // shuffle=0 keeps the catalog order, so these can assert on Q1 (the house idiom below).
      render(
        <MemoryRouter initialEntries={[`/decks/5/practice?mode=test&shuffle=0${search}`]}>
          <Routes>
            <Route path="/decks/:id/practice" element={<PracticePage />} />
            <Route path="/" element={<div>library</div>} />
          </Routes>
        </MemoryRouter>,
      );
    };

    it('offers the mic on an untimed run when the flag and the preference are both on', async () => {
      startTestRun('');
      expect(await screen.findByText(/Speech is processed/)).toBeInTheDocument();
    });

    /**
     * Timed runs get voice too. It costs the clock ~2.5s a card (Chrome's final transcript lags ~1s
     * behind end-of-speech, plus the grace window), but silently dropping a feature the user
     * explicitly switched on is worse than a slightly generous timer.
     */
    it('offers voice in a timed run too, rather than silently dropping it', async () => {
      startTestRun('&timeLimit=300');
      await screen.findByLabelText('time remaining');
      expect(await screen.findByText(/Speech is processed/)).toBeInTheDocument();
    });

    it('offers voice in Multiple Choice too (#388), not just Test', async () => {
      vi.mocked(api.createSession).mockResolvedValue(
        session({ mode: 'multiple_choice', createdAtMillis: Date.now(), timeLimitSeconds: null }),
      );
      const deck = { id: 5, title: 'Spanish', editable: true, flashcards: threeCards };
      vi.mocked(api.getDeck).mockResolvedValue(deck);
      render(
        <MemoryRouter initialEntries={['/decks/5/practice?mode=multiple_choice&shuffle=0']}>
          <Routes>
            <Route path="/decks/:id/practice" element={<PracticePage />} />
            <Route path="/" element={<div>library</div>} />
          </Routes>
        </MemoryRouter>,
      );

      expect(await screen.findByText(/Speech is processed/)).toBeInTheDocument();
    });

    it('offers nothing when the flag is off, even with the preference on', async () => {
      mockFlags = { ...mockFlags, practice_voice_input: false };
      startTestRun('');
      expect(await screen.findByText('Q1')).toBeInTheDocument();
      expect(screen.queryByText(/Speech is processed/)).not.toBeInTheDocument();
    });

    it('offers nothing to a guest, who carries no flags at all', async () => {
      mockToken = null;
      startTestRun('');
      expect(await screen.findByText('Q1')).toBeInTheDocument();
      expect(screen.queryByText(/Speech is processed/)).not.toBeInTheDocument();
    });
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
    mockFlags = { ...mockFlags, discussions: false }; // kill switch off for this signed-in user
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

  describe('back destination (FLA-168)', () => {
    function renderFrom(from: string | undefined) {
      vi.mocked(api.createSession).mockResolvedValue(session());
      vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: true, flashcards: threeCards });
      vi.mocked(api.updateProgress).mockResolvedValue(session());
      vi.mocked(api.getStreaks).mockResolvedValue({ overall: { current: 0, longest: 0 }, decks: [] });
      render(
        <MemoryRouter
          initialEntries={[{ pathname: '/decks/5/practice', search: '?mode=flashcards', state: from ? { from } : undefined }]}
        >
          <Routes>
            <Route path="/decks/:id/practice" element={<PracticePage />} />
            <Route path="/" element={<div>home page</div>} />
            <Route path="/library" element={<div>library page</div>} />
          </Routes>
        </MemoryRouter>,
      );
    }

    it('returns to Home when practice was launched from Home', async () => {
      renderFrom('/');
      await screen.findByText('Q1');

      await userEvent.click(screen.getByRole('button', { name: /Home/ }));
      expect(await screen.findByText('home page')).toBeInTheDocument();
    });

    it('returns to Library when practice was launched from Library', async () => {
      renderFrom('/library');
      await screen.findByText('Q1');

      await userEvent.click(screen.getByRole('button', { name: /Library/ }));
      expect(await screen.findByText('library page')).toBeInTheDocument();
    });

    it('falls back to Home for a deep link with no origin', async () => {
      renderFrom(undefined);
      await screen.findByText('Q1');

      await userEvent.click(screen.getByRole('button', { name: /Home/ }));
      expect(await screen.findByText('home page')).toBeInTheDocument();
    });
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
      expect(api.createSession).toHaveBeenCalledWith(5, 'flashcards', false, null);
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
