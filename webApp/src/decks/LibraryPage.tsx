import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { FlashcardDeckDto } from '../api/types';
import { useAuth } from '../auth/auth-context';

export function LibraryPage() {
  const { signOut } = useAuth();
  const navigate = useNavigate();
  const [decks, setDecks] = useState<FlashcardDeckDto[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  // Client-side title filter over the decks loaded so far (case-insensitive).
  const normalizedSearch = search.trim().toLowerCase();
  const filteredDecks = normalizedSearch
    ? decks.filter((deck) => deck.title.toLowerCase().includes(normalizedSearch))
    : decks;

  // Fetches one page. `reset` replaces the list (initial load); otherwise appends (load more).
  const loadPage = useCallback(async (cursorToken: string | undefined, reset: boolean) => {
    try {
      const page = await api.getDecks(cursorToken ? { cursor: cursorToken } : {});
      setDecks((prev) => (reset ? page.items : [...prev, ...page.items]));
      setCursor(page.nextCursor);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load your decks.');
    }
  }, []);

  useEffect(() => {
    // Legitimate fetch-on-mount: loadPage only writes state after its await, but this
    // lint rule is conservative about effects that call a state-setting function.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadPage(undefined, true).finally(() => setLoading(false));
  }, [loadPage]);

  const loadMore = async () => {
    if (!cursor) return;
    setLoadingMore(true);
    await loadPage(cursor, false);
    setLoadingMore(false);
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>Library</h1>
        <nav className="app-header-nav">
          <Link to="/" className="link-btn">
            Home
          </Link>
          <button className="link-btn" onClick={signOut}>
            Sign out
          </button>
        </nav>
      </header>

      <main className="container">
        <div className="library-actions">
          <button onClick={() => navigate('/create')}>+ Create deck</button>
        </div>

        {!loading && !error && (decks.length > 0 || search) && (
          <input
            className="deck-search"
            type="search"
            placeholder="Search decks"
            aria-label="Search decks"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        )}

        {loading ? (
          <p className="muted">Loading…</p>
        ) : error ? (
          <p className="error">{error}</p>
        ) : decks.length === 0 ? (
          <p className="muted">No decks yet — create your first one.</p>
        ) : filteredDecks.length === 0 ? (
          <p className="muted">No decks match “{search}”.</p>
        ) : (
          <ul className="deck-list">
            {filteredDecks.map((deck) => (
              <li key={deck.id} className="deck-row">
                <button className="deck-row-main" onClick={() => navigate(`/decks/${deck.id}/edit`)}>
                  <span className="deck-title">{deck.title}</span>
                  <span className="muted">
                    {deck.flashcards.length} card{deck.flashcards.length === 1 ? '' : 's'}
                  </span>
                </button>
                {deck.flashcards.length > 0 && (
                  <button className="secondary deck-practice" onClick={() => navigate(`/decks/${deck.id}/practice`)}>
                    Practice
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}

        {!loading && !error && cursor && (
          <div className="library-actions">
            <button className="secondary" onClick={loadMore} disabled={loadingMore}>
              {loadingMore ? 'Loading…' : 'Load more'}
            </button>
          </div>
        )}
      </main>
    </div>
  );
}
