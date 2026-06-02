import { describe, it, expect, beforeEach } from 'vitest';
import { clearToken, getToken, setToken } from './token';

describe('token storage', () => {
  beforeEach(() => localStorage.clear());

  it('returns null when no token is stored', () => {
    expect(getToken()).toBeNull();
  });

  it('round-trips a stored token', () => {
    setToken('tok-123');
    expect(getToken()).toBe('tok-123');
  });

  it('clears the stored token', () => {
    setToken('tok-123');
    clearToken();
    expect(getToken()).toBeNull();
  });
});
