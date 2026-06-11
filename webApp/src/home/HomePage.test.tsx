import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom';
import { HomePage } from './HomePage';
import { api } from '../api/client';
import type { HomeData } from '../api/types';

vi.mock('../api/client', () => ({
  api: { getHome: vi.fn(), getSession: vi.fn(), getAllDecks: vi.fn() },
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
  title: 'Continue Spanish practice',
  button: { message: 'Continue practice', action: { type: 'continue_practice', sessionId: 7 } },
};
const continueItemWithSession: HomeData = {
  title: 'Continue Spanish practice',
  button: { message: 'Continue practice', action: { type: 'continue_practice', sessionId: 7 } },
  session: { mode: 'multiple_choice', numCorrect: 3, numIncorrect: 1, currentCardIndex: 4, totalCards: 10 },
};
const practiceItem: HomeData = {
  title: 'Practice Flags of the World',
  button: { message: 'Practice', action: { type: 'navigate_to_practice', deckId: 9 } },
};
const createItem: HomeData = {
  title: 'Create a new flashcard set',
  button: { message: 'Create', action: { type: 'create_new_flashcard_set' } },
};

describe('HomePage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders the home feed items and their buttons', async () => {
    vi.mocked(api.getHome).mockResolvedValue([continueItem, practiceItem, createItem]);
    renderHome();

    expect(await screen.findByText('Continue Spanish practice')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continue practice' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create' })).toBeInTheDocument();
  });

  it('shows mode, score and progress for an in-progress session', async () => {
    vi.mocked(api.getHome).mockResolvedValue([continueItemWithSession]);
    renderHome();

    expect(await screen.findByText('Multiple Choice')).toBeInTheDocument();
    expect(screen.getByText('✓ 3 correct')).toBeInTheDocument();
    expect(screen.getByText('✗ 1 incorrect')).toBeInTheDocument();
    expect(screen.getByText('Card 5 of 10')).toBeInTheDocument();
    // 4 of 10 cards reached → 40%.
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '40');
  });

  it('continue tile resumes the session via its deck', async () => {
    vi.mocked(api.getHome).mockResolvedValue([continueItem]);
    vi.mocked(api.getSession).mockResolvedValue({ id: 7, deckId: 3, mode: 'flashcards' } as never);
    renderHome();

    await userEvent.click(await screen.findByRole('button', { name: 'Continue practice' }));

    expect(api.getSession).toHaveBeenCalledWith(7);
    expect(await screen.findByText('practice-3')).toBeInTheDocument();
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

  it('shows an error when the feed fails to load', async () => {
    vi.mocked(api.getHome).mockRejectedValue(new Error('offline'));
    renderHome();

    expect(await screen.findByText('offline')).toBeInTheDocument();
  });
});
