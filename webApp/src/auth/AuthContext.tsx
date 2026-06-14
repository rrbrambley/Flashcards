import { useEffect, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler } from '../api/client';
import { clearToken, getRefreshToken, getToken, setTokens } from './token';
import { AuthContext, type AuthContextValue } from './auth-context';
import type { AuthResponse } from '../api/types';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(getToken());
  const [permissions, setPermissions] = useState<string[]>([]);

  // login/register/google return the permissions inline, so we set them without a round-trip.
  const persist = (auth: AuthResponse) => {
    setTokens(auth.accessToken, auth.refreshToken);
    setTokenState(auth.accessToken);
    setPermissions(auth.permissions ?? []);
  };

  // A terminal 401 (refresh failed/absent) clears auth state so route guards send the user to login.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearToken();
      setTokenState(null);
      setPermissions([]);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  // On a fresh page load with a stored token (no inline permissions to seed from), fetch /me once.
  useEffect(() => {
    if (!getToken()) return;
    let active = true;
    api
      .getMe()
      .then((me) => {
        if (active) setPermissions(me.permissions);
      })
      .catch(() => {
        /* ignore — a 401 is handled by the unauthorized handler; otherwise stay un-permissioned */
      });
    return () => {
      active = false;
    };
  }, []);

  const value: AuthContextValue = {
    token,
    permissions,
    can: (permission) => permissions.includes(permission),
    login: async (email, password) => persist(await api.login(email, password)),
    register: async (email, password) => persist(await api.register(email, password)),
    googleSignIn: async (idToken) => persist(await api.googleSignIn(idToken)),
    applyAuth: persist,
    signOut: () => {
      // Best-effort server-side revoke of the refresh token; local tokens are always cleared so
      // logout works even if the call fails.
      const refreshToken = getRefreshToken();
      if (refreshToken) void api.logout(refreshToken).catch(() => {});
      clearToken();
      setTokenState(null);
      setPermissions([]);
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
