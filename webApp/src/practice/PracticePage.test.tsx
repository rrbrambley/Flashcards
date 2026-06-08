import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { PracticePage } from './PracticePage';
import { api } from '../api/client';
import type { FlashcardDto, PracticeSessionDto } from '../api/types';

vi.mock('../api/client', () => ({
  api: { createSession: vi.fn(), getDeck: vi.fn(), updateProgress: vi.fn(), completeSession: vi.fn() },
}));

const session = (over: Partial<PracticeSessionDto> = {}): PracticeSessionDto => ({
  id: 1,
  deckId: 5,
  deckTitle: 'Spanish',
  currentCardIndex: 0,
  numCorrect: 0,
  numIncorrect: 0,
  isCompleted: false,
  mode: 'flashcards',
  createdAtMillis: 0,
  updatedAtMillis: 0,
  ...over,
});

function setup(cards: FlashcardDto[], sessionOver: Partial<PracticeSessionDto> = {}) {
  vi.mocked(api.createSession).mockResolvedValue(session(sessionOver));
  vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', editable: true, flashcards: cards });
  vi.mocked(api.updateProgress).mockResolvedValue(session());
  vi.mocked(api.completeSession).mockResolvedValue(session({ isCompleted: true }));
  render(
    <MemoryRouter initialEntries={['/decks/5/practice']}>
      <Routes>
        <Route path="/decks/:id/practice" element={<PracticePage />} />
        <Route path="/" element={<div>library</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

const threeCards: FlashcardDto[] = [
  { question: 'Q1', answer: 'A1' },
  { question: 'Q2', answer: 'A2' },
  { question: 'Q3', answer: 'A3' },
];

describe('PracticePage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('starts a session in the default (classic) mode and shows the (resumed) current card', async () => {
    setup(threeCards, { currentCardIndex: 1 });
    expect(await screen.findByText('Q2')).toBeInTheDocument();
    expect(api.createSession).toHaveBeenCalledWith(5, 'flashcards');
  });

  it('clicking the card flips it', async () => {
    setup(threeCards);
    const card = await screen.findByRole('button', { name: 'Show answer' });
    await userEvent.click(card);
    expect(screen.getByRole('button', { name: 'Show question' })).toBeInTheDocument();
  });

  it('marking correct advances and persists progress', async () => {
    setup(threeCards);
    await screen.findByText('Q1');

    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));

    expect(await screen.findByText('Q2')).toBeInTheDocument();
    expect(api.updateProgress).toHaveBeenCalledWith(1, { currentCardIndex: 1, numCorrect: 1, numIncorrect: 0 });
  });

  it('the right arrow key marks correct', async () => {
    setup(threeCards);
    await screen.findByText('Q1');

    fireEvent.keyDown(document.body, { key: 'ArrowRight' });

    expect(await screen.findByText('Q2')).toBeInTheDocument();
  });

  it('marking the last card completes the session', async () => {
    setup([{ question: 'Only', answer: 'Card' }]);
    await screen.findByText('Only');

    await userEvent.click(screen.getByRole('button', { name: /Got it/ }));

    expect(await screen.findByText('Practice complete')).toBeInTheDocument();
    expect(api.completeSession).toHaveBeenCalledWith(1);
  });

  it('shows an error when the deck has no cards', async () => {
    setup([]);
    expect(await screen.findByText(/no cards to practice/i)).toBeInTheDocument();
  });

  it('shows an error when starting the session fails', async () => {
    vi.mocked(api.createSession).mockRejectedValue(new Error('offline'));
    vi.mocked(api.getDeck).mockResolvedValue({ id: 5, title: 'Spanish', flashcards: threeCards });
    render(
      <MemoryRouter initialEntries={['/decks/5/practice']}>
        <Routes>
          <Route path="/decks/:id/practice" element={<PracticePage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('offline')).toBeInTheDocument();
  });
});
