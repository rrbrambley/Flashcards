import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { FlashcardDeckDto } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { CreateDeckForm } from './CreateDeckForm';

export function HomePage() {
  const { signOut } = useAuth();
  const [decks, setDecks] = useState<FlashcardDeckDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadDecks = useCallback(async () => {
    setLoading(true);
    try {
      setDecks(await api.getDecks());
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load your decks.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDecks();
  }, [loadDecks]);

  return (
    <div className="app">
      <header className="app-header">
        <h1>Flashcards</h1>
        <button className="link-btn" onClick={signOut}>
          Sign out
        </button>
      </header>

      <main className="layout">
        <section className="panel">
          <h2>Your decks</h2>
          {loading ? (
            <p className="muted">Loading…</p>
          ) : error ? (
            <p className="error">{error}</p>
          ) : decks.length === 0 ? (
            <p className="muted">No decks yet — create one on the right.</p>
          ) : (
            <ul className="deck-list">
              {decks.map((deck) => (
                <li key={deck.id}>
                  <span className="deck-title">{deck.title}</span>
                  <span className="muted">
                    {deck.flashcards.length} card{deck.flashcards.length === 1 ? '' : 's'}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="panel">
          <h2>Create a deck</h2>
          <CreateDeckForm onCreated={loadDecks} />
        </section>
      </main>
    </div>
  );
}
