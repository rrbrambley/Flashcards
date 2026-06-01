import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { FlashcardDeckDto } from '../api/types';
import { useAuth } from '../auth/auth-context';

export function LibraryPage() {
  const { signOut } = useAuth();
  const navigate = useNavigate();
  const [decks, setDecks] = useState<FlashcardDeckDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadDecks = useCallback(async () => {
    // `loading` starts true; all state writes happen after the await so the mount
    // effect doesn't set state synchronously.
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
    // Legitimate fetch-on-mount: loadDecks only writes state after its await, but this
    // lint rule is conservative about effects that call a state-setting function.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadDecks();
  }, [loadDecks]);

  return (
    <div className="app">
      <header className="app-header">
        <h1>Library</h1>
        <button className="link-btn" onClick={signOut}>
          Sign out
        </button>
      </header>

      <main className="container">
        <div className="library-actions">
          <button onClick={() => navigate('/create')}>+ Create deck</button>
        </div>

        {loading ? (
          <p className="muted">Loading…</p>
        ) : error ? (
          <p className="error">{error}</p>
        ) : decks.length === 0 ? (
          <p className="muted">No decks yet — create your first one.</p>
        ) : (
          <ul className="deck-list">
            {decks.map((deck) => (
              <li key={deck.id}>
                <button className="deck-row" onClick={() => navigate(`/decks/${deck.id}/edit`)}>
                  <span className="deck-title">{deck.title}</span>
                  <span className="muted">
                    {deck.flashcards.length} card{deck.flashcards.length === 1 ? '' : 's'} ›
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  );
}
