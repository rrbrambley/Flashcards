import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider } from './AuthContext';
import { useAuth } from './auth-context';
import { api } from '../api/client';
import { getToken, setTokens } from './token';

vi.mock('../api/client', () => ({
  api: {
    login: vi.fn(),
    register: vi.fn(),
    googleSignIn: vi.fn(),
    logout: vi.fn(() => Promise.resolve()),
    // Defaulted to a resolved profile so persist()'s background fetch doesn't reject; individual
    // tests override it. Only displayName/avatarUrl are consumed from this in persist.
    getMe: vi.fn(() => Promise.resolve({ userId: 1, email: 'a@b.com', roles: [], permissions: [] })),
  },
  setUnauthorizedHandler: vi.fn(),
}));

function Consumer() {
  const { token, login, signOut, can, permissionsReady, isEnabled } = useAuth();
  return (
    <div>
      <span data-testid="token">{token ?? 'none'}</span>
      <span data-testid="admin">{can('manage_global_decks') ? 'admin' : 'no'}</span>
      <span data-testid="ready">{permissionsReady ? 'ready' : 'pending'}</span>
      <span data-testid="flag">{isEnabled('streak_calendar') ? 'on' : 'off'}</span>
      <button onClick={() => login('a@b.com', 'pw')}>login</button>
      <button onClick={signOut}>signout</button>
    </div>
  );
}

describe('AuthProvider / useAuth', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('starts logged out when no token is stored', () => {
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    expect(screen.getByTestId('token')).toHaveTextContent('none');
  });

  it('login persists the returned token to context and storage', async () => {
    vi.mocked(api.login).mockResolvedValue({ accessToken: 'tok', refreshToken: 'r', userId: 1 });
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    await userEvent.click(screen.getByText('login'));

    expect(await screen.findByTestId('token')).toHaveTextContent('tok');
    expect(getToken()).toBe('tok');
    expect(api.login).toHaveBeenCalledWith('a@b.com', 'pw');
  });

  it('signOut clears the token', async () => {
    vi.mocked(api.login).mockResolvedValue({ accessToken: 'tok', refreshToken: 'r', userId: 1 });
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await userEvent.click(screen.getByText('login'));
    expect(await screen.findByTestId('token')).toHaveTextContent('tok');

    await userEvent.click(screen.getByText('signout'));

    expect(screen.getByTestId('token')).toHaveTextContent('none');
    expect(getToken()).toBeNull();
    expect(api.logout).toHaveBeenCalled();
  });

  it('exposes permissions from the login response via can()', async () => {
    vi.mocked(api.login).mockResolvedValue({
      accessToken: 'tok',
      refreshToken: 'r',
      userId: 1,
      permissions: ['manage_global_decks'],
    });
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    expect(screen.getByTestId('admin')).toHaveTextContent('no');
    await userEvent.click(screen.getByText('login'));
    expect(await screen.findByTestId('admin')).toHaveTextContent('admin');

    // ...and signOut clears them.
    await userEvent.click(screen.getByText('signout'));
    expect(screen.getByTestId('admin')).toHaveTextContent('no');
  });

  it('hydrates permissions from /me on a fresh load with a stored token', async () => {
    setTokens('stored-tok', 'stored-r');
    vi.mocked(api.getMe).mockResolvedValue({
      userId: 1,
      email: 'a@b.com',
      roles: ['admin'],
      permissions: ['manage_global_decks'],
    });
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    expect(await screen.findByTestId('admin')).toHaveTextContent('admin');
    expect(api.getMe).toHaveBeenCalled();
  });

  it('hydrates feature flags from /me and exposes them via isEnabled', async () => {
    setTokens('stored-tok', 'stored-r');
    vi.mocked(api.getMe).mockResolvedValue({
      userId: 1,
      email: 'a@b.com',
      roles: [],
      permissions: [],
      flags: { streak_calendar: true },
    });
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    expect(await screen.findByTestId('flag')).toHaveTextContent('on');
  });

  it('defaults isEnabled to false for an unknown/absent flag', () => {
    // Logged out (no stored token): flags are empty, so isEnabled is false.
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    expect(screen.getByTestId('flag')).toHaveTextContent('off');
  });

  it('permissions are ready immediately when logged out', () => {
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    expect(screen.getByTestId('ready')).toHaveTextContent('ready');
  });

  it('keeps permissions not-ready on a cold load until /me resolves (FLA-136)', async () => {
    setTokens('stored-tok', 'stored-r');
    let resolveMe: (value: { userId: number; email: string; roles: string[]; permissions: string[] }) => void = () => {};
    vi.mocked(api.getMe).mockReturnValue(
      new Promise((resolve) => {
        resolveMe = resolve;
      }),
    );
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    // A stored token but permissions not yet hydrated — guards must wait, not redirect.
    expect(screen.getByTestId('ready')).toHaveTextContent('pending');

    resolveMe({ userId: 1, email: 'a@b.com', roles: ['admin'], permissions: ['manage_global_decks'] });

    expect(await screen.findByTestId('ready')).toHaveTextContent('ready');
    expect(screen.getByTestId('admin')).toHaveTextContent('admin');
  });

  it('useAuth throws when used outside a provider', () => {
    function Orphan() {
      useAuth();
      return null;
    }
    // Suppress the expected React error log for the throwing render.
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<Orphan />)).toThrow('useAuth must be used within an AuthProvider');
    spy.mockRestore();
  });
});
