import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ApiError, api } from './client';
import { setToken } from '../auth/token';

type Init = RequestInit & { headers: Record<string, string> };

interface FakeResponse {
  ok: boolean;
  status: number;
  json: () => Promise<unknown>;
}

function stubFetch(res: Partial<FakeResponse>) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: res.ok ?? true,
    status: res.status ?? 200,
    json: res.json ?? (() => Promise.resolve({})),
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function lastCall(fetchMock: ReturnType<typeof stubFetch>): { url: string; init: Init } {
  const [url, init] = fetchMock.mock.calls[0] as [string, Init];
  return { url, init };
}

describe('api client', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.unstubAllGlobals());

  it('getDecks sends an authed GET to /decks', async () => {
    setToken('tok');
    const fetchMock = stubFetch({ json: () => Promise.resolve([]) });

    const decks = await api.getDecks();

    expect(decks).toEqual([]);
    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/decks');
    expect(init.method).toBe('GET');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(init.body).toBeUndefined();
  });

  it('register posts JSON without an auth header', async () => {
    const fetchMock = stubFetch({ json: () => Promise.resolve({ token: 't', userId: 1 }) });

    await api.register('a@b.com', 'pw');

    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/auth/register');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.headers.Authorization).toBeUndefined();
    expect(JSON.parse(init.body as string)).toEqual({ email: 'a@b.com', password: 'pw' });
  });

  it('throws ApiError with the server message on a non-2xx response', async () => {
    stubFetch({ ok: false, status: 409, json: () => Promise.resolve({ error: 'conflict', message: 'taken' }) });

    const err = await api.register('a@b.com', 'pw').catch((e: unknown) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(409);
    expect((err as ApiError).message).toBe('taken');
  });

  it('falls back to a default message when the error body is not JSON', async () => {
    stubFetch({ ok: false, status: 500, json: () => Promise.reject(new Error('not json')) });

    const err = await api.getDecks().catch((e: unknown) => e);

    expect((err as ApiError).status).toBe(500);
    expect((err as ApiError).message).toBe('Request failed (500)');
  });

  it('returns undefined for a 204 response', async () => {
    stubFetch({ ok: true, status: 204, json: () => Promise.reject(new Error('no body')) });

    expect(await api.getDecks()).toBeUndefined();
  });

  it('uploadImage posts multipart FormData with the bearer token and no Content-Type', async () => {
    setToken('tok');
    const fetchMock = stubFetch({ json: () => Promise.resolve({ url: 'https://cdn/x.png' }) });
    const file = new File(['x'], 'img.png', { type: 'image/png' });

    const result = await api.uploadImage(file);

    expect(result).toEqual({ url: 'https://cdn/x.png' });
    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/images');
    expect(init.method).toBe('POST');
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get('file')).toBe(file);
    expect(init.headers['Content-Type']).toBeUndefined();
    expect(init.headers.Authorization).toBe('Bearer tok');
  });

  it('createSession posts the deckId to /sessions', async () => {
    setToken('tok');
    const fetchMock = stubFetch({ json: () => Promise.resolve({ id: 7, deckId: 3 }) });

    const session = await api.createSession(3);

    expect(session).toMatchObject({ id: 7, deckId: 3 });
    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/sessions');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(JSON.parse(init.body as string)).toEqual({ deckId: 3 });
  });

  it('updateProgress PATCHes the session with progress', async () => {
    const fetchMock = stubFetch({ json: () => Promise.resolve({ id: 7 }) });

    await api.updateProgress(7, { currentCardIndex: 2, numCorrect: 1, numIncorrect: 1 });

    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/sessions/7');
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(init.body as string)).toEqual({ currentCardIndex: 2, numCorrect: 1, numIncorrect: 1 });
  });

  it('completeSession posts to /sessions/{id}/complete', async () => {
    const fetchMock = stubFetch({ json: () => Promise.resolve({ id: 7, isCompleted: true }) });

    await api.completeSession(7);

    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/sessions/7/complete');
    expect(init.method).toBe('POST');
  });

  it('logout posts to /auth/logout with the bearer token', async () => {
    setToken('tok');
    const fetchMock = stubFetch({ ok: true, status: 204, json: () => Promise.reject(new Error('no body')) });

    await api.logout();

    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/auth/logout');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer tok');
  });
});
