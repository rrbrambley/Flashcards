import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom';
import { HomePage } from './HomePage';
import { api } from '../api/client';
import type { HomeData } from '../api/types';

vi.mock('../api/client', () => ({
  api: { getHome: vi.fn(), getSession: vi.fn(), deleteSession: vi.fn(), getAllDecks: vi.fn(), getStreaks: vi.fn() },
}));
vi.mock('../auth/auth-context', () => ({ useAuth: () => ({ signOut: vi.fn() }) }));

function PracticeStub() {
  const { id } = useParams();
  return <div>practice-{id}</div>;
}

function renderHome() {
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/create" element={<div>create page</div>} />
        <Route path="/library" element={<div>library page</div>} />
        <Route path="/decks/:id/practice" element={<PracticeStub />} />
      </Routes>
    </MemoryRouter>,
  );
}

const continueItem: HomeData = {
  title: 'Spanish',
  section: 'Continue studying',
  button: { message: 'Resume', action: { type: 'continue_practice', sessionId: 7 } },
};
const continueItemWithSession: HomeData = {
  title: 'Spanish',
  section: 'Continue studying',
  button: { message: 'Resume', action: { type: 'continue_practice', sessionId: 7 } },
  session: { mode: 'multiple_choice', numCorrect: 3, numIncorrect: 1, currentCardIndex: 4, totalCards: 10 },
};
const practiceItem: HomeData = {
  title: 'Practice Flags of the World',
  section: 'Study something new',
  button: { message: 'Practice', action: { type: 'navigate_to_practice', deckId: 9 } },
};
const createItem: HomeData = {
  title: 'Create a new flashcard set',
  section: 'Study something new',
  button: { message: 'Create', action: { type: 'create_new_flashcard_set' } },
};

describe('HomePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: no active streak, so the badge is absent unless a test opts in.
    vi.mocked(api.getStreaks).mockResolvedValue({ overall: { current: 0, longest: 0 }, decks: [] });
  });

  it('renders the home feed items and their buttons', async () => {
    vi.mocked(api.getHome).mockResolvedValue([continueItem, practiceItem, createItem]);
    renderHome();

    expect(await screen.findByText('Spanish')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Resume' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create' })).toBeInTheDocument();
  });

  it('groups items under their section headers', async () => {
    vi.mocked(api.getHome).mockResolvedValue([continueItem, practiceItem, createItem]);
    renderHome();

    expect(await screen.findByRole('heading', { name: 'Continue studying' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Study something new' })).toBeInTheDocument();
    // The two "Study something new" items share a single header, not one each.
    expect(screen.getAllByRole('heading', { name: 'Study something new' })).toHaveLength(1);
  });

  it('shows mode, score and progress for an in-progress session', async () => {
    vi.mocked(api.getHome).mockResolvedValue([continueItemWithSession]);
    renderHome();

    expect(await screen.findByText('Multiple Choice')).toBeInTheDocument();
    expect(screen.getByText('✓ 3')).toBeInTheDocument();
    expect(screen.getByText('✗ 1')).toBeInTheDocument();
    expect(screen.getByText('5 of 10')).toBeInTheDocument();
    // 4 of 10 cards reached → 40%.
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '40');
  });

  it('continue tile resumes the session via its deck', async () => {
    vi.mocked(api.getHome).mockResolvedValue([continueItem]);
    vi.mocked(api.getSession).mockResolvedValue({ id: 7, deckId: 3, mode: 'flashcards' } as never);
    renderHome();

    await userEvent.click(await screen.findByRole('button', { name: 'Resume' }));

    expect(api.getSession).toHaveBeenCalledWith(7);
    expect(await screen.findByText('practice-3')).toBeInTheDocument();
  });

  it('shows the remove "×" only on in-progress session cards', async () => {
    vi.mocked(api.getHome).mockResolvedValue([continueItem, practiceItem, createItem]);
    renderHome();

    await screen.findByText('Spanish');
    // One removable card (the continue tile); the featured/create cards have no ×.
    expect(screen.getAllByRole('button', { name: 'Remove practice session' })).toHaveLength(1);
  });

  it('removes a session after confirming, and deletes it via the api', async () => {
    vi.mocked(api.getHome).mockResolvedValue([continueItemWithSession, createItem]);
    vi.mocked(api.deleteSession).mockResolvedValue(undefined as never);
    renderHome();

    await screen.findByText('Spanish');
    await userEvent.click(screen.getByRole('button', { name: 'Remove practice session' }));
    // Confirm dialog, then Remove.
    expect(await screen.findByText('Remove practice session?')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }));

    expect(api.deleteSession).toHaveBeenCalledWith(7);
    // The card is gone; the create card remains.
    expect(screen.queryByText('Spanish')).not.toBeInTheDocument();
    expect(screen.getByText('Create a new flashcard set')).toBeInTheDocument();
  });

  it('cancelling the confirm keeps the session and calls nothing', async () => {
    vi.mocked(api.getHome).mockResolvedValue([continueItemWithSession]);
    renderHome();

    await screen.findByText('Spanish');
    await userEvent.click(screen.getByRole('button', { name: 'Remove practice session' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }));

    expect(api.deleteSession).not.toHaveBeenCalled();
    expect(screen.getByText('Spanish')).toBeInTheDocument();
  });

  it('practice tile routes to the deck id in the action', async () => {
    vi.mocked(api.getHome).mockResolvedValue([practiceItem]);
    renderHome();

    await userEvent.click(await screen.findByRole('button', { name: 'Practice' }));

    expect(await screen.findByText('practice-9')).toBeInTheDocument();
  });

  it('create tile routes to the create page', async () => {
    vi.mocked(api.getHome).mockResolvedValue([createItem]);
    renderHome();

    await userEvent.click(await screen.findByRole('button', { name: 'Create' }));

    expect(await screen.findByText('create page')).toBeInTheDocument();
  });

  it('shows the overall streak badge when there is an active streak', async () => {
    vi.mocked(api.getHome).mockResolvedValue([createItem]);
    vi.mocked(api.getStreaks).mockResolvedValue({ overall: { current: 4, longest: 9 }, decks: [] });
    renderHome();

    expect(await screen.findByText(/4 day streak/)).toBeInTheDocument();
  });

  it('hides the streak badge when the current streak is zero', async () => {
    vi.mocked(api.getHome).mockResolvedValue([createItem]);
    renderHome();

    await screen.findByText('Create a new flashcard set');
    expect(screen.queryByText(/day streak/)).not.toBeInTheDocument();
  });

  it('shows an error when the feed fails to load', async () => {
    vi.mocked(api.getHome).mockRejectedValue(new Error('offline'));
    renderHome();

    expect(await screen.findByText('offline')).toBeInTheDocument();
  });
});
