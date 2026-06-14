import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { CatalogPage } from './CatalogPage';
import { api } from '../api/client';
import type { FlashcardDeckDto } from '../api/types';

vi.mock('../api/client', () => ({ api: { getCatalog: vi.fn() } }));

const decks: FlashcardDeckDto[] = [
  { id: 1, title: 'Flags of the World', editable: false, flashcards: [{ question: '', answer: 'France' }], tags: ['Geography'] },
  { id: 2, title: 'World Currencies', editable: false, flashcards: [{ question: 'Japan', answer: 'Yen' }] },
];

function renderCatalog() {
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<CatalogPage />} />
        <Route path="/login" element={<div>login page</div>} />
        <Route path="/decks/:id/practice" element={<div>practice-page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('CatalogPage (guest landing)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lists the public catalog decks with sign-in CTAs', async () => {
    vi.mocked(api.getCatalog).mockResolvedValue({ items: decks, nextCursor: null });
    renderCatalog();

    expect(await screen.findByText('Flags of the World')).toBeInTheDocument();
    expect(screen.getByText('World Currencies')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Log in' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Create account' })).toBeInTheDocument();
    // Uses the unauthenticated catalog endpoint, never the authed deck list.
    expect(api.getCatalog).toHaveBeenCalled();
  });

  it('starts practice when a deck is tapped', async () => {
    vi.mocked(api.getCatalog).mockResolvedValue({ items: decks, nextCursor: null });
    renderCatalog();

    await userEvent.click(await screen.findByText('Flags of the World'));

    expect(await screen.findByText('practice-page')).toBeInTheDocument();
  });

  it('shows an error when the catalog fails to load', async () => {
    vi.mocked(api.getCatalog).mockRejectedValue(new Error('offline'));
    renderCatalog();

    expect(await screen.findByText('offline')).toBeInTheDocument();
  });
});
