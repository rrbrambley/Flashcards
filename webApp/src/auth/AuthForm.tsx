import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from './auth-context';
import { GoogleButton } from './GoogleButton';

export function AuthForm({ mode }: { mode: 'login' | 'register' }) {
  const { login, register } = useAuth();
  const isRegister = mode === 'register';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!email.trim() || !password) {
      setError('Enter your email and password.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      if (isRegister) {
        await register(email.trim(), password);
      } else {
        await login(email.trim(), password);
      }
      // Success flips the auth state; the router redirects to the app.
    } catch (err) {
      setSubmitting(false);
      setError(messageFor(err));
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>{isRegister ? 'Create your account' : 'Welcome back'}</h1>
        <form onSubmit={submit} className="auth-form">
          <label>
            Email
            <input
              type="email"
              value={email}
              autoComplete="email"
              onChange={(e) => {
                setEmail(e.target.value);
                setError(null);
              }}
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              onChange={(e) => {
                setPassword(e.target.value);
                setError(null);
              }}
            />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? '…' : isRegister ? 'Create account' : 'Log in'}
          </button>
        </form>

        <div className="divider">
          <span>OR</span>
        </div>

        <GoogleButton onError={setError} />

        <p className="switch">
          {isRegister ? 'Already have an account? ' : "Don't have an account? "}
          <Link to={isRegister ? '/login' : '/register'}>{isRegister ? 'Log in' : 'Register'}</Link>
        </p>
      </div>
    </div>
  );
}

function messageFor(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.status) {
      case 409:
        return 'An account with that email already exists.';
      case 401:
        return 'Invalid email or password.';
      case 400:
        return 'Enter a valid email and a password.';
      case 503:
        return "Google sign-in isn't available right now.";
    }
  }
  return 'Something went wrong. Check your connection and try again.';
}
