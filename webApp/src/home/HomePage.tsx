import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { HomeButtonAction, HomeData, HomeSessionInfo } from '../api/types';
import { useAuth } from '../auth/auth-context';
import { findMode } from '../practice/modes';

export function HomePage() {
  const { signOut } = useAuth();
  const navigate = useNavigate();
  const [items, setItems] = useState<HomeData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const loadHome = useCallback(async () => {
    try {
      setItems(await api.getHome());
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load your home feed.');
    } finally {
      setLoading(false);
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
          navigate(`/decks/${session.deckId}/practice?mode=${session.mode}`);
          break;
        }
        case 'navigate_to_practice': {
          // The backend resolves which deck (the featured global deck) and sends its id.
          navigate(`/decks/${action.deckId}/practice`);
          break;
        }
      }
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>Flashcards</h1>
        <nav className="app-header-nav">
          <Link to="/library" className="link-btn">
            Library
          </Link>
          <button className="link-btn" onClick={signOut}>
            Sign out
          </button>
        </nav>
      </header>

      <main className="container">
        {actionError && <p className="error">{actionError}</p>}
        {loading ? (
          <p className="muted">Loading…</p>
        ) : error ? (
          <p className="error">{error}</p>
        ) : (
          <ul className="home-list">
            {items.map((item, index) => (
              <li key={index} className="home-card">
                <span className="home-card-title">{item.title}</span>
                {item.session && <SessionDetail session={item.session} />}
                {item.button && (
                  <button onClick={() => runAction(item.button!.action)}>{item.button.message}</button>
                )}
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  );
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
