import { createContext, useContext } from 'react';
import type { AuthResponse } from '../api/types';

export interface AuthContextValue {
  token: string | null;
  /** The user's effective feature permissions (empty until loaded / for a signed-out user). */
  permissions: string[];
  /**
   * False only while the initial `GET /auth/me` permission hydration is in flight on a cold load
   * with a stored token; true otherwise (signed-out, or once permissions are known). Route guards
   * wait on this so an admin isn't redirected before their permissions arrive (FLA-136).
   */
  permissionsReady: boolean;
  /** Whether the user holds a given permission (e.g. `can('manage_global_decks')`). */
  can: (permission: string) => boolean;
  /** The caller's resolved feature flags (FLA-174), keyed by flag key. */
  flags: Record<string, boolean>;
  /** Whether a feature flag is enabled for the caller (false when unknown/absent). */
  isEnabled: (key: string) => boolean;
  /** The current user's public display name (null when unset → falls back to the email local-part). */
  displayName: string | null;
  /** The current user's avatar URL (null when unset → the UI renders an initials monogram). FLA-162. */
  avatarUrl: string | null;
  /**
   * Update the in-memory profile after a settings change (display name / avatar) so the header and
   * any other consumers reflect it immediately, without a reload.
   */
  setProfile: (profile: { displayName: string | null; avatarUrl: string | null }) => void;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  googleSignIn: (idToken: string) => Promise<void>;
  /**
   * Apply an already-obtained auth response (tokens + permissions) to the in-memory auth state.
   * Used by the guest "save my session" flow, which registers and persists a session *before*
   * flipping auth state so the route tree only swaps once the save is durable.
   */
  applyAuth: (auth: AuthResponse) => void;
  signOut: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

// Kept in this non-component module (separate from AuthProvider) so the provider file
// only exports a component — required for React Fast Refresh / the eslint rule.
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
