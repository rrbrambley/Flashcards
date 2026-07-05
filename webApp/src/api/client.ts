import { clearToken, getRefreshToken, getToken, setTokens } from '../auth/token';
import type {
  AdminFlagDto,
  AdminUserDto,
  AnswerSuggestion,
  AuthResponse,
  AvatarOption,
  CreateDeckRequest,
  DiscussionMessage,
  DiscussionThread,
  ErrorResponse,
  FlashcardDeckDto,
  HomeData,
  ImageUploadResponse,
  MeResponse,
  Page,
  PracticeAnswer,
  PracticeSessionDto,
  ReportedMessage,
  RoleDto,
  StreakCalendarResponse,
  StreaksResponse,
  UpdateProfileRequest,
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
  // Update the current user's profile (FLA-114/FLA-162). Per-field merge: omit a field to leave it
  // unchanged, pass a blank string to clear it. (Omitted fields are dropped by JSON.stringify.)
  updateProfile: (update: UpdateProfileRequest) =>
    request<MeResponse>('/auth/me', { method: 'PATCH', body: update, auth: true }),
  // The curated avatar catalog (FLA-162). Empty when the CDN is unconfigured.
  getAvatars: () => request<AvatarOption[]>('/avatars', { auth: true }),
  getHome: () => request<HomeData[]>('/home', { auth: true }),
  // One cursor-paginated page of decks (newest first). Pass a prior page's nextCursor to continue.
  getDecks: (params: { limit?: number; cursor?: string } = {}) => getDecksPage(params),
  // Every deck across all pages — for flows that need the whole library (e.g. finding a deck by title).
  getAllDecks: () => fetchAllPages((cursor) => getDecksPage({ cursor })),
  // One page of the global (ownerless) catalog — server-gated on manage_global_decks (admin view).
  getGlobalDecks: (params: { limit?: number; cursor?: string } = {}) =>
    request<Page<FlashcardDeckDto>>(`/decks/global${buildQuery(params)}`, { auth: true }),
  // Public, unauthenticated guest-mode catalog (browse + practice without an account). Read-only.
  getCatalog: (params: { limit?: number; cursor?: string } = {}) =>
    request<Page<FlashcardDeckDto>>(`/catalog${buildQuery(params)}`),
  getCatalogDeck: (id: number) => request<FlashcardDeckDto>(`/catalog/${id}`),
  getDeck: (id: number) => request<FlashcardDeckDto>(`/decks/${id}`, { auth: true }),
  createDeck: (deck: CreateDeckRequest) =>
    request<FlashcardDeckDto>('/decks', { method: 'POST', body: deck, auth: true }),
  // Create a global (ownerless) catalog deck — server-gated on the manage_global_decks permission.
  createGlobalDeck: (deck: CreateDeckRequest) =>
    request<FlashcardDeckDto>('/decks/global', { method: 'POST', body: deck, auth: true }),
  updateDeck: (id: number, deck: CreateDeckRequest) =>
    request<FlashcardDeckDto>(`/decks/${id}`, { method: 'PUT', body: deck, auth: true }),
  deleteDeck: (id: number) => request<void>(`/decks/${id}`, { method: 'DELETE', auth: true }),
  // Admin (manage_global_decks): toggle whether a deck is a global (catalog) deck (FLA-119).
  setDeckGlobal: (deckId: number, global: boolean) =>
    request<FlashcardDeckDto>(`/decks/${deckId}/global`, { method: 'PATCH', body: { global }, auth: true }),

  // Practice sessions
  createSession: (deckId: number, mode = 'flashcards', shuffle = false) =>
    request<PracticeSessionDto>('/sessions', { method: 'POST', body: { deckId, mode, shuffle }, auth: true }),
  getSession: (id: number) => request<PracticeSessionDto>(`/sessions/${id}`, { auth: true }),
  // Every session across all pages (active + completed) — used to derive per-deck last-practiced time.
  getAllSessions: () => fetchAllPages((cursor) => getSessionsPage(cursor)),
  updateProgress: (id: number, progress: UpdateProgressRequest) =>
    request<PracticeSessionDto>(`/sessions/${id}`, { method: 'PATCH', body: progress, auth: true }),
  // timeZone (IANA): recorded with the completion for day-based streaks (FLA-105).
  completeSession: (id: number, timeZone?: string) =>
    request<PracticeSessionDto>(`/sessions/${id}/complete`, { method: 'POST', body: { timeZone }, auth: true }),
  // Append answers to the session's log (FLA-99); idempotent per answerUid. Returns the session.
  recordAnswers: (id: number, answers: PracticeAnswer[]) =>
    request<PracticeSessionDto>(`/sessions/${id}/answers`, { method: 'POST', body: { answers }, auth: true }),
  getAnswers: (id: number) => request<PracticeAnswer[]>(`/sessions/${id}/answers`, { auth: true }),

  // The caller's resolved feature flags (FLA-174). Also delivered on /auth/me; this refreshes them.
  getFlags: () => request<Record<string, boolean>>('/flags', { auth: true }),

  // Practice streak (FLA-106). `tz` (IANA) anchors "today" to the caller's local day.
  getStreaks: (tz?: string) => request<StreaksResponse>(`/streaks${buildQuery({ tz })}`, { auth: true }),
  // One month's practice-activity days for the streak calendar (FLA-170). `month` is `YYYY-MM`.
  getStreakCalendar: (month: string, tz?: string) =>
    request<StreakCalendarResponse>(`/streaks/calendar${buildQuery({ month, tz })}`, { auth: true }),

  // Card discussions (FLA-116). Reads are public (guests can read); posting/locking need auth.
  getDiscussionThread: (cardUid: string) =>
    request<DiscussionThread>(`/discussions/${encodeURIComponent(cardUid)}`),
  getDiscussionMessages: (cardUid: string, params: { limit?: number; cursor?: string } = {}) =>
    request<Page<DiscussionMessage>>(`/discussions/${encodeURIComponent(cardUid)}/messages${buildQuery(params)}`),
  postDiscussionMessage: (cardUid: string, content: string, parentMessageId?: number) =>
    request<DiscussionMessage>(`/discussions/${encodeURIComponent(cardUid)}/messages`, {
      method: 'POST',
      body: { content, parentMessageId },
      auth: true,
    }),
  // Admin (manage_discussions): lock/unlock a thread, and enable/disable discussions on a global deck.
  lockDiscussionThread: (cardUid: string, locked: boolean) =>
    request<DiscussionThread>(`/discussions/${encodeURIComponent(cardUid)}/lock`, {
      method: 'PATCH',
      body: { locked },
      auth: true,
    }),
  setDeckDiscussionsEnabled: (deckId: number, enabled: boolean) =>
    request<FlashcardDeckDto>(`/decks/${deckId}/discussion`, { method: 'PATCH', body: { enabled }, auth: true }),

  // Moderation (FLA-118). Reporting is any signed-in user; delete + the queue are manage_discussions.
  reportMessage: (messageId: number, reason?: string) =>
    request<void>(`/discussions/messages/${messageId}/report`, { method: 'POST', body: { reason }, auth: true }),
  deleteDiscussionMessage: (messageId: number) =>
    request<DiscussionMessage>(`/discussions/messages/${messageId}`, { method: 'DELETE', auth: true }),
  getDiscussionReports: (params: { limit?: number; cursor?: string } = {}) =>
    request<Page<ReportedMessage>>(`/admin/discussions/reports${buildQuery(params)}`, { auth: true }),
  dismissDiscussionReport: (reportId: number) =>
    request<void>(`/admin/discussions/reports/${reportId}`, {
      method: 'PATCH',
      body: { status: 'dismissed' },
      auth: true,
    }),

  // Answer suggestions (FLA-130). Suggesting is any signed-in user; the queue + accept/dismiss are
  // gated on manage_suggestions.
  suggestAnswer: (cardUid: string, suggestedAnswer: string) =>
    request<void>(`/cards/${encodeURIComponent(cardUid)}/answer-suggestions`, {
      method: 'POST',
      body: { suggestedAnswer },
      auth: true,
    }),
  getAnswerSuggestions: (params: { limit?: number; cursor?: string } = {}) =>
    request<Page<AnswerSuggestion>>(`/admin/answer-suggestions${buildQuery(params)}`, { auth: true }),
  acceptAnswerSuggestion: (id: number) =>
    request<void>(`/admin/answer-suggestions/${id}/accept`, { method: 'POST', auth: true }),
  dismissAnswerSuggestion: (id: number) =>
    request<void>(`/admin/answer-suggestions/${id}/dismiss`, { method: 'POST', auth: true }),

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

  // Admin feature-flag management (FLA-176; server-gated on manage_feature_flags). List the catalog,
  // toggle the global state, and set/clear per-user (by id) and per-role overrides.
  getAdminFlags: () => request<AdminFlagDto[]>('/admin/flags', { auth: true }),
  setFlagGlobal: (key: string, enabled: boolean) =>
    request<AdminFlagDto>(`/admin/flags/${encodeURIComponent(key)}`, { method: 'PATCH', body: { enabled }, auth: true }),
  setFlagUserOverride: (key: string, userId: number, enabled: boolean) =>
    request<AdminFlagDto>(`/admin/flags/${encodeURIComponent(key)}/users/${userId}`, {
      method: 'PUT',
      body: { enabled },
      auth: true,
    }),
  clearFlagUserOverride: (key: string, userId: number) =>
    request<AdminFlagDto>(`/admin/flags/${encodeURIComponent(key)}/users/${userId}`, { method: 'DELETE', auth: true }),
  setFlagRoleOverride: (key: string, roleKey: string, enabled: boolean) =>
    request<AdminFlagDto>(`/admin/flags/${encodeURIComponent(key)}/roles/${encodeURIComponent(roleKey)}`, {
      method: 'PUT',
      body: { enabled },
      auth: true,
    }),
  clearFlagRoleOverride: (key: string, roleKey: string) =>
    request<AdminFlagDto>(`/admin/flags/${encodeURIComponent(key)}/roles/${encodeURIComponent(roleKey)}`, {
      method: 'DELETE',
      auth: true,
    }),
};
