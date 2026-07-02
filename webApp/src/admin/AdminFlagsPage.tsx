import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api } from '../api/client';
import type { AdminFlagDto, RoleDto } from '../api/types';
import { useAuth } from '../auth/auth-context';
import { ToggleSwitch } from '../decks/ToggleSwitch';

/**
 * Admin feature-flag management (FLA-176): toggle each flag's global state and set/clear per-user
 * (by email) and per-role overrides. Gated on `manage_feature_flags` — non-admins are bounced to
 * their library; the API enforces the same gate server-side. Effective value = user override → role
 * override → global default.
 */
export function AdminFlagsPage() {
  const { signOut, can, permissionsReady } = useAuth();
  const isAdmin = can('manage_feature_flags');
  const [flags, setFlags] = useState<AdminFlagDto[]>([]);
  const [roles, setRoles] = useState<RoleDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  // Override keys with a request in flight, so their controls disable.
  const [pending, setPending] = useState<Set<string>>(new Set());
  // Per-flag "add user override" email input.
  const [emailInput, setEmailInput] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!isAdmin) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    Promise.all([api.getAdminFlags(), api.getRoles().catch(() => [] as RoleDto[])])
      .then(([flagList, roleList]) => {
        setFlags(flagList);
        setRoles(roleList);
        setError(null);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Could not load feature flags.'))
      .finally(() => setLoading(false));
  }, [isAdmin]);

  if (!permissionsReady) {
    return (
      <div className="app">
        <p className="muted">Loading…</p>
      </div>
    );
  }
  if (!isAdmin) {
    return <Navigate to="/library" replace />;
  }

  const replaceFlag = (updated: AdminFlagDto) =>
    setFlags((prev) => prev.map((f) => (f.key === updated.key ? updated : f)));

  // Runs an override mutation keyed by `busyKey` (disables its control) and swaps in the result.
  const run = async (busyKey: string, action: () => Promise<AdminFlagDto>) => {
    setPending((prev) => new Set(prev).add(busyKey));
    setActionError(null);
    try {
      replaceFlag(await action());
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Could not update the flag.');
    } finally {
      setPending((prev) => {
        const next = new Set(prev);
        next.delete(busyKey);
        return next;
      });
    }
  };

  const roleOverrideValue = (flag: AdminFlagDto, roleKey: string): boolean | null =>
    flag.roleOverrides.find((o) => o.roleKey === roleKey)?.enabled ?? null;

  // Sets a role override to on/off, or clears it (value === null → back to the global default).
  const setRole = (flag: AdminFlagDto, roleKey: string, value: boolean | null) =>
    run(`${flag.key}:role:${roleKey}`, () =>
      value === null
        ? api.clearFlagRoleOverride(flag.key, roleKey)
        : api.setFlagRoleOverride(flag.key, roleKey, value),
    );

  const addUserOverride = async (flag: AdminFlagDto, enabled: boolean) => {
    const email = (emailInput[flag.key] ?? '').trim();
    if (!email) return;
    const busyKey = `${flag.key}:adduser`;
    setPending((prev) => new Set(prev).add(busyKey));
    setActionError(null);
    try {
      // The override endpoint takes a user id; resolve the email via the admin user search.
      const match = (await api.getAdminUsers({ q: email })).items.find((u) => u.email === email);
      if (!match) {
        setActionError(`No user with email “${email}”.`);
        return;
      }
      replaceFlag(await api.setFlagUserOverride(flag.key, match.id, enabled));
      setEmailInput((prev) => ({ ...prev, [flag.key]: '' }));
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Could not add the override.');
    } finally {
      setPending((prev) => {
        const next = new Set(prev);
        next.delete(busyKey);
        return next;
      });
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>Feature flags</h1>
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
        ) : flags.length === 0 ? (
          <p className="muted">No feature flags.</p>
        ) : (
          <ul className="flag-list">
            {flags.map((flag) => (
              <li key={flag.key} className="flag-card">
                <div className="flag-card-head">
                  <div>
                    <span className="flag-key">{flag.key}</span>
                    <span className="field-hint">{flag.description}</span>
                  </div>
                  <ToggleSwitch
                    checked={flag.enabled}
                    disabled={pending.has(`${flag.key}:global`)}
                    ariaLabel={`${flag.key} global`}
                    onChange={() => run(`${flag.key}:global`, () => api.setFlagGlobal(flag.key, !flag.enabled))}
                  />
                </div>

                <div className="flag-overrides">
                  <span className="flag-overrides-label">Role overrides</span>
                  {roles.map((role) => {
                    const value = roleOverrideValue(flag, role.key);
                    const busy = pending.has(`${flag.key}:role:${role.key}`);
                    return (
                      <div key={role.key} className="flag-override-row">
                        <span className="flag-override-name">{role.key}</span>
                        <div className="flag-override-choices" role="group" aria-label={`${role.key} override`}>
                          <button
                            className={value === null ? 'chip active' : 'chip'}
                            disabled={busy}
                            onClick={() => setRole(flag, role.key, null)}
                          >
                            Default
                          </button>
                          <button
                            className={value === true ? 'chip active' : 'chip'}
                            disabled={busy}
                            onClick={() => setRole(flag, role.key, true)}
                          >
                            On
                          </button>
                          <button
                            className={value === false ? 'chip active' : 'chip'}
                            disabled={busy}
                            onClick={() => setRole(flag, role.key, false)}
                          >
                            Off
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>

                <div className="flag-overrides">
                  <span className="flag-overrides-label">User overrides</span>
                  {flag.userOverrides.length === 0 && <span className="field-hint">None.</span>}
                  {flag.userOverrides.map((override) => (
                    <div key={override.userId} className="flag-override-row">
                      <span className="flag-override-name">{override.email}</span>
                      <div className="flag-override-choices">
                        <span className="badge">{override.enabled ? 'On' : 'Off'}</span>
                        <button
                          className="chip"
                          disabled={pending.has(`${flag.key}:user:${override.userId}`)}
                          onClick={() =>
                            run(`${flag.key}:user:${override.userId}`, () =>
                              api.clearFlagUserOverride(flag.key, override.userId),
                            )
                          }
                        >
                          Remove
                        </button>
                      </div>
                    </div>
                  ))}
                  <div className="flag-override-add">
                    <input
                      type="email"
                      placeholder="user@example.com"
                      aria-label={`Add user override for ${flag.key}`}
                      value={emailInput[flag.key] ?? ''}
                      onChange={(e) => setEmailInput((prev) => ({ ...prev, [flag.key]: e.target.value }))}
                    />
                    <button
                      className="chip"
                      disabled={pending.has(`${flag.key}:adduser`)}
                      onClick={() => addUserOverride(flag, true)}
                    >
                      + On
                    </button>
                    <button
                      className="chip"
                      disabled={pending.has(`${flag.key}:adduser`)}
                      onClick={() => addUserOverride(flag, false)}
                    >
                      + Off
                    </button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  );
}
