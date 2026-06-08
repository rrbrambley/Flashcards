import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api } from '../api/client';
import type { AdminUserDto, RoleDto } from '../api/types';
import { useAuth } from '../auth/auth-context';

/**
 * Admin RBAC management: a searchable, paginated list of users where an admin can grant/revoke roles
 * (drawn from the read-only code-defined catalog). Gated on `manage_roles` — non-admins are bounced
 * to their library; the API enforces the same gate server-side.
 */
export function AdminUsersPage() {
  const { signOut, can } = useAuth();
  const [users, setUsers] = useState<AdminUserDto[]>([]);
  const [roles, setRoles] = useState<RoleDto[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  // `${userId}:${roleKey}` entries with a grant/revoke request in flight (their buttons disable).
  const [pending, setPending] = useState<Set<string>>(new Set());

  const isAdmin = can('manage_roles');

  // Fetches one page of users for the committed `query`. `reset` replaces the list (new search /
  // initial load); otherwise appends (load more).
  const loadPage = useCallback(async (q: string, cursorToken: string | undefined, reset: boolean) => {
    try {
      const page = await api.getAdminUsers({ q: q || undefined, cursor: cursorToken });
      setUsers((prev) => (reset ? page.items : [...prev, ...page.items]));
      setCursor(page.nextCursor);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load users.');
    }
  }, []);

  useEffect(() => {
    if (!isAdmin) return;
    api
      .getRoles()
      .then(setRoles)
      .catch(() => {
        /* the catalog is a nicety; grant/revoke still works off whatever loaded */
      });
  }, [isAdmin]);

  useEffect(() => {
    if (!isAdmin) return;
    // Re-runs on a new committed `query`; replaces the list. loadPage only writes state after its
    // await, but this lint rule is conservative about effects that call a state-setting function.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadPage(query, undefined, true).finally(() => setLoading(false));
  }, [isAdmin, query, loadPage]);

  if (!isAdmin) {
    return <Navigate to="/library" replace />;
  }

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setQuery(searchInput.trim());
  };

  const loadMore = async () => {
    if (!cursor) return;
    setLoadingMore(true);
    await loadPage(query, cursor, false);
    setLoadingMore(false);
  };

  const toggleRole = async (user: AdminUserDto, roleKey: string, granted: boolean) => {
    const key = `${user.id}:${roleKey}`;
    setPending((prev) => new Set(prev).add(key));
    setActionError(null);
    try {
      const updated = granted ? await api.revokeRole(user.id, roleKey) : await api.grantRole(user.id, roleKey);
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Could not update the role.');
    } finally {
      setPending((prev) => {
        const next = new Set(prev);
        next.delete(key);
        return next;
      });
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>Users</h1>
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
        <form className="library-controls" onSubmit={submitSearch}>
          <input
            className="deck-search"
            type="search"
            placeholder="Search by email"
            aria-label="Search users"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
          <button type="submit">Search</button>
          {query && (
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setSearchInput('');
                setQuery('');
              }}
            >
              Clear
            </button>
          )}
        </form>

        {actionError && <p className="error">{actionError}</p>}

        {loading ? (
          <p className="muted">Loading…</p>
        ) : error ? (
          <p className="error">{error}</p>
        ) : users.length === 0 ? (
          <p className="muted">{query ? `No users match “${query}”.` : 'No users.'}</p>
        ) : (
          <ul className="user-list">
            {users.map((user) => (
              <li key={user.id} className="user-row">
                <span className="user-email">{user.email}</span>
                <div className="user-roles">
                  {roles.map((role) => {
                    const granted = user.roles.includes(role.key);
                    const key = `${user.id}:${role.key}`;
                    return (
                      <button
                        key={role.key}
                        className={granted ? 'role-chip granted' : 'role-chip'}
                        title={
                          role.permissions.length > 0
                            ? `${role.description} — grants: ${role.permissions.join(', ')}`
                            : role.description
                        }
                        disabled={pending.has(key)}
                        aria-pressed={granted}
                        onClick={() => toggleRole(user, role.key, granted)}
                      >
                        {role.key} {granted ? '✕' : '+'}
                      </button>
                    );
                  })}
                </div>
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
