import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { LibraryPage } from './LibraryPage';
import { api } from '../api/client';

vi.mock('../api/client', () => ({ api: { getDecks: vi.fn() } }));
vi.mock('../auth/auth-context', () => ({ useAuth: () => ({ signOut: vi.fn() }) }));

function renderPage() {
  render(
    <MemoryRouter>
      <LibraryPage />
    </MemoryRouter>,
  );
}

describe('LibraryPage', () => {
  beforeEach(() => vi.clearAllMocks());

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
