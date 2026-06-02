import { clearToken, getRefreshToken, getToken, setTokens } from '../auth/token';
import type {
  AuthResponse,
  CreateDeckRequest,
  ErrorResponse,
  FlashcardDeckDto,
  ImageUploadResponse,
  PracticeSessionDto,
  UpdateProgressRequest,
} from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = 'ApiError';
  }
}

// Registered by the AuthProvider so a terminal 401 (refresh failed/absent) can drop the in-memory
// auth state and route the user back to sign-in. Kept as a module hook to avoid a React dependency.
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

// Single-flight refresh: concurrent 401s share one /auth/refresh call rather than stampeding it.
let refreshInFlight: Promise<boolean> | null = null;

async function refreshAccessToken(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;
  refreshInFlight = (async () => {
    try {
      const response = await fetch(`${BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!response.ok) return false;
      const data = (await response.json()) as AuthResponse;
      setTokens(data.accessToken, data.refreshToken);
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = {};
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.auth) {
    const token = getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  // Access token expired/invalid: transparently refresh once and retry the original request.
  if (options.auth && response.status === 401 && !retried) {
    if (await refreshAccessToken()) {
      return request<T>(path, options, true);
    }
    clearToken();
    onUnauthorized?.();
  }

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const error = (await response.json()) as ErrorResponse;
      if (error?.message) message = error.message;
    } catch {
      // non-JSON error body; keep the default message
    }
    throw new ApiError(response.status, message);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

// Multipart upload — let the browser set the Content-Type (with the boundary).
async function uploadImage(file: File, retried = false): Promise<ImageUploadResponse> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const form = new FormData();
  form.append('file', file);
  const response = await fetch(`${BASE_URL}/images`, { method: 'POST', headers, body: form });

  if (response.status === 401 && !retried) {
    if (await refreshAccessToken()) {
      return uploadImage(file, true);
    }
    clearToken();
    onUnauthorized?.();
  }

  if (!response.ok) {
    let message = `Upload failed (${response.status})`;
    try {
      const error = (await response.json()) as ErrorResponse;
      if (error?.message) message = error.message;
    } catch {
      // keep default
    }
    throw new ApiError(response.status, message);
  }
  return (await response.json()) as ImageUploadResponse;
}

export const api = {
  register: (email: string, password: string) =>
    request<AuthResponse>('/auth/register', { method: 'POST', body: { email, password } }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/auth/login', { method: 'POST', body: { email, password } }),
  googleSignIn: (idToken: string) =>
    request<AuthResponse>('/auth/google', { method: 'POST', body: { idToken } }),
  // Revokes the refresh token server-side; needs the access bearer (auth: true) too.
  logout: (refreshToken: string) =>
    request<void>('/auth/logout', { method: 'POST', body: { refreshToken }, auth: true }),
  getDecks: () => request<FlashcardDeckDto[]>('/decks', { auth: true }),
  getDeck: (id: number) => request<FlashcardDeckDto>(`/decks/${id}`, { auth: true }),
  createDeck: (deck: CreateDeckRequest) =>
    request<FlashcardDeckDto>('/decks', { method: 'POST', body: deck, auth: true }),
  updateDeck: (id: number, deck: CreateDeckRequest) =>
    request<FlashcardDeckDto>(`/decks/${id}`, { method: 'PUT', body: deck, auth: true }),
  deleteDeck: (id: number) => request<void>(`/decks/${id}`, { method: 'DELETE', auth: true }),

  // Practice sessions
  createSession: (deckId: number) =>
    request<PracticeSessionDto>('/sessions', { method: 'POST', body: { deckId }, auth: true }),
  getSession: (id: number) => request<PracticeSessionDto>(`/sessions/${id}`, { auth: true }),
  updateProgress: (id: number, progress: UpdateProgressRequest) =>
    request<PracticeSessionDto>(`/sessions/${id}`, { method: 'PATCH', body: progress, auth: true }),
  completeSession: (id: number) =>
    request<PracticeSessionDto>(`/sessions/${id}/complete`, { method: 'POST', auth: true }),

  uploadImage: (file: File) => uploadImage(file),
};
