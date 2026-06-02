import { useState, type ReactNode } from 'react';
import { api } from '../api/client';
import { clearToken, getToken, setToken } from './token';
import { AuthContext, type AuthContextValue } from './auth-context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(getToken());

  const persist = (value: string) => {
    setToken(value);
    setTokenState(value);
  };

  const value: AuthContextValue = {
    token,
    login: async (email, password) => persist((await api.login(email, password)).token),
    register: async (email, password) => persist((await api.register(email, password)).token),
    googleSignIn: async (idToken) => persist((await api.googleSignIn(idToken)).token),
    signOut: () => {
      // Best-effort server-side revoke (request() reads the token before we clear it);
      // the local token is always cleared so logout works even if the call fails.
      void api.logout().catch(() => {});
      clearToken();
      setTokenState(null);
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
