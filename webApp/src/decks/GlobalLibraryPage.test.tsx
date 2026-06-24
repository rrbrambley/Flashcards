import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { GlobalLibraryPage } from './GlobalLibraryPage';
import { api } from '../api/client';

const authState = vi.hoisted(() => ({
  canManageGlobal: false,
  canManageDiscussions: false,
  permissionsReady: true,
}));

vi.mock('../api/client', () => ({
  api: { getGlobalDecks: vi.fn(), getAllSessions: vi.fn(), setDeckDiscussionsEnabled: vi.fn() },
}));
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({
    signOut: vi.fn(),
    can: (p: string) =>
      (p === 'manage_global_decks' && authState.canManageGlobal) ||
      (p === 'manage_discussions' && authState.canManageDiscussions),
    permissionsReady: authState.permissionsReady,
  }),
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
    authState.canManageDiscussions = false;
    authState.permissionsReady = true;
  });

  it('redirects non-admins to the personal library', () => {
    renderPage();
    expect(screen.getByText('Personal library')).toBeInTheDocument();
    expect(api.getGlobalDecks).not.toHaveBeenCalled();
  });

  it('shows a loading state (not a redirect) while permissions are still hydrating', () => {
    authState.permissionsReady = false; // cold load: /auth/me not resolved yet
    renderPage();
    expect(screen.getByText('Loading…')).toBeInTheDocument();
    expect(screen.queryByText('Personal library')).not.toBeInTheDocument();
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

  it('an admin toggles discussions on a deck via the switch, and the management view hides Practice (FLA-116)', async () => {
    authState.canManageGlobal = true;
    authState.canManageDiscussions = true;
    vi.mocked(api.getGlobalDecks).mockResolvedValue({
      items: [{ id: 1, title: 'Flags', flashcards: [{ question: 'Q', answer: 'A' }], editable: true, discussionsEnabled: false }],
      nextCursor: null,
    });
    vi.mocked(api.setDeckDiscussionsEnabled).mockResolvedValue({
      id: 1,
      title: 'Flags',
      flashcards: [{ question: 'Q', answer: 'A' }],
      editable: true,
      discussionsEnabled: true,
    });
    renderPage();

    const toggle = await screen.findByRole('switch', { name: /Discussions for Flags/ });
    expect(toggle).not.toBeChecked();
    // Practice is not offered on the management screen, even for a deck with cards.
    expect(screen.queryByRole('button', { name: 'Practice' })).not.toBeInTheDocument();

    await userEvent.click(toggle);
    await waitFor(() => expect(api.setDeckDiscussionsEnabled).toHaveBeenCalledWith(1, true));
    expect(toggle).toBeChecked();
  });

  it('hides the discussions switch from an admin without manage_discussions', async () => {
    authState.canManageGlobal = true;
    authState.canManageDiscussions = false;
    vi.mocked(api.getGlobalDecks).mockResolvedValue({
      items: [{ id: 1, title: 'Flags', flashcards: [], editable: true }],
      nextCursor: null,
    });
    renderPage();
    await screen.findByText('Flags');
    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
  });
});
