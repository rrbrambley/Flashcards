import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider } from './AuthContext';
import { useAuth } from './auth-context';
import { api } from '../api/client';
import { getToken } from './token';

vi.mock('../api/client', () => ({
  api: { login: vi.fn(), register: vi.fn(), googleSignIn: vi.fn() },
}));

function Consumer() {
  const { token, login, signOut } = useAuth();
  return (
    <div>
      <span data-testid="token">{token ?? 'none'}</span>
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
    vi.mocked(api.login).mockResolvedValue({ token: 'tok', userId: 1 });
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
    vi.mocked(api.login).mockResolvedValue({ token: 'tok', userId: 1 });
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
