import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { HomeButtonAction, HomeData, HomeSessionInfo } from '../api/types';
import { useAuth } from '../auth/auth-context';
import { Avatar } from '../components/Avatar';
import { findMode } from '../practice/modes';

export function HomePage() {
  const { signOut, displayName, avatarUrl } = useAuth();
  const navigate = useNavigate();
  const [items, setItems] = useState<HomeData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  // The in-progress session the user is confirming removal of (FLA-205), or null.
  const [confirmRemove, setConfirmRemove] = useState<{ sessionId: number; title: string } | null>(null);
  const [removing, setRemoving] = useState(false);
  // Overall practice streak (FLA-106); null until loaded, 0 when there is no active streak.
  const [streak, setStreak] = useState<number | null>(null);

  const loadHome = useCallback(async () => {
    try {
      setItems(await api.getHome());
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load your home feed.');
    } finally {
      setLoading(false);
    }
    // Best-effort: a streak failure must never block the feed, so fetch it separately.
    try {
      const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
      setStreak((await api.getStreaks(tz)).overall.current);
    } catch {
      // leave the badge hidden
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadHome();
  }, [loadHome]);

  const runAction = async (action: HomeButtonAction) => {
    setActionError(null);
    try {
      switch (action.type) {
        case 'create_new_flashcard_set':
          navigate('/create');
          break;
        case 'continue_practice': {
          // Resume the session in its own mode; createSession on (deck, mode) resumes the active one.
          const session = await api.getSession(action.sessionId);
          // Pass the origin so practice "back" returns here to Home (FLA-168).
          navigate(`/decks/${session.deckId}/practice?mode=${session.mode}`, { state: { from: '/' } });
          break;
        }
        case 'navigate_to_practice': {
          // The backend resolves which deck (the featured global deck) and sends its id.
          navigate(`/decks/${action.deckId}/practice`, { state: { from: '/' } });
          break;
        }
      }
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
    }
  };

  // Discards the confirmed in-progress session (FLA-205): drop the card optimistically, then delete.
  const handleRemove = async () => {
    if (!confirmRemove) return;
    const { sessionId } = confirmRemove;
    setRemoving(true);
    setActionError(null);
    try {
      await api.deleteSession(sessionId);
      setItems((prev) => prev.filter((it) => continueSessionId(it) !== sessionId));
      setConfirmRemove(null);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Could not remove the session. Please try again.');
      setConfirmRemove(null);
      loadHome(); // resync so the card reflects the true state
    } finally {
      setRemoving(false);
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>Flashcards</h1>
        <nav className="app-header-nav">
          {streak != null && streak > 0 && (
            <span className="streak-badge" title="Days in a row with a completed practice">
              🔥 {streak} day streak
            </span>
          )}
          <Link to="/library" className="link-btn">
            Library
          </Link>
          <button className="link-btn" onClick={signOut}>
            Sign out
          </button>
          <Link to="/settings" className="avatar-link" title="Settings" aria-label="Settings">
            <Avatar url={avatarUrl} name={displayName} size={32} />
          </Link>
        </nav>
      </header>

      <main className="container">
        {actionError && <p className="error">{actionError}</p>}
        {loading ? (
          <p className="muted">Loading…</p>
        ) : error ? (
          <p className="error">{error}</p>
        ) : (
          groupBySection(items).map((group, groupIndex) => (
            <section key={groupIndex} className="home-section">
              {group.section && <h2 className="home-section-header">{group.section}</h2>}
              <ul className="home-list">
                {group.items.map((item, index) => {
                  const sessionId = continueSessionId(item);
                  return (
                    <li key={index} className="home-card">
                      {sessionId != null && (
                        <button
                          className="home-card-remove"
                          aria-label="Remove practice session"
                          title="Remove"
                          onClick={() => setConfirmRemove({ sessionId, title: item.title })}
                        >
                          ×
                        </button>
                      )}
                      <span className="home-card-title">{item.title}</span>
                      {item.session && <SessionDetail session={item.session} />}
                      {item.button && (
                        <button onClick={() => runAction(item.button!.action)}>{item.button.message}</button>
                      )}
                    </li>
                  );
                })}
              </ul>
            </section>
          ))
        )}
      </main>

      {confirmRemove && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Remove practice session">
          <div className="modal-card">
            <h2>Remove practice session?</h2>
            <p className="muted">Your progress on “{confirmRemove.title}” will be lost.</p>
            <div className="modal-actions">
              <button onClick={handleRemove} disabled={removing}>
                {removing ? '…' : 'Remove'}
              </button>
              <button className="link-btn" onClick={() => setConfirmRemove(null)} disabled={removing}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/** The session id of an in-progress "continue practice" card (FLA-205), or null for other cards. */
function continueSessionId(item: HomeData): number | null {
  return item.button?.action.type === 'continue_practice' ? item.button.action.sessionId : null;
}

/**
 * Group consecutive feed items by their `section` header (FLA-96), preserving feed order so each
 * section renders under one header. Items without a section form their own header-less group.
 */
function groupBySection(items: HomeData[]): { section: string | null; items: HomeData[] }[] {
  const groups: { section: string | null; items: HomeData[] }[] = [];
  for (const item of items) {
    const section = item.section ?? null;
    const last = groups[groups.length - 1];
    if (last && last.section === section) last.items.push(item);
    else groups.push({ section, items: [item] });
  }
  return groups;
}

/** Mode + score + a progress bar for an in-progress session, shown on its "continue" home card. */
function SessionDetail({ session }: { session: HomeSessionInfo }) {
  const modeLabel = findMode(session.mode)?.label ?? session.mode;
  const { totalCards, currentCardIndex, numCorrect, numIncorrect } = session;
  const progressPct = totalCards > 0 ? Math.round((currentCardIndex / totalCards) * 100) : 0;

  return (
    <div className="home-card-session">
      <div className="home-card-session-meta">
        <span className="badge">{modeLabel}</span>
        <span className="session-correct">✓ {numCorrect}</span>
        <span className="session-incorrect">✗ {numIncorrect}</span>
        {totalCards > 0 && (
          <span className="muted home-card-session-progress">
            {Math.min(currentCardIndex + 1, totalCards)} of {totalCards}
          </span>
        )}
      </div>
      {totalCards > 0 && (
        <div
          className="progress"
          role="progressbar"
          aria-label="Practice progress"
          aria-valuenow={progressPct}
          aria-valuemin={0}
          aria-valuemax={100}
        >
          <div className="progress-bar" style={{ width: `${progressPct}%` }} />
        </div>
      )}
    </div>
  );
}
