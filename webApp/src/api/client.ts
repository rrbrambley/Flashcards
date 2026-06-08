import { clearToken, getRefreshToken, getToken, setTokens } from '../auth/token';
import type {
  AdminUserDto,
  AuthResponse,
  CreateDeckRequest,
  ErrorResponse,
  FlashcardDeckDto,
  HomeData,
  ImageUploadResponse,
  MeResponse,
  Page,
  PracticeSessionDto,
  RoleDto,
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

// Builds a `?k=v&…` query string, dropping undefined values. Returns '' when nothing is set.
function buildQuery(params: Record<string, string | number | boolean | undefined>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined);
  if (entries.length === 0) return '';
  return '?' + entries.map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`).join('&');
}

// Walks a cursor-paginated endpoint to the end, accumulating every page's items.
async function fetchAllPages<T>(fetchPage: (cursor?: string) => Promise<Page<T>>): Promise<T[]> {
  const all: T[] = [];
  let cursor: string | undefined;
  do {
    const page = await fetchPage(cursor);
    all.push(...page.items);
    cursor = page.nextCursor ?? undefined;
  } while (cursor);
  return all;
}

function getDecksPage(params: { limit?: number; cursor?: string } = {}): Promise<Page<FlashcardDeckDto>> {
  return request<Page<FlashcardDeckDto>>(`/decks${buildQuery(params)}`, { auth: true });
}

function getSessionsPage(cursor?: string): Promise<Page<PracticeSessionDto>> {
  return request<Page<PracticeSessionDto>>(`/sessions${buildQuery({ active: false, cursor })}`, { auth: true });
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
  // The current user's identity, roles, and effective permissions (gates admin UI).
  getMe: () => request<MeResponse>('/auth/me', { auth: true }),
  getHome: () => request<HomeData[]>('/home', { auth: true }),
  // One cursor-paginated page of decks (newest first). Pass a prior page's nextCursor to continue.
  getDecks: (params: { limit?: number; cursor?: string } = {}) => getDecksPage(params),
  // Every deck across all pages — for flows that need the whole library (e.g. finding a deck by title).
  getAllDecks: () => fetchAllPages((cursor) => getDecksPage({ cursor })),
  // One page of the global (ownerless) catalog — server-gated on manage_global_decks (admin view).
  getGlobalDecks: (params: { limit?: number; cursor?: string } = {}) =>
    request<Page<FlashcardDeckDto>>(`/decks/global${buildQuery(params)}`, { auth: true }),
  getDeck: (id: number) => request<FlashcardDeckDto>(`/decks/${id}`, { auth: true }),
  createDeck: (deck: CreateDeckRequest) =>
    request<FlashcardDeckDto>('/decks', { method: 'POST', body: deck, auth: true }),
  // Create a global (ownerless) catalog deck — server-gated on the manage_global_decks permission.
  createGlobalDeck: (deck: CreateDeckRequest) =>
    request<FlashcardDeckDto>('/decks/global', { method: 'POST', body: deck, auth: true }),
  updateDeck: (id: number, deck: CreateDeckRequest) =>
    request<FlashcardDeckDto>(`/decks/${id}`, { method: 'PUT', body: deck, auth: true }),
  deleteDeck: (id: number) => request<void>(`/decks/${id}`, { method: 'DELETE', auth: true }),

  // Practice sessions
  createSession: (deckId: number, mode = 'flashcards') =>
    request<PracticeSessionDto>('/sessions', { method: 'POST', body: { deckId, mode }, auth: true }),
  getSession: (id: number) => request<PracticeSessionDto>(`/sessions/${id}`, { auth: true }),
  // Every session across all pages (active + completed) — used to derive per-deck last-practiced time.
  getAllSessions: () => fetchAllPages((cursor) => getSessionsPage(cursor)),
  updateProgress: (id: number, progress: UpdateProgressRequest) =>
    request<PracticeSessionDto>(`/sessions/${id}`, { method: 'PATCH', body: progress, auth: true }),
  completeSession: (id: number) =>
    request<PracticeSessionDto>(`/sessions/${id}/complete`, { method: 'POST', auth: true }),

  uploadImage: (file: File) => uploadImage(file),

  // Admin RBAC (gated server-side on manage_roles). One page of users, optionally filtered by an
  // email substring `q`; the role catalog; and grant/revoke of a user's roles.
  getAdminUsers: (params: { q?: string; limit?: number; cursor?: string } = {}) =>
    request<Page<AdminUserDto>>(`/admin/users${buildQuery(params)}`, { auth: true }),
  getRoles: () => request<RoleDto[]>('/admin/roles', { auth: true }),
  grantRole: (userId: number, role: string) =>
    request<AdminUserDto>(`/admin/users/${userId}/roles`, { method: 'POST', body: { role }, auth: true }),
  revokeRole: (userId: number, role: string) =>
    request<AdminUserDto>(`/admin/users/${userId}/roles/${role}`, { method: 'DELETE', auth: true }),
};
