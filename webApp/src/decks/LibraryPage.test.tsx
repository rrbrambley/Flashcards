import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
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
    vi.mocked(api.getDecks).mockResolvedValue([
      { id: 1, title: 'Spanish', flashcards: [{ question: 'Hola', answer: 'Hello' }] },
    ]);
    renderPage();
    expect(await screen.findByText('Spanish')).toBeInTheDocument();
  });

  it('shows an empty state when there are no decks', async () => {
    vi.mocked(api.getDecks).mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText(/No decks yet/)).toBeInTheDocument();
  });

  it('shows the error message when loading fails', async () => {
    vi.mocked(api.getDecks).mockRejectedValue(new Error('boom'));
    renderPage();
    expect(await screen.findByText('boom')).toBeInTheDocument();
  });
});
