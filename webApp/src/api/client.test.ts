import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ApiError, api, setUnauthorizedHandler } from './client';
import { getToken, setToken, setTokens } from '../auth/token';

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

  it('getHome sends an authed GET to /home', async () => {
    setToken('tok');
    const fetchMock = stubFetch({ json: () => Promise.resolve([]) });

    await api.getHome();

    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/home');
    expect(init.method).toBe('GET');
    expect(init.headers.Authorization).toBe('Bearer tok');
  });

  it('getDecks sends an authed GET to /decks and returns the page', async () => {
    setToken('tok');
    const fetchMock = stubFetch({ json: () => Promise.resolve({ items: [], nextCursor: null }) });

    const page = await api.getDecks();

    expect(page).toEqual({ items: [], nextCursor: null });
    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/decks');
    expect(url).not.toContain('?'); // no params on the first page
    expect(init.method).toBe('GET');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(init.body).toBeUndefined();
  });

  it('getAllDecks follows nextCursor across pages', async () => {
    setToken('tok');
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ items: [{ id: 1 }], nextCursor: 'c1' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ items: [{ id: 2 }], nextCursor: null }),
      });
    vi.stubGlobal('fetch', fetchMock);

    const decks = await api.getAllDecks();

    expect(decks).toEqual([{ id: 1 }, { id: 2 }]);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect((fetchMock.mock.calls[0] as [string])[0]).not.toContain('cursor=');
    expect((fetchMock.mock.calls[1] as [string])[0]).toContain('cursor=c1');
  });

  it('getAllSessions walks pages of /sessions?active=false', async () => {
    setToken('tok');
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ items: [{ id: 10, deckId: 1 }], nextCursor: 'c1' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ items: [{ id: 11, deckId: 2 }], nextCursor: null }),
      });
    vi.stubGlobal('fetch', fetchMock);

    const sessions = await api.getAllSessions();

    expect(sessions.map((s) => s.id)).toEqual([10, 11]);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect((fetchMock.mock.calls[0] as [string])[0]).toContain('active=false');
    expect((fetchMock.mock.calls[1] as [string])[0]).toContain('cursor=c1');
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

  it('createSession posts the deckId + mode to /sessions', async () => {
    setToken('tok');
    const fetchMock = stubFetch({ json: () => Promise.resolve({ id: 7, deckId: 3 }) });

    const session = await api.createSession(3, 'test', true);

    expect(session).toMatchObject({ id: 7, deckId: 3 });
    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/sessions');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(JSON.parse(init.body as string)).toEqual({ deckId: 3, mode: 'test', shuffle: true, questionCount: null });
  });

  it('createSession defaults the mode to flashcards and shuffle off', async () => {
    setToken('tok');
    const fetchMock = stubFetch({ json: () => Promise.resolve({ id: 7, deckId: 3 }) });

    await api.createSession(3);

    const { init } = lastCall(fetchMock);
    expect(JSON.parse(init.body as string)).toEqual({
      deckId: 3,
      mode: 'flashcards',
      shuffle: false,
      questionCount: null,
    });
  });

  it('updateProgress PATCHes the session with progress', async () => {
    const fetchMock = stubFetch({ json: () => Promise.resolve({ id: 7 }) });

    await api.updateProgress(7, { currentCardIndex: 2, numCorrect: 1, numIncorrect: 1 });

    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/sessions/7');
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(init.body as string)).toEqual({ currentCardIndex: 2, numCorrect: 1, numIncorrect: 1 });
  });

  it('deleteDeck sends an authed DELETE to /decks/{id}', async () => {
    setToken('tok');
    const fetchMock = stubFetch({ ok: true, status: 204, json: () => Promise.reject(new Error('no body')) });

    await api.deleteDeck(9);

    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/decks/9');
    expect(init.method).toBe('DELETE');
    expect(init.headers.Authorization).toBe('Bearer tok');
  });

  it('completeSession posts to /sessions/{id}/complete', async () => {
    const fetchMock = stubFetch({ json: () => Promise.resolve({ id: 7, isCompleted: true }) });

    await api.completeSession(7);

    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/sessions/7/complete');
    expect(init.method).toBe('POST');
  });

  it('logout posts the refresh token to /auth/logout with the bearer token', async () => {
    setTokens('tok', 'refresh-1');
    const fetchMock = stubFetch({ ok: true, status: 204, json: () => Promise.reject(new Error('no body')) });

    await api.logout('refresh-1');

    const { url, init } = lastCall(fetchMock);
    expect(url).toContain('/auth/logout');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(JSON.parse(init.body as string)).toEqual({ refreshToken: 'refresh-1' });
  });

  it('transparently refreshes on a 401 and retries the original request', async () => {
    setTokens('expired-access', 'refresh-1');
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 401, json: () => Promise.resolve({ error: 'unauthorized' }) })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ accessToken: 'fresh-access', refreshToken: 'refresh-2', userId: 1 }),
      })
      .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve([{ id: 1 }]) });
    vi.stubGlobal('fetch', fetchMock);

    const decks = await api.getDecks();

    expect(decks).toEqual([{ id: 1 }]);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect((fetchMock.mock.calls[1] as [string])[0]).toContain('/auth/refresh');
    // The refreshed access token is persisted and used for the retry.
    expect(getToken()).toBe('fresh-access');
    expect((fetchMock.mock.calls[2] as [string, Init])[1].headers.Authorization).toBe('Bearer fresh-access');
  });

  it('on a 401 with no refresh token, clears auth and invokes the unauthorized handler', async () => {
    setToken('tok'); // access token only, no refresh token
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    stubFetch({ ok: false, status: 401, json: () => Promise.resolve({ error: 'unauthorized', message: 'nope' }) });

    const err = await api.getDecks().catch((e: unknown) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(401);
    expect(onUnauthorized).toHaveBeenCalled();
    expect(getToken()).toBeNull();
    setUnauthorizedHandler(null);
  });
});
