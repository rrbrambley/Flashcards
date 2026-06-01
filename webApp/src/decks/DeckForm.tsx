import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { FlashcardDto } from '../api/types';

interface CardDraft {
  id: number;
  term: string;
  definition: string;
  imageUrl: string | null;
  uploading: boolean;
}

const MINIMUM_COMPLETE_CARDS = 1;
const ACCEPT = 'image/png,image/jpeg,image/webp,image/gif';

interface InitialCard {
  term: string;
  definition: string;
  imageUrl?: string | null;
}

interface DeckFormProps {
  submitLabel: string;
  initialTitle?: string;
  initialCards?: InitialCard[];
  /** Read-only decks (e.g. the global catalog) are shown but can't be edited. */
  readOnly?: boolean;
  /** Receives validated values (term -> question, definition -> answer). Throw to surface an error. */
  onSubmit: (title: string, flashcards: FlashcardDto[]) => Promise<void>;
}

// A card needs a definition plus either a term or an image (image-only cards are allowed).
const isComplete = (c: CardDraft) => c.definition.trim() !== '' && (c.term.trim() !== '' || c.imageUrl != null);
const isStarted = (c: CardDraft) => c.term.trim() !== '' || c.definition.trim() !== '' || c.imageUrl != null;

export function DeckForm({ submitLabel, initialTitle = '', initialCards, readOnly = false, onSubmit }: DeckFormProps) {
  const seededCards: CardDraft[] = (initialCards && initialCards.length > 0
    ? initialCards
    : [{ term: '', definition: '' }]
  ).map((card, index) => ({
    id: index + 1,
    term: card.term,
    definition: card.definition,
    imageUrl: card.imageUrl ?? null,
    uploading: false,
  }));

  const [title, setTitle] = useState(initialTitle);
  const [cards, setCards] = useState<CardDraft[]>(seededCards);
  const [nextId, setNextId] = useState(seededCards.length + 1);
  const [showErrors, setShowErrors] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const updateCard = (id: number, patch: Partial<CardDraft>) => {
    setCards((current) => current.map((card) => (card.id === id ? { ...card, ...patch } : card)));
    setShowErrors(false);
  };

  const addCard = () => {
    setCards((current) => [...current, { id: nextId, term: '', definition: '', imageUrl: null, uploading: false }]);
    setNextId((n) => n + 1);
  };

  const handleImage = async (id: number, file: File) => {
    updateCard(id, { uploading: true });
    try {
      const { url } = await api.uploadImage(file);
      updateCard(id, { imageUrl: url, uploading: false });
    } catch (err) {
      updateCard(id, { uploading: false });
      setError(err instanceof Error ? err.message : 'Image upload failed.');
    }
  };

  const completeCards = cards.filter(isComplete);
  const hasIncompleteStartedCard = cards.some((c) => isStarted(c) && !isComplete(c));
  const titleError = showErrors && !title.trim();
  const cardCountError = showErrors && completeCards.length < MINIMUM_COMPLETE_CARDS;
  const incompleteError = showErrors && hasIncompleteStartedCard;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const valid =
      title.trim().length > 0 &&
      completeCards.length >= MINIMUM_COMPLETE_CARDS &&
      !hasIncompleteStartedCard;
    if (!valid) {
      setShowErrors(true);
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      // term -> question, definition -> answer (same mapping as Android).
      await onSubmit(
        title.trim(),
        completeCards.map((c) => ({
          question: c.term.trim(),
          answer: c.definition.trim(),
          imageUrl: c.imageUrl,
        })),
      );
    } catch (err) {
      setSubmitting(false);
      setError(err instanceof Error ? err.message : 'Could not save the deck.');
    }
  };

  return (
    <form className="create-form" onSubmit={submit}>
      {readOnly && <p className="muted read-only-note">This deck is read-only and can't be edited.</p>}
      <label>
        Deck title
        <input
          type="text"
          value={title}
          disabled={readOnly}
          onChange={(e) => {
            setTitle(e.target.value);
            setShowErrors(false);
          }}
          aria-invalid={titleError}
        />
        {titleError && <span className="field-error">Enter a deck title</span>}
      </label>

      {cards.map((card, index) => {
        const started = isStarted(card);
        const termError = showErrors && started && !card.term.trim() && card.imageUrl == null;
        const definitionError = showErrors && started && !card.definition.trim();
        return (
          <fieldset className="card-draft" key={card.id}>
            <legend>Card {index + 1}</legend>

            {(card.imageUrl || !readOnly) && (
              <div className="card-image">
                {card.imageUrl ? (
                  <div className="thumb-wrap">
                    <img src={card.imageUrl} alt="card" className="card-thumb" />
                    {!readOnly && (
                      <button type="button" className="link-btn" onClick={() => updateCard(card.id, { imageUrl: null })}>
                        Remove image
                      </button>
                    )}
                  </div>
                ) : card.uploading ? (
                  <span className="muted">Uploading…</span>
                ) : (
                  <label className="image-button">
                    🖼 Add image
                    <input
                      type="file"
                      accept={ACCEPT}
                      hidden
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        e.target.value = '';
                        if (file) handleImage(card.id, file);
                      }}
                    />
                  </label>
                )}
              </div>
            )}

            <label>
              {card.imageUrl ? 'Term (optional)' : 'Term'}
              <input
                type="text"
                value={card.term}
                disabled={readOnly}
                onChange={(e) => updateCard(card.id, { term: e.target.value })}
                aria-invalid={termError}
              />
              {termError && <span className="field-error">Add a term or an image</span>}
            </label>
            <label>
              Definition
              <textarea
                value={card.definition}
                rows={2}
                disabled={readOnly}
                onChange={(e) => updateCard(card.id, { definition: e.target.value })}
                aria-invalid={definitionError}
              />
              {definitionError && <span className="field-error">Enter a definition</span>}
            </label>
          </fieldset>
        );
      })}

      {!readOnly && (
        <button type="button" className="secondary" onClick={addCard}>
          + Add card
        </button>
      )}

      {cardCountError && <p className="error">Add at least one card with a definition and a term or image.</p>}
      {incompleteError && <p className="error">Finish each started card: a definition plus a term or image.</p>}
      {error && <p className="error">{error}</p>}

      {!readOnly && (
        <button type="submit" disabled={submitting}>
          {submitting ? 'Saving…' : submitLabel}
        </button>
      )}
    </form>
  );
}
