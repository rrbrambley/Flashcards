import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/auth-context';
import { DeckLibrary } from './DeckLibrary';

export function LibraryPage() {
  const { signOut, can } = useAuth();
  const navigate = useNavigate();
  const canManageGlobal = can('manage_global_decks');
  const canManageRoles = can('manage_roles');
  const canManageDiscussions = can('manage_discussions');
  const isAdmin = canManageGlobal || canManageRoles || canManageDiscussions;

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
          <div className="library-actions-group">
            {/* Admin controls grouped in their own panel, set apart from the regular controls. */}
            {isAdmin && (
              <section className="admin-panel" aria-label="Admin">
                <h2 className="admin-panel-title">Admin</h2>
                <div className="admin-panel-buttons">
                  {canManageGlobal && (
                    <button className="secondary" onClick={() => navigate('/library/global')}>
                      Manage global decks
                    </button>
                  )}
                  {canManageRoles && (
                    <button className="secondary" onClick={() => navigate('/admin/users')}>
                      Manage users
                    </button>
                  )}
                  {canManageDiscussions && (
                    <button className="secondary" onClick={() => navigate('/admin/discussions')}>
                      Discussion reports
                    </button>
                  )}
                </div>
              </section>
            )}
            <button onClick={() => navigate('/create')}>+ Create deck</button>
          </div>
        }
      />
    </div>
  );
}
