import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/auth-context';
import { DeckLibrary } from './DeckLibrary';

export function LibraryPage() {
  const { signOut, can } = useAuth();
  const navigate = useNavigate();

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

      <DeckLibrary
        fetchPage={(cursor) => api.getDecks(cursor ? { cursor } : {})}
        emptyMessage="No decks yet — create your first one."
        actions={
          <>
            <button onClick={() => navigate('/create')}>+ Create deck</button>
            {can('manage_global_decks') && (
              <button className="secondary" onClick={() => navigate('/library/global')}>
                Manage global decks
              </button>
            )}
          </>
        }
      />
    </div>
  );
}
