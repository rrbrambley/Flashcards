import { createContext, useContext } from 'react';

export interface AuthContextValue {
  token: string | null;
  /** The user's effective feature permissions (empty until loaded / for a signed-out user). */
  permissions: string[];
  /** Whether the user holds a given permission (e.g. `can('manage_global_decks')`). */
  can: (permission: string) => boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  googleSignIn: (idToken: string) => Promise<void>;
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
