import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DeckSettings } from './DeckSettings';
import { api } from '../api/client';
import type { FlashcardDeckDto } from '../api/types';

vi.mock('../api/client', () => ({
  api: { setDeckGlobal: vi.fn(), setDeckDiscussionsEnabled: vi.fn() },
}));

const deck = (over: Partial<FlashcardDeckDto> = {}): FlashcardDeckDto => ({
  id: 7,
  title: 'Capitals',
  editable: true,
  flashcards: [],
  isGlobal: false,
  discussionsEnabled: false,
  ...over,
});

describe('DeckSettings', () => {
  beforeEach(() => vi.clearAllMocks());

  it('toggles Global and reflects the server response', async () => {
    vi.mocked(api.setDeckGlobal).mockResolvedValue(deck({ isGlobal: true }));
    render(<DeckSettings deck={deck()} canManageGlobal canManageDiscussions />);

    const globalSwitch = screen.getByRole('switch', { name: 'Global deck' });
    expect(globalSwitch).not.toBeChecked();

    await userEvent.click(globalSwitch);

    expect(api.setDeckGlobal).toHaveBeenCalledWith(7, true);
    await waitFor(() => expect(globalSwitch).toBeChecked());
  });

  it('disables the Discussions toggle until the deck is global', async () => {
    vi.mocked(api.setDeckGlobal).mockResolvedValue(deck({ isGlobal: true }));
    render(<DeckSettings deck={deck()} canManageGlobal canManageDiscussions />);

    const discussions = screen.getByRole('switch', { name: 'Card discussions' });
    expect(discussions).toBeDisabled();

    // Flipping Global on enables the subsection.
    await userEvent.click(screen.getByRole('switch', { name: 'Global deck' }));
    await waitFor(() => expect(discussions).toBeEnabled());
  });

  it('toggles Discussions once global', async () => {
    vi.mocked(api.setDeckDiscussionsEnabled).mockResolvedValue(deck({ isGlobal: true, discussionsEnabled: true }));
    render(<DeckSettings deck={deck({ isGlobal: true })} canManageGlobal canManageDiscussions />);

    const discussions = screen.getByRole('switch', { name: 'Card discussions' });
    expect(discussions).toBeEnabled();

    await userEvent.click(discussions);

    expect(api.setDeckDiscussionsEnabled).toHaveBeenCalledWith(7, true);
    await waitFor(() => expect(discussions).toBeChecked());
  });

  it('shows only the toggles the user is permissioned for', () => {
    render(<DeckSettings deck={deck({ isGlobal: true })} canManageGlobal={false} canManageDiscussions />);

    expect(screen.queryByRole('switch', { name: 'Global deck' })).toBeNull();
    expect(screen.getByRole('switch', { name: 'Card discussions' })).toBeInTheDocument();
  });
});
