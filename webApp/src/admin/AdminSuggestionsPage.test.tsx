import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AdminSuggestionsPage } from './AdminSuggestionsPage';
import { api } from '../api/client';
import type { AnswerSuggestion } from '../api/types';

const authState = vi.hoisted(() => ({ canManageSuggestions: false }));

vi.mock('../api/client', () => ({
  api: { getAnswerSuggestions: vi.fn(), acceptAnswerSuggestion: vi.fn(), dismissAnswerSuggestion: vi.fn() },
}));
vi.mock('../auth/auth-context', () => ({
  useAuth: () => ({ signOut: vi.fn(), can: (p: string) => p === 'manage_suggestions' && authState.canManageSuggestions }),
}));

const suggestion = (over: Partial<AnswerSuggestion> = {}): AnswerSuggestion => ({
  id: 1,
  cardUid: 'c1',
  suggestedAnswer: 'Paris, France',
  deckId: 5,
  deckTitle: 'World Capitals',
  question: 'Capital of France?',
  currentAnswer: 'Paris',
  suggesterDisplayName: 'Quiz Whiz',
  createdAtMillis: 1000,
  ...over,
});

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/admin/suggestions']}>
      <Routes>
        <Route path="/admin/suggestions" element={<AdminSuggestionsPage />} />
        <Route path="/library" element={<div>Personal library</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AdminSuggestionsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authState.canManageSuggestions = false;
  });

  it('redirects non-admins to the library', () => {
    renderPage();
    expect(screen.getByText('Personal library')).toBeInTheDocument();
  });

  it('lists open suggestions with the card context', async () => {
    authState.canManageSuggestions = true;
    vi.mocked(api.getAnswerSuggestions).mockResolvedValue({ items: [suggestion()], nextCursor: null });
    renderPage();

    expect(await screen.findByText('Paris, France')).toBeInTheDocument();
    expect(screen.getByText(/World Capitals/)).toBeInTheDocument();
    expect(screen.getByText('Capital of France?')).toBeInTheDocument();
  });

  it('accepts a suggestion and removes the row', async () => {
    authState.canManageSuggestions = true;
    vi.mocked(api.getAnswerSuggestions).mockResolvedValue({ items: [suggestion()], nextCursor: null });
    vi.mocked(api.acceptAnswerSuggestion).mockResolvedValue(undefined);
    renderPage();
    const row = within((await screen.findByText('Paris, France')).closest('li') as HTMLElement);

    await userEvent.click(row.getByRole('button', { name: 'Accept' }));

    await waitFor(() => expect(api.acceptAnswerSuggestion).toHaveBeenCalledWith(1));
    await waitFor(() => expect(screen.queryByText('Paris, France')).not.toBeInTheDocument());
  });

  it('dismisses a suggestion and removes the row', async () => {
    authState.canManageSuggestions = true;
    vi.mocked(api.getAnswerSuggestions).mockResolvedValue({ items: [suggestion()], nextCursor: null });
    vi.mocked(api.dismissAnswerSuggestion).mockResolvedValue(undefined);
    renderPage();
    const row = within((await screen.findByText('Paris, France')).closest('li') as HTMLElement);

    await userEvent.click(row.getByRole('button', { name: 'Dismiss' }));

    await waitFor(() => expect(api.dismissAnswerSuggestion).toHaveBeenCalledWith(1));
    await waitFor(() => expect(screen.queryByText('Paris, France')).not.toBeInTheDocument());
  });
});
