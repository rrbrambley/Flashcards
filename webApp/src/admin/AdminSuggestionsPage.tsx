import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api } from '../api/client';
import type { AnswerSuggestion } from '../api/types';
import { useAuth } from '../auth/auth-context';

/**
 * The answer-suggestion review queue (FLA-130): open "this should be correct" suggestions, newest
 * first, each with the card in context. A moderator can accept (appends the answer to the card's
 * alternatives) or dismiss. Gated on `manage_suggestions` — non-admins are bounced; the API enforces
 * the same gate.
 */
export function AdminSuggestionsPage() {
  const { signOut, can, permissionsReady } = useAuth();
  const [suggestions, setSuggestions] = useState<AnswerSuggestion[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<Set<number>>(new Set());

  const loadPage = useCallback(async (cursorToken: string | undefined, reset: boolean) => {
    try {
      const page = await api.getAnswerSuggestions(cursorToken ? { cursor: cursorToken } : {});
      setSuggestions((prev) => (reset ? page.items : [...prev, ...page.items]));
      setCursor(page.nextCursor);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load suggestions.');
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadPage(undefined, true).finally(() => setLoading(false));
  }, [loadPage]);

  // Wait for permissions to hydrate on a cold load before deciding to redirect (FLA-136).
  if (!permissionsReady) return <div className="app"><p className="muted">Loading…</p></div>;
  const isAdmin = can('manage_suggestions');
  if (!isAdmin) return <Navigate to="/library" replace />;

  const loadMore = async () => {
    if (!cursor) return;
    setLoadingMore(true);
    await loadPage(cursor, false);
    setLoadingMore(false);
  };

  const withPending = async (id: number, action: () => Promise<void>) => {
    setPending((s) => new Set(s).add(id));
    try {
      await action();
      setSuggestions((prev) => prev.filter((s) => s.id !== id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Action failed. Please try again.');
    } finally {
      setPending((s) => {
        const next = new Set(s);
        next.delete(id);
        return next;
      });
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>Answer suggestions</h1>
        <nav className="app-header-nav">
          <Link to="/library" className="link-btn">
            Library
          </Link>
          <button className="link-btn" onClick={signOut}>
            Sign out
          </button>
        </nav>
      </header>

      {error && <p className="error">{error}</p>}
      {loading ? (
        <p className="muted">Loading…</p>
      ) : suggestions.length === 0 ? (
        <p className="muted">No open suggestions. 🎉</p>
      ) : (
        <ul className="report-list">
          {suggestions.map((s) => (
            <li key={s.id} className="report-card">
              <p className="report-meta muted">
                {s.deckTitle} · suggested by {s.suggesterDisplayName} · {new Date(s.createdAtMillis).toLocaleString()}
              </p>
              <div className="report-message">
                <p className="discussion-content">
                  <span className="muted">Q:</span> {s.question}
                </p>
                <p className="discussion-content">
                  <span className="muted">Current answer:</span> {s.currentAnswer}
                </p>
                <p className="discussion-content">
                  <span className="muted">Suggested:</span> <strong>{s.suggestedAnswer}</strong>
                </p>
              </div>
              <div className="report-actions">
                <button
                  disabled={pending.has(s.id)}
                  onClick={() => withPending(s.id, () => api.acceptAnswerSuggestion(s.id))}
                >
                  Accept
                </button>
                <button
                  className="secondary"
                  disabled={pending.has(s.id)}
                  onClick={() => withPending(s.id, () => api.dismissAnswerSuggestion(s.id))}
                >
                  Dismiss
                </button>
              </div>
            </li>
          ))}
          {cursor && (
            <li>
              <button className="secondary" onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? 'Loading…' : 'Load more'}
              </button>
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
