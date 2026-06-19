import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DeckForm } from './DeckForm';
import { api } from '../api/client';

vi.mock('../api/client', () => ({ api: { uploadImage: vi.fn() } }));

describe('DeckForm', () => {
  it('rejects an oversized image client-side without calling the server', async () => {
    const { container } = render(<DeckForm submitLabel="Create deck" onSubmit={vi.fn()} />);
    const input = container.querySelector('input[type="file"]') as HTMLInputElement;
    const big = new File(['x'], 'big.png', { type: 'image/png' });
    Object.defineProperty(big, 'size', { value: 6 * 1024 * 1024 });

    await userEvent.upload(input, big);

    expect(api.uploadImage).not.toHaveBeenCalled();
    expect(screen.getByText(/under 5 MB/i)).toBeInTheDocument();
  });

  it('shows a per-card error when the upload fails', async () => {
    vi.mocked(api.uploadImage).mockRejectedValue(new Error('Image rejected'));
    const { container } = render(<DeckForm submitLabel="Create deck" onSubmit={vi.fn()} />);
    const input = container.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['x'], 'img.png', { type: 'image/png' });

    await userEvent.upload(input, file);

    expect(await screen.findByText('Image rejected')).toBeInTheDocument();
  });

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
      expect(onSubmit).toHaveBeenCalledWith(
        'Spanish',
        [{ question: 'Hola', answer: 'Hello', imageUrl: null, alternativeAnswers: [] }],
        [],
      ),
    );
  });

  it('collects per-card alternative answers (one per line, trimmed, deduped)', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <DeckForm
        submitLabel="Save changes"
        initialTitle="Capitals"
        initialCards={[{ term: 'New York', definition: 'NYC', alternativeAnswers: ['New York City'] }]}
        onSubmit={onSubmit}
      />,
    );

    // Seeded alternatives are shown one per line; add another (with blank/dup noise that gets cleaned).
    const alternatives = screen.getByLabelText(/Other accepted answers/);
    expect(alternatives).toHaveValue('New York City');
    await userEvent.clear(alternatives);
    await userEvent.type(alternatives, 'NYC\n  The Big Apple  \n\nNYC');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith(
        'Capitals',
        [{ question: 'New York', answer: 'NYC', imageUrl: null, alternativeAnswers: ['NYC', 'The Big Apple'] }],
        [],
      ),
    );
  });

  it('round-trips the category as a single tag, seeded from initialCategory', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <DeckForm
        submitLabel="Save changes"
        initialTitle="Capitals"
        initialCategory="Geography"
        initialCards={[{ term: 'France', definition: 'Paris' }]}
        onSubmit={onSubmit}
      />,
    );

    // The category field is pre-filled from initialCategory; edit it, then save.
    const categoryField = screen.getByLabelText('Category (optional)');
    expect(categoryField).toHaveValue('Geography');
    await userEvent.clear(categoryField);
    await userEvent.type(categoryField, 'History');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith(
        'Capitals',
        [{ question: 'France', answer: 'Paris', imageUrl: null, alternativeAnswers: [] }],
        ['History'],
      ),
    );
  });

  it('sends an empty tag list when the category is left blank', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<DeckForm submitLabel="Create deck" onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText('Deck title'), 'Spanish');
    await userEvent.type(screen.getByLabelText('Term'), 'Hola');
    await userEvent.type(screen.getByLabelText('Definition'), 'Hello');
    await userEvent.click(screen.getByRole('button', { name: 'Create deck' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('Spanish', expect.anything(), []));
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
