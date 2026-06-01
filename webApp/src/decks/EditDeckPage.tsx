import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import type { FlashcardDeckDto } from '../api/types';
import { BackHeader } from './BackHeader';
import { DeckForm } from './DeckForm';

export function EditDeckPage() {
  const { id } = useParams();
  const deckId = Number(id);
  const navigate = useNavigate();
  const [deck, setDeck] = useState<FlashcardDeckDto | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    api
      .getDeck(deckId)
      .then((d) => {
        if (active) setDeck(d);
      })
      .catch((err) => {
        if (active) setLoadError(err instanceof Error ? err.message : 'Could not load the deck.');
      });
    return () => {
      active = false;
    };
  }, [deckId]);

  return (
    <div className="app">
      <BackHeader title="Edit deck" />
      <main className="container">
        {loadError ? (
          <p className="error">{loadError}</p>
        ) : !deck ? (
          <p className="muted">Loading…</p>
        ) : (
          <DeckForm
            submitLabel="Save changes"
            initialTitle={deck.title}
            initialCards={deck.flashcards.map((f) => ({ term: f.question, definition: f.answer }))}
            onSubmit={async (title, flashcards) => {
              try {
                await api.updateDeck(deckId, { title, flashcards });
              } catch (err) {
                // The seeded global deck isn't owned by the user, so the server rejects edits.
                if (err instanceof ApiError && err.status === 404) {
                  throw new Error("This deck can't be edited.");
                }
                throw err;
              }
              navigate('/');
            }}
          />
        )}
      </main>
    </div>
  );
}
