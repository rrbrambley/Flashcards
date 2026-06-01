import { useState, type FormEvent } from 'react';
import type { FlashcardDto } from '../api/types';

interface CardDraft {
  id: number;
  term: string;
  definition: string;
}

const MINIMUM_COMPLETE_CARDS = 1;

interface DeckFormProps {
  submitLabel: string;
  initialTitle?: string;
  initialCards?: { term: string; definition: string }[];
  /** Receives validated values (term -> question, definition -> answer). Throw to surface an error. */
  onSubmit: (title: string, flashcards: FlashcardDto[]) => Promise<void>;
}

export function DeckForm({ submitLabel, initialTitle = '', initialCards, onSubmit }: DeckFormProps) {
  const seededCards: CardDraft[] = (initialCards && initialCards.length > 0
    ? initialCards
    : [{ term: '', definition: '' }]
  ).map((card, index) => ({ id: index + 1, term: card.term, definition: card.definition }));

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
    setCards((current) => [...current, { id: nextId, term: '', definition: '' }]);
    setNextId((n) => n + 1);
  };

  // Validation mirrors the Android CreateDeckViewModel.
  const completeCards = cards.filter((c) => c.term.trim() && c.definition.trim());
  const hasIncompleteStartedCard = cards.some(
    (c) => (c.term.trim() && !c.definition.trim()) || (!c.term.trim() && c.definition.trim()),
  );
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
        completeCards.map((c) => ({ question: c.term.trim(), answer: c.definition.trim() })),
      );
      // On success the page navigates away; keep the button disabled until then.
    } catch (err) {
      setSubmitting(false);
      setError(err instanceof Error ? err.message : 'Could not save the deck.');
    }
  };

  return (
    <form className="create-form" onSubmit={submit}>
      <label>
        Deck title
        <input
          type="text"
          value={title}
          onChange={(e) => {
            setTitle(e.target.value);
            setShowErrors(false);
          }}
          aria-invalid={titleError}
        />
        {titleError && <span className="field-error">Enter a deck title</span>}
      </label>

      {cards.map((card, index) => {
        const started = card.term.trim() !== '' || card.definition.trim() !== '';
        const termError = showErrors && started && !card.term.trim();
        const definitionError = showErrors && started && !card.definition.trim();
        return (
          <fieldset className="card-draft" key={card.id}>
            <legend>Card {index + 1}</legend>
            <label>
              Term
              <input
                type="text"
                value={card.term}
                onChange={(e) => updateCard(card.id, { term: e.target.value })}
                aria-invalid={termError}
              />
              {termError && <span className="field-error">Enter a term</span>}
            </label>
            <label>
              Definition
              <textarea
                value={card.definition}
                rows={2}
                onChange={(e) => updateCard(card.id, { definition: e.target.value })}
                aria-invalid={definitionError}
              />
              {definitionError && <span className="field-error">Enter a definition</span>}
            </label>
          </fieldset>
        );
      })}

      <button type="button" className="secondary" onClick={addCard}>
        + Add card
      </button>

      {cardCountError && <p className="error">Add at least one complete card (term and definition).</p>}
      {incompleteError && <p className="error">Finish or clear the started card(s).</p>}
      {error && <p className="error">{error}</p>}

      <button type="submit" disabled={submitting}>
        {submitting ? 'Saving…' : submitLabel}
      </button>
    </form>
  );
}
