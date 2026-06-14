import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { FlashcardDeckDto } from '../api/types';
import { ShareButton } from '../practice/ShareButton';

/**
 * The guest landing page (FLA-101): the public global-deck catalog. Anyone — signed in or not — can
 * browse these read-only decks and start practicing; a header CTA invites creating an account. Uses
 * the unauthenticated `/catalog` endpoints, so it never triggers a sign-in redirect.
 */
export function CatalogPage() {
  const navigate = useNavigate();
  const [decks, setDecks] = useState<FlashcardDeckDto[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  const loadPage = useCallback(async (cursorToken: string | undefined, reset: boolean) => {
    try {
      const page = await api.getCatalog(cursorToken ? { cursor: cursorToken } : {});
      setDecks((prev) => (reset ? page.items : [...prev, ...page.items]));
      setCursor(page.nextCursor);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load the deck catalog.');
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadPage(undefined, true).finally(() => setLoading(false));
  }, [loadPage]);

  const loadMore = async () => {
    if (!cursor) return;
    setLoadingMore(true);
    await loadPage(cursor, false);
    setLoadingMore(false);
  };

  const normalizedSearch = search.trim().toLowerCase();
  const visibleDecks = normalizedSearch
    ? decks.filter(
        (deck) =>
          deck.title.toLowerCase().includes(normalizedSearch) ||
          (deck.tags ?? []).some((tag) => tag.toLowerCase().includes(normalizedSearch)),
      )
    : decks;

  return (
    <div className="app">
      <header className="app-header">
        <h1>Flashcards</h1>
        <nav className="app-header-nav">
          <Link to="/login" className="link-btn">
            Log in
          </Link>
          <Link to="/register" className="link-btn">
            Create account
          </Link>
        </nav>
      </header>

      <main className="container">
        <p className="muted catalog-intro">Pick a deck and start studying — no account needed.</p>

        {!loading && !error && (decks.length > 0 || search) && (
          <div className="library-controls">
            <input
              className="deck-search"
              type="search"
              placeholder="Search decks"
              aria-label="Search decks"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        )}

        {loading ? (
          <p className="muted">Loading…</p>
        ) : error ? (
          <p className="error">{error}</p>
        ) : decks.length === 0 ? (
          <p className="muted">No decks are available yet.</p>
        ) : visibleDecks.length === 0 ? (
          <p className="muted">No decks match “{search}”.</p>
        ) : (
          <ul className="deck-list">
            {visibleDecks.map((deck) => (
              <li key={deck.id} className="deck-row">
                <button className="deck-row-main" onClick={() => navigate(`/decks/${deck.id}/practice`)}>
                  <span className="deck-row-text">
                    <span className="deck-title">{deck.title}</span>
                    {deck.tags?.[0] && <span className="deck-category">{deck.tags[0]}</span>}
                  </span>
                  <span className="muted">
                    {deck.flashcards.length} card{deck.flashcards.length === 1 ? '' : 's'}
                  </span>
                </button>
                <ShareButton deckId={deck.id} title={deck.title} className="secondary deck-practice" />
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
