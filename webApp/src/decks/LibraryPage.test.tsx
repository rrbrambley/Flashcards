import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { LibraryPage } from './LibraryPage';
import { api } from '../api/client';

const authState = vi.hoisted(() => ({ canManageGlobal: false }));

vi.mock('../api/client', () => ({ api: { getDecks: vi.fn(), getAllSessions: vi.fn() } }));
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({ signOut: vi.fn(), can: (p: string) => p === 'manage_global_decks' && authState.canManageGlobal }),
}));

function renderPage() {
  render(
    <MemoryRouter>
      <LibraryPage />
    </MemoryRouter>,
  );
}

describe('LibraryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authState.canManageGlobal = false;
  });

  it('hides the "New global deck" action for non-admins', async () => {
    vi.mocked(api.getDecks).mockResolvedValue({ items: [], nextCursor: null });
    renderPage();
    await screen.findByText(/No decks yet/);
    expect(screen.queryByRole('button', { name: '+ New global deck' })).not.toBeInTheDocument();
  });

  it('shows the "New global deck" action for admins', async () => {
    vi.mocked(api.getDecks).mockResolvedValue({ items: [], nextCursor: null });
    authState.canManageGlobal = true;
    renderPage();
    expect(await screen.findByRole('button', { name: '+ New global deck' })).toBeInTheDocument();
  });

  it('renders the fetched decks', async () => {
    vi.mocked(api.getDecks).mockResolvedValue({
      items: [{ id: 1, title: 'Spanish', flashcards: [{ question: 'Hola', answer: 'Hello' }] }],
      nextCursor: null,
    });
    renderPage();
    expect(await screen.findByText('Spanish')).toBeInTheDocument();
  });

  it('shows an empty state when there are no decks', async () => {
    vi.mocked(api.getDecks).mockResolvedValue({ items: [], nextCursor: null });
    renderPage();
    expect(await screen.findByText(/No decks yet/)).toBeInTheDocument();
  });

  it('shows the error message when loading fails', async () => {
    vi.mocked(api.getDecks).mockRejectedValue(new Error('boom'));
    renderPage();
    expect(await screen.findByText('boom')).toBeInTheDocument();
  });

  it('defaults to A–Z and reorders by recently practiced when selected', async () => {
    vi.mocked(api.getDecks).mockResolvedValue({
      items: [
        { id: 1, title: 'Alpha', flashcards: [] },
        { id: 2, title: 'Beta', flashcards: [] },
      ],
      nextCursor: null,
    });
    vi.mocked(api.getAllSessions).mockResolvedValue([
      {
        id: 10,
        deckId: 2,
        deckTitle: 'Beta',
        currentCardIndex: 0,
        numCorrect: 0,
        numIncorrect: 0,
        isCompleted: true,
        createdAtMillis: 1,
        updatedAtMillis: 500,
      },
    ]);
    renderPage();

    // Default A–Z: Alpha before Beta.
    await screen.findByText('Alpha');
    expect(screen.getAllByRole('listitem')[0]).toHaveTextContent('Alpha');

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Sort decks' }), 'recent');

    // Beta was practiced most recently; Alpha never → Beta floats to the top.
    await waitFor(() => {
      expect(screen.getAllByRole('listitem')[0]).toHaveTextContent('Beta');
    });
    expect(api.getAllSessions).toHaveBeenCalledTimes(1);
  });

  it('filters decks by title as you type, with a no-match message', async () => {
    vi.mocked(api.getDecks).mockResolvedValue({
      items: [
        { id: 1, title: 'Spanish basics', flashcards: [] },
        { id: 2, title: 'French verbs', flashcards: [] },
      ],
      nextCursor: null,
    });
    renderPage();

    expect(await screen.findByText('Spanish basics')).toBeInTheDocument();
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search decks' }), 'french');

    expect(screen.getByText('French verbs')).toBeInTheDocument();
    expect(screen.queryByText('Spanish basics')).not.toBeInTheDocument();

    await userEvent.clear(screen.getByRole('searchbox', { name: 'Search decks' }));
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search decks' }), 'zzz');
    expect(screen.getByText(/No decks match/)).toBeInTheDocument();
  });

  it('appends the next page when "Load more" is clicked', async () => {
    vi.mocked(api.getDecks)
      .mockResolvedValueOnce({ items: [{ id: 1, title: 'Spanish', flashcards: [] }], nextCursor: 'c1' })
      .mockResolvedValueOnce({ items: [{ id: 2, title: 'French', flashcards: [] }], nextCursor: null });
    renderPage();

    expect(await screen.findByText('Spanish')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Load more' }));

    expect(await screen.findByText('French')).toBeInTheDocument();
    expect(screen.getByText('Spanish')).toBeInTheDocument();
    // The second page is fetched with the first page's cursor, and paging ends (button gone).
    expect(api.getDecks).toHaveBeenNthCalledWith(2, { cursor: 'c1' });
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument();
  });
});
