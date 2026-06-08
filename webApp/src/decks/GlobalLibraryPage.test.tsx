import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { GlobalLibraryPage } from './GlobalLibraryPage';
import { api } from '../api/client';

const authState = vi.hoisted(() => ({ canManageGlobal: false }));

vi.mock('../api/client', () => ({ api: { getGlobalDecks: vi.fn(), getAllSessions: vi.fn() } }));
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({ signOut: vi.fn(), can: (p: string) => p === 'manage_global_decks' && authState.canManageGlobal }),
}));

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/library/global']}>
      <Routes>
        <Route path="/library/global" element={<GlobalLibraryPage />} />
        <Route path="/library" element={<div>Personal library</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('GlobalLibraryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authState.canManageGlobal = false;
  });

  it('redirects non-admins to the personal library', () => {
    renderPage();
    expect(screen.getByText('Personal library')).toBeInTheDocument();
    expect(api.getGlobalDecks).not.toHaveBeenCalled();
  });

  it('lists the global catalog and offers the "New global deck" action for admins', async () => {
    authState.canManageGlobal = true;
    vi.mocked(api.getGlobalDecks).mockResolvedValue({
      items: [{ id: 1, title: 'Flags of the World', flashcards: [], editable: true }],
      nextCursor: null,
    });
    renderPage();

    expect(await screen.findByText('Flags of the World')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '+ New global deck' })).toBeInTheDocument();
    // It reads the global catalog, not the personal decks.
    expect(api.getGlobalDecks).toHaveBeenCalled();
  });

  it('shows the empty state when there are no global decks', async () => {
    authState.canManageGlobal = true;
    vi.mocked(api.getGlobalDecks).mockResolvedValue({ items: [], nextCursor: null });
    renderPage();
    expect(await screen.findByText(/No global decks yet/)).toBeInTheDocument();
  });
});
