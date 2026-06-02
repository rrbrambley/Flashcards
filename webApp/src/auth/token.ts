const ACCESS_TOKEN_KEY = 'flashcards_token';
const REFRESH_TOKEN_KEY = 'flashcards_refresh_token';

/** The short-lived JWT access token, sent as the bearer on API requests. */
export const getToken = (): string | null => localStorage.getItem(ACCESS_TOKEN_KEY);

/** The opaque, long-lived refresh token, exchanged at /auth/refresh and revoked on logout. */
export const getRefreshToken = (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY);

/** Stores the access token only (used when a refresh yields a fresh access token). */
export const setToken = (token: string): void => localStorage.setItem(ACCESS_TOKEN_KEY, token);

/** Stores both tokens (login/register/google). */
export const setTokens = (accessToken: string, refreshToken: string): void => {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
};

/** Clears both tokens (logout, or a terminal 401). */
export const clearToken = (): void => {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
};
