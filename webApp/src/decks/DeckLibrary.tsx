import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { FlashcardDeckDto, Page } from '../api/types';

/**
 * The shared deck-library body: a paginated, searchable, sortable deck list. Used by both the
 * personal Library and the admin "global decks" view — they differ only in their data source
 * ([fetchPage]), the action buttons above the list ([actions]), and the empty-state copy.
 */
export function DeckLibrary({
  fetchPage,
  actions,
  emptyMessage,
  renderDeckControls,
  showPractice = true,
}: {
  fetchPage: (cursor?: string) => Promise<Page<FlashcardDeckDto>>;
  actions: ReactNode;
  emptyMessage: string;
  /** Optional extra control per deck row (e.g. the admin discussions toggle on the global list). */
  renderDeckControls?: (deck: FlashcardDeckDto) => ReactNode;
  /** Whether to show the per-deck Practice shortcut. False on the admin management view. */
  showPractice?: boolean;
}) {
  const navigate = useNavigate();
  // Remember which list we're on so Edit can send the user back here (personal vs. global).
  const origin = useLocation().pathname;
  const [decks, setDecks] = useState<FlashcardDeckDto[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [sortOrder, setSortOrder] = useState<'alpha' | 'recent'>('alpha');
  // deckId -> most recent practice time; loaded lazily the first time "Recently practiced" is picked.
  const [lastPracticed, setLastPracticed] = useState<Record<number, number>>({});
  const [sessionsLoaded, setSessionsLoaded] = useState(false);
  // The deck whose actions sheet is open (#380). Clicking a row used to navigate straight to Edit —
  // the biggest target on the row going somewhere people rarely want — so it now offers the choice,
  // matching what Android and iOS already do on a deck tap.
  const [actionsFor, setActionsFor] = useState<FlashcardDeckDto | null>(null);
  // Delete is confirmed *inside* the same sheet rather than by stacking a second overlay on the
  // first (#382) — the actions swap out for the confirmation and back again on Cancel.
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const closeSheet = useCallback(() => {
    setActionsFor(null);
    setConfirmingDelete(false);
    setDeleting(false);
    setDeleteError(null);
  }, []);

  const handleDelete = useCallback(async () => {
    if (actionsFor == null) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await api.deleteDeck(actionsFor.id);
      // Drop it locally rather than refetching: the list is paginated, and a refetch would reset
      // whatever the user has already loaded via "Load more".
      setDecks((prev) => prev.filter((d) => d.id !== actionsFor.id));
      closeSheet();
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : 'Could not delete the deck.');
      setDeleting(false);
    }
  }, [actionsFor, closeSheet]);

  // "Recently practiced" needs per-deck session times; fetch them once, only when first selected.
  useEffect(() => {
    if (sortOrder !== 'recent' || sessionsLoaded) return;
    let cancelled = false;
    api
      .getAllSessions()
      .then((sessions) => {
        if (cancelled) return;
        const map: Record<number, number> = {};
        for (const s of sessions) map[s.deckId] = Math.max(map[s.deckId] ?? 0, s.updatedAtMillis);
        setLastPracticed(map);
      })
      .catch(() => {
        /* leave the map empty; recency sort falls back to deck order */
      })
      .finally(() => !cancelled && setSessionsLoaded(true));
    return () => {
      cancelled = true;
    };
  }, [sortOrder, sessionsLoaded]);

  // Client-side filter over the decks loaded so far (case-insensitive); matches the title or any tag.
  const normalizedSearch = search.trim().toLowerCase();
  const filteredDecks = normalizedSearch
    ? decks.filter(
        (deck) =>
          deck.title.toLowerCase().includes(normalizedSearch) ||
          (deck.tags ?? []).some((tag) => tag.toLowerCase().includes(normalizedSearch)),
      )
    : decks;

  const sortedDecks = [...filteredDecks].sort((a, b) =>
    sortOrder === 'alpha'
      ? a.title.localeCompare(b.title, undefined, { sensitivity: 'base' })
      : (lastPracticed[b.id] ?? 0) - (lastPracticed[a.id] ?? 0) || b.id - a.id,
  );

  // Fetches one page. `reset` replaces the list (initial load); otherwise appends (load more).
  const loadPage = useCallback(
    async (cursorToken: string | undefined, reset: boolean) => {
      try {
        const page = await fetchPage(cursorToken);
        setDecks((prev) => (reset ? page.items : [...prev, ...page.items]));
        setCursor(page.nextCursor);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not load your decks.');
      }
    },
    [fetchPage],
  );

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
    <main className="container">
      <div className="library-actions">{actions}</div>

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
          <select
            className="deck-sort"
            aria-label="Sort decks"
            value={sortOrder}
            onChange={(e) => setSortOrder(e.target.value as 'alpha' | 'recent')}
          >
            <option value="alpha">A–Z</option>
            <option value="recent">Recently practiced</option>
          </select>
        </div>
      )}

      {loading ? (
        <p className="muted">Loading…</p>
      ) : error ? (
        <p className="error">{error}</p>
      ) : decks.length === 0 ? (
        <p className="muted">{emptyMessage}</p>
      ) : sortedDecks.length === 0 ? (
        <p className="muted">No decks match “{search}”.</p>
      ) : (
        <ul className="deck-list">
          {sortedDecks.map((deck) => (
            <li key={deck.id} className="deck-row">
              <button className="deck-row-main" onClick={() => setActionsFor(deck)}>
                <span className="deck-row-text">
                  <span className="deck-title">{deck.title}</span>
                  {deck.tags?.[0] && <span className="deck-category">{deck.tags[0]}</span>}
                </span>
                <span className="muted">
                  {deck.flashcards.length} card{deck.flashcards.length === 1 ? '' : 's'}
                </span>
              </button>
              {showPractice && deck.flashcards.length > 0 && (
                <button
                  className="secondary deck-practice"
                  onClick={() => navigate(`/decks/${deck.id}/practice`, { state: { from: origin } })}
                >
                  Practice
                </button>
              )}
              {renderDeckControls?.(deck)}
            </li>
          ))}
        </ul>
      )}

      {actionsFor && (
        <div
          className="modal-overlay"
          role="dialog"
          aria-modal="true"
          aria-label={confirmingDelete ? `Delete ${actionsFor.title}?` : `Actions for ${actionsFor.title}`}
          onClick={deleting ? undefined : closeSheet}
        >
          <div className="modal-card deck-actions" onClick={(e) => e.stopPropagation()}>
            {confirmingDelete ? (
              <>
                <h2>Delete “{actionsFor.title}”?</h2>
                <p className="muted">
                  Its {actionsFor.flashcards.length} card{actionsFor.flashcards.length === 1 ? '' : 's'} and
                  practice history will be gone. This can't be undone.
                </p>
                {deleteError && <p className="error">{deleteError}</p>}
                <button className="danger" onClick={handleDelete} disabled={deleting}>
                  {deleting ? 'Deleting…' : 'Delete deck'}
                </button>
                <button className="link-btn" onClick={() => setConfirmingDelete(false)} disabled={deleting}>
                  Cancel
                </button>
              </>
            ) : (
              <>
                <h2>{actionsFor.title}</h2>
                {showPractice && actionsFor.flashcards.length > 0 && (
                  <button
                    onClick={() => navigate(`/decks/${actionsFor.id}/practice`, { state: { from: origin } })}
                  >
                    Practice
                  </button>
                )}
                {/* A global catalog deck is read-only for anyone without manage_global_decks, so don't
                    offer Edit or Delete there — Edit used to land on a form nothing could be changed
                    on (#380), and the backend refuses the delete anyway. Same gate as Android's sheet. */}
                {actionsFor.editable !== false && (
                  <>
                    <button
                      className="secondary"
                      onClick={() => navigate(`/decks/${actionsFor.id}/edit`, { state: { from: origin } })}
                    >
                      Edit
                    </button>
                    <button className="secondary danger-text" onClick={() => setConfirmingDelete(true)}>
                      Delete
                    </button>
                  </>
                )}
                <button className="link-btn" onClick={closeSheet}>
                  Cancel
                </button>
              </>
            )}
          </div>
        </div>
      )}

      {!loading && !error && cursor && (
        <div className="library-actions">
          <button className="secondary" onClick={loadMore} disabled={loadingMore}>
            {loadingMore ? 'Loading…' : 'Load more'}
          </button>
        </div>
      )}
    </main>
  );
}
