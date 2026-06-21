import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { EditDeckPage } from './EditDeckPage';
import { ApiError, api } from '../api/client';
import type { FlashcardDeckDto } from '../api/types';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      getDeck: vi.fn(),
      updateDeck: vi.fn(),
      deleteDeck: vi.fn(),
      setDeckGlobal: vi.fn(),
      setDeckDiscussionsEnabled: vi.fn(),
    },
  };
});

// The admin "Deck settings" section is gated on permissions; default to a non-admin so the
// existing edit tests see no settings section. Per-test overrides grant specific permissions.
const NO_PERMISSIONS: string[] = [];
const can = vi.fn((permission: string) => NO_PERMISSIONS.includes(permission));
vi.mock('../auth/auth-context', () => ({ useAuth: () => ({ can }) }));

function renderPage(from?: string) {
  render(
    <MemoryRouter initialEntries={[{ pathname: '/decks/5/edit', state: from ? { from } : undefined }]}>
      <Routes>
        <Route path="/decks/:id/edit" element={<EditDeckPage />} />
        <Route path="/library" element={<div>library</div>} />
        <Route path="/library/global" element={<div>global library</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

const deck = (over: Partial<FlashcardDeckDto> = {}): FlashcardDeckDto => ({
  id: 5,
  title: 'Capitals',
  editable: true,
  flashcards: [{ question: 'France?', answer: 'Paris', imageUrl: null }],
  ...over,
});

describe('EditDeckPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    can.mockImplementation((permission: string) => NO_PERMISSIONS.includes(permission)); // default: non-admin
  });

  it('loads the deck into an editable form', async () => {
    vi.mocked(api.getDeck).mockResolvedValue(deck());
    renderPage();

    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument();
    expect(screen.getByDisplayValue('Capitals')).toBeInTheDocument();
  });

  it('renders read-only when the deck is not editable', async () => {
    vi.mocked(api.getDeck).mockResolvedValue(deck({ editable: false }));
    renderPage();

    expect(await screen.findByText("This deck is read-only and can't be edited.")).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save changes' })).toBeNull();
  });

  it('hides the admin Deck settings section for a non-admin', async () => {
    vi.mocked(api.getDeck).mockResolvedValue(deck());
    renderPage();

    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Deck settings' })).toBeNull();
  });

  it('shows the Deck settings section to a global-decks admin', async () => {
    can.mockImplementation((p: string) => p === 'manage_global_decks');
    vi.mocked(api.getDeck).mockResolvedValue(deck());
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Deck settings' })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Global deck' })).toBeInTheDocument();
  });

  it('maps a 404 on save to a friendly message', async () => {
    vi.mocked(api.getDeck).mockResolvedValue(deck());
    vi.mocked(api.updateDeck).mockRejectedValue(new ApiError(404, 'not found'));
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText("This deck can't be edited.")).toBeInTheDocument();
  });

  it('deletes the deck after confirmation and returns home', async () => {
    vi.mocked(api.getDeck).mockResolvedValue(deck());
    vi.mocked(api.deleteDeck).mockResolvedValue(undefined);
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Delete deck' }));
    // A confirmation step appears before anything is deleted.
    expect(screen.getByText(/can't be undone/i)).toBeInTheDocument();
    expect(api.deleteDeck).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Delete deck' }));

    expect(api.deleteDeck).toHaveBeenCalledWith(5);
    expect(await screen.findByText('library')).toBeInTheDocument();
  });

  it('returns to the global list after deleting a deck opened from it', async () => {
    vi.mocked(api.getDeck).mockResolvedValue(deck());
    vi.mocked(api.deleteDeck).mockResolvedValue(undefined);
    renderPage('/library/global');

    // The back link points at the global list, not the personal library.
    expect(await screen.findByRole('link', { name: '← Global decks' })).toHaveAttribute('href', '/library/global');

    await userEvent.click(screen.getByRole('button', { name: 'Delete deck' }));
    await userEvent.click(screen.getByRole('button', { name: 'Delete deck' }));

    expect(api.deleteDeck).toHaveBeenCalledWith(5);
    expect(await screen.findByText('global library')).toBeInTheDocument();
  });

  it('does not offer delete for a read-only deck', async () => {
    vi.mocked(api.getDeck).mockResolvedValue(deck({ editable: false }));
    renderPage();

    await screen.findByText("This deck is read-only and can't be edited.");
    expect(screen.queryByRole('button', { name: 'Delete deck' })).toBeNull();
  });
});
