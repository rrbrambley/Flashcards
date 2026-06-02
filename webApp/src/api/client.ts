import { getToken } from '../auth/token';
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

interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
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

export const api = {
  register: (email: string, password: string) =>
    request<AuthResponse>('/auth/register', { method: 'POST', body: { email, password } }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/auth/login', { method: 'POST', body: { email, password } }),
  googleSignIn: (idToken: string) =>
    request<AuthResponse>('/auth/google', { method: 'POST', body: { idToken } }),
  logout: () => request<void>('/auth/logout', { method: 'POST', auth: true }),
  getDecks: () => request<FlashcardDeckDto[]>('/decks', { auth: true }),
  getDeck: (id: number) => request<FlashcardDeckDto>(`/decks/${id}`, { auth: true }),
  createDeck: (deck: CreateDeckRequest) =>
    request<FlashcardDeckDto>('/decks', { method: 'POST', body: deck, auth: true }),
  updateDeck: (id: number, deck: CreateDeckRequest) =>
    request<FlashcardDeckDto>(`/decks/${id}`, { method: 'PUT', body: deck, auth: true }),

  // Practice sessions
  createSession: (deckId: number) =>
    request<PracticeSessionDto>('/sessions', { method: 'POST', body: { deckId }, auth: true }),
  getSession: (id: number) => request<PracticeSessionDto>(`/sessions/${id}`, { auth: true }),
  updateProgress: (id: number, progress: UpdateProgressRequest) =>
    request<PracticeSessionDto>(`/sessions/${id}`, { method: 'PATCH', body: progress, auth: true }),
  completeSession: (id: number) =>
    request<PracticeSessionDto>(`/sessions/${id}/complete`, { method: 'POST', auth: true }),

  // Multipart upload — let the browser set the Content-Type (with the boundary).
  uploadImage: async (file: File): Promise<ImageUploadResponse> => {
    const headers: Record<string, string> = {};
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const form = new FormData();
    form.append('file', file);
    const response = await fetch(`${BASE_URL}/images`, { method: 'POST', headers, body: form });
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
  },
};
