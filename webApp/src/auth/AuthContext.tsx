import { createContext, useContext, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import { clearToken, getToken, setToken } from './token';

interface AuthContextValue {
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  googleSignIn: (idToken: string) => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(getToken());

  const persist = (value: string) => {
    setToken(value);
    setTokenState(value);
  };

  const value: AuthContextValue = {
    token,
    login: async (email, password) => persist((await api.login(email, password)).token),
    register: async (email, password) => persist((await api.register(email, password)).token),
    googleSignIn: async (idToken) => persist((await api.googleSignIn(idToken)).token),
    signOut: () => {
      clearToken();
      setTokenState(null);
    },
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
