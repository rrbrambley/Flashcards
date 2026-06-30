import { useEffect, useState, type ReactNode } from 'react';
import { api, setUnauthorizedHandler } from '../api/client';
import { clearToken, getRefreshToken, getToken, setTokens } from './token';
import { AuthContext, type AuthContextValue } from './auth-context';
import type { AuthResponse } from '../api/types';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(getToken());
  const [permissions, setPermissions] = useState<string[]>([]);
  // The current user's profile (FLA-162) for the header avatar/menu; null until hydrated or when
  // signed out. Auth responses don't carry it, so login/cold-load fetch it from /auth/me.
  const [displayName, setDisplayNameState] = useState<string | null>(null);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  // Only a cold load with a stored token needs async hydration; otherwise permissions are known
  // immediately (signed-out, or seeded inline by login/register/google). (FLA-136)
  const [permissionsReady, setPermissionsReady] = useState<boolean>(() => getToken() === null);

  const setProfile = (profile: { displayName: string | null; avatarUrl: string | null }) => {
    setDisplayNameState(profile.displayName);
    setAvatarUrl(profile.avatarUrl);
  };

  // login/register/google return the permissions inline, so we set them without a round-trip; the
  // profile (display name + avatar) isn't in the auth response, so fetch it in the background.
  const persist = (auth: AuthResponse) => {
    setTokens(auth.accessToken, auth.refreshToken);
    setTokenState(auth.accessToken);
    setPermissions(auth.permissions ?? []);
    setPermissionsReady(true);
    void api
      .getMe()
      .then((me) => setProfile({ displayName: me.displayName ?? null, avatarUrl: me.avatarUrl ?? null }))
      .catch(() => {
        /* header just falls back to a monogram until the next load */
      });
  };

  // A terminal 401 (refresh failed/absent) clears auth state so route guards send the user to login.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearToken();
      setTokenState(null);
      setPermissions([]);
      setProfile({ displayName: null, avatarUrl: null });
      setPermissionsReady(true); // signed out — nothing left to hydrate
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
        if (!active) return;
        setPermissions(me.permissions);
        setProfile({ displayName: me.displayName ?? null, avatarUrl: me.avatarUrl ?? null });
      })
      .catch(() => {
        /* ignore — a 401 is handled by the unauthorized handler; otherwise stay un-permissioned */
      })
      .finally(() => {
        if (active) setPermissionsReady(true);
      });
    return () => {
      active = false;
    };
  }, []);

  const value: AuthContextValue = {
    token,
    permissions,
    permissionsReady,
    can: (permission) => permissions.includes(permission),
    displayName,
    avatarUrl,
    setProfile,
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
      setProfile({ displayName: null, avatarUrl: null });
      setPermissionsReady(true); // signed out — nothing left to hydrate
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
