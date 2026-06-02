import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DeckForm } from './DeckForm';

vi.mock('../api/client', () => ({ api: { uploadImage: vi.fn() } }));

describe('DeckForm', () => {
  it('blocks an invalid submit and shows validation errors', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<DeckForm submitLabel="Create deck" onSubmit={onSubmit} />);

    await userEvent.click(screen.getByRole('button', { name: 'Create deck' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByText('Enter a deck title')).toBeInTheDocument();
  });

  it('submits a valid card with term→question / definition→answer mapping', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<DeckForm submitLabel="Create deck" onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText('Deck title'), 'Spanish');
    await userEvent.type(screen.getByLabelText('Term'), 'Hola');
    await userEvent.type(screen.getByLabelText('Definition'), 'Hello');
    await userEvent.click(screen.getByRole('button', { name: 'Create deck' }));

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith('Spanish', [{ question: 'Hola', answer: 'Hello', imageUrl: null }]),
    );
  });

  it('removes an individual card, and hides the remove control when only one remains', async () => {
    render(
      <DeckForm
        submitLabel="Create deck"
        onSubmit={vi.fn()}
        initialCards={[
          { term: 'Hola', definition: 'Hello' },
          { term: 'Gracias', definition: 'Thanks' },
        ]}
      />,
    );

    // Two cards → each has a remove control.
    expect(screen.getByDisplayValue('Hola')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Remove card 1'));

    // The first card is gone; with one card left the remove control disappears.
    expect(screen.queryByDisplayValue('Hola')).toBeNull();
    expect(screen.getByDisplayValue('Gracias')).toBeInTheDocument();
    expect(screen.queryByLabelText(/Remove card/)).toBeNull();
  });

  it('read-only mode shows the note, disables the title, and hides submit', () => {
    render(
      <DeckForm
        submitLabel="Save changes"
        readOnly
        initialTitle="Flags"
        initialCards={[{ term: '', definition: 'Canada', imageUrl: 'https://cdn/flag.png' }]}
        onSubmit={vi.fn()}
      />,
    );

    expect(screen.getByText("This deck is read-only and can't be edited.")).toBeInTheDocument();
    expect(screen.getByLabelText('Deck title')).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Save changes' })).toBeNull();
  });
});
