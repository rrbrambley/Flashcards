import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { LibraryPage } from './LibraryPage';
import { api } from '../api/client';

const authState = vi.hoisted(() => ({ canManageGlobal: false, canManageRoles: false }));

vi.mock('../api/client', () => ({ api: { getDecks: vi.fn(), getAllSessions: vi.fn(), deleteDeck: vi.fn() } }));
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({
    signOut: vi.fn(),
    can: (p: string) =>
      (p === 'manage_global_decks' && authState.canManageGlobal) || (p === 'manage_roles' && authState.canManageRoles),
  }),
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
    authState.canManageRoles = false;
  });

  it('hides the "Manage global decks" action for non-admins', async () => {
    vi.mocked(api.getDecks).mockResolvedValue({ items: [], nextCursor: null });
    renderPage();
    await screen.findByText(/No decks yet/);
    expect(screen.queryByRole('button', { name: 'Manage global decks' })).not.toBeInTheDocument();
  });

  it('shows the "Manage global decks" action for admins', async () => {
    vi.mocked(api.getDecks).mockResolvedValue({ items: [], nextCursor: null });
    authState.canManageGlobal = true;
    renderPage();
    expect(await screen.findByRole('button', { name: 'Manage global decks' })).toBeInTheDocument();
  });

  it('shows the "Manage users" action only for users with manage_roles', async () => {
    vi.mocked(api.getDecks).mockResolvedValue({ items: [], nextCursor: null });
    renderPage();
    await screen.findByText(/No decks yet/);
    expect(screen.queryByRole('button', { name: 'Manage users' })).not.toBeInTheDocument();
  });

  it('shows the "Manage users" action for role admins', async () => {
    vi.mocked(api.getDecks).mockResolvedValue({ items: [], nextCursor: null });
    authState.canManageRoles = true;
    renderPage();
    expect(await screen.findByRole('button', { name: 'Manage users' })).toBeInTheDocument();
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

  it('shows a deck category label and matches it in search', async () => {
    vi.mocked(api.getDecks).mockResolvedValue({
      items: [
        { id: 1, title: 'Spanish basics', flashcards: [], tags: ['Language'] },
        { id: 2, title: 'Flags', flashcards: [], tags: ['Geography'] },
      ],
      nextCursor: null,
    });
    renderPage();

    // The category renders as a small label on the row.
    expect(await screen.findByText('Geography')).toBeInTheDocument();

    // Searching by a tag matches the deck even though its title doesn't contain the query.
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search decks' }), 'geo');
    expect(screen.getByText('Flags')).toBeInTheDocument();
    expect(screen.queryByText('Spanish basics')).not.toBeInTheDocument();
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

  // #380: the row's main area used to navigate straight to Edit — the biggest target on the row going
  // where people rarely want. It now offers the choice, matching the deck-tap sheet on Android/iOS.
  describe('deck actions sheet', () => {
    const deck = (over: Partial<{ id: number; title: string; editable: boolean; cards: number }> = {}) => ({
      id: over.id ?? 1,
      title: over.title ?? 'Spanish',
      editable: over.editable ?? true,
      flashcards: Array.from({ length: over.cards ?? 2 }, () => ({ question: 'q', answer: 'a' })),
      tags: [],
    });

    const openSheet = async (title = 'Spanish') => {
      renderPage();
      const row = await screen.findByRole('button', { name: new RegExp(title) });
      await userEvent.click(row);
    };

    it('opens the actions sheet rather than navigating to Edit', async () => {
      vi.mocked(api.getDecks).mockResolvedValue({ items: [deck()], nextCursor: null });
      await openSheet();

      expect(screen.getByRole('dialog', { name: /Actions for Spanish/ })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
    });

    it('offers Practice for a deck that has cards', async () => {
      vi.mocked(api.getDecks).mockResolvedValue({ items: [deck()], nextCursor: null });
      await openSheet();
      // Two: the row shortcut and the sheet's own.
      expect(screen.getAllByRole('button', { name: 'Practice' }).length).toBeGreaterThan(1);
    });

    it('omits Edit for a deck the user cannot edit', async () => {
      // A global catalog deck: clicking the row used to land on a form nothing could be changed on.
      vi.mocked(api.getDecks).mockResolvedValue({
        items: [deck({ title: 'Flags of the World', editable: false })],
        nextCursor: null,
      });
      await openSheet('Flags of the World');

      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    });

    it('omits Practice for an empty deck', async () => {
      vi.mocked(api.getDecks).mockResolvedValue({ items: [deck({ cards: 0 })], nextCursor: null });
      await openSheet();
      expect(screen.queryByRole('button', { name: 'Practice' })).not.toBeInTheDocument();
    });

    it('closes on Cancel', async () => {
      vi.mocked(api.getDecks).mockResolvedValue({ items: [deck()], nextCursor: null });
      await openSheet();
      await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  // #382: Delete from the library sheet, for parity with Android/iOS — web previously only offered it
  // from inside Edit. Confirmed in place rather than by stacking a second overlay.
  describe('deleting from the actions sheet', () => {
    const owned = {
      id: 1,
      title: 'Spanish',
      editable: true,
      flashcards: [{ question: 'q', answer: 'a' }],
      tags: [],
    };

    const openSheet = async (deck = owned) => {
      vi.mocked(api.getDecks).mockResolvedValue({ items: [deck], nextCursor: null });
      renderPage();
      await userEvent.click(await screen.findByRole('button', { name: new RegExp(deck.title) }));
    };

    it('confirms before deleting, and only then calls the API', async () => {
      await openSheet();
      await userEvent.click(screen.getByRole('button', { name: 'Delete' }));

      // Still nothing deleted — the sheet swapped to the confirmation.
      expect(api.deleteDeck).not.toHaveBeenCalled();
      expect(screen.getByRole('dialog', { name: /Delete Spanish\?/ })).toBeInTheDocument();

      vi.mocked(api.deleteDeck).mockResolvedValue(undefined);
      await userEvent.click(screen.getByRole('button', { name: 'Delete deck' }));
      await waitFor(() => expect(api.deleteDeck).toHaveBeenCalledWith(1));
    });

    it('removes the deck from the list once deleted', async () => {
      await openSheet();
      vi.mocked(api.deleteDeck).mockResolvedValue(undefined);
      await userEvent.click(screen.getByRole('button', { name: 'Delete' }));
      await userEvent.click(screen.getByRole('button', { name: 'Delete deck' }));

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(screen.queryByText('Spanish')).not.toBeInTheDocument();
    });

    it('keeps the deck and shows why when the delete fails', async () => {
      await openSheet();
      vi.mocked(api.deleteDeck).mockRejectedValue(new Error('Deck is in use'));
      await userEvent.click(screen.getByRole('button', { name: 'Delete' }));
      await userEvent.click(screen.getByRole('button', { name: 'Delete deck' }));

      expect(await screen.findByText('Deck is in use')).toBeInTheDocument();
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('backs out of the confirmation without deleting', async () => {
      await openSheet();
      await userEvent.click(screen.getByRole('button', { name: 'Delete' }));
      await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

      // Back to the actions, deck untouched.
      expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
      expect(api.deleteDeck).not.toHaveBeenCalled();
    });

    it('does not offer Delete on a deck the user cannot edit', async () => {
      await openSheet({ ...owned, title: 'Flags of the World', editable: false });
      expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });
  });
});
