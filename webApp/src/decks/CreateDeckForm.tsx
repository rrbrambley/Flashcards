import { useState, type FormEvent } from 'react';
import { api } from '../api/client';

interface CardDraft {
  id: number;
  term: string;
  definition: string;
}

const MINIMUM_COMPLETE_CARDS = 1;

export function CreateDeckForm({ onCreated }: { onCreated: () => void }) {
  const [title, setTitle] = useState('');
  const [cards, setCards] = useState<CardDraft[]>([{ id: 1, term: '', definition: '' }]);
  const [nextId, setNextId] = useState(2);
  const [showErrors, setShowErrors] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<string | null>(null);

  const updateCard = (id: number, patch: Partial<CardDraft>) => {
    setCards((current) => current.map((card) => (card.id === id ? { ...card, ...patch } : card)));
    setShowErrors(false);
    setCreated(null);
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
    setCreated(null);
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
      const deck = await api.createDeck({
        title: title.trim(),
        // term -> question, definition -> answer (same mapping as Android).
        flashcards: completeCards.map((c) => ({ question: c.term.trim(), answer: c.definition.trim() })),
      });
      setTitle('');
      setCards([{ id: nextId, term: '', definition: '' }]);
      setNextId((n) => n + 1);
      setShowErrors(false);
      setCreated(deck.title);
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the deck.');
    } finally {
      setSubmitting(false);
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
            setCreated(null);
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
      {created && <p className="success">Created “{created}” ✓</p>}

      <button type="submit" disabled={submitting}>
        {submitting ? 'Creating…' : 'Create deck'}
      </button>
    </form>
  );
}
