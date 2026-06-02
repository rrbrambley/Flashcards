import { useEffect, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler } from '../api/client';
import { clearToken, getRefreshToken, getToken, setTokens } from './token';
import { AuthContext, type AuthContextValue } from './auth-context';
import type { AuthResponse } from '../api/types';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(getToken());

  const persist = (auth: AuthResponse) => {
    setTokens(auth.accessToken, auth.refreshToken);
    setTokenState(auth.accessToken);
  };

  // A terminal 401 (refresh failed/absent) clears auth state so route guards send the user to login.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearToken();
      setTokenState(null);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  const value: AuthContextValue = {
    token,
    login: async (email, password) => persist(await api.login(email, password)),
    register: async (email, password) => persist(await api.register(email, password)),
    googleSignIn: async (idToken) => persist(await api.googleSignIn(idToken)),
    signOut: () => {
      // Best-effort server-side revoke of the refresh token; local tokens are always cleared so
      // logout works even if the call fails.
      const refreshToken = getRefreshToken();
      if (refreshToken) void api.logout(refreshToken).catch(() => {});
      clearToken();
      setTokenState(null);
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
