import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { EditDeckPage } from './EditDeckPage';
import { ApiError, api } from '../api/client';
import type { FlashcardDeckDto } from '../api/types';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return { ...actual, api: { getDeck: vi.fn(), updateDeck: vi.fn() } };
});

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/decks/5/edit']}>
      <Routes>
        <Route path="/decks/:id/edit" element={<EditDeckPage />} />
        <Route path="/" element={<div>home</div>} />
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
  beforeEach(() => vi.clearAllMocks());

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

  it('maps a 404 on save to a friendly message', async () => {
    vi.mocked(api.getDeck).mockResolvedValue(deck());
    vi.mocked(api.updateDeck).mockRejectedValue(new ApiError(404, 'not found'));
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText("This deck can't be edited.")).toBeInTheDocument();
  });
});
