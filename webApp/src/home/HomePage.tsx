import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { HomeButtonAction, HomeData } from '../api/types';
import { useAuth } from '../auth/auth-context';

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
          // Resume the session by its deck; createSession on that deck resumes the active one.
          const session = await api.getSession(action.sessionId);
          navigate(`/decks/${session.deckId}/practice`);
          break;
        }
        case 'navigate_to_practice': {
          // Practice the global catalog deck (Flags of the World), mirroring Android.
          const decks = await api.getAllDecks();
          const target = decks.find((d) => d.title === 'Flags of the World') ?? decks[0];
          if (!target) {
            setActionError('There are no decks to practice yet.');
            return;
          }
          navigate(`/decks/${target.id}/practice`);
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
