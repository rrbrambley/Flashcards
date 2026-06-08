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
    getMe: vi.fn(),
  },
  setUnauthorizedHandler: vi.fn(),
}));

function Consumer() {
  const { token, login, signOut, can } = useAuth();
  return (
    <div>
      <span data-testid="token">{token ?? 'none'}</span>
      <span data-testid="admin">{can('manage_global_decks') ? 'admin' : 'no'}</span>
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
