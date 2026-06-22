import { useState, type FormEvent } from 'react';
import { api, ApiError } from '../api/client';
import { useAuth } from '../auth/auth-context';
import { setTokens } from '../auth/token';

interface SuggestAnswerButtonProps {
  cardUid: string;
  /** The answer the user typed (and that was graded incorrect) — what we propose as an alternative. */
  answer: string;
  /** Guests must sign in to suggest; we capture the suggestion and run the conversion (FLA-130). */
  isGuest: boolean;
}

/**
 * "This should be correct" — on the Test-mode incorrect verdict, lets the user propose their typed
 * answer as an alternative (FLA-130). Signed-in users submit directly; guests go through the
 * sign-in/up conversion, which submits the suggestion before flipping auth state. A 409 (already
 * suggested) is treated as success.
 */
export function SuggestAnswerButton({ cardUid, answer, isGuest }: SuggestAnswerButtonProps) {
  const [state, setState] = useState<'idle' | 'submitting' | 'done' | 'error'>('idle');
  const [showAuth, setShowAuth] = useState(false);

  const trimmed = answer.trim();
  if (!trimmed) return null;

  const submit = async () => {
    if (isGuest) {
      setShowAuth(true);
      return;
    }
    setState('submitting');
    try {
      await api.suggestAnswer(cardUid, trimmed);
      setState('done');
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setState('done'); // already suggested — same outcome
      } else {
        setState('error');
      }
    }
  };

  if (state === 'done') {
    return <p className="muted suggest-done">Thanks — your suggestion was sent for review.</p>;
  }

  return (
    <>
      <button type="button" className="link-btn suggest-btn" onClick={submit} disabled={state === 'submitting'}>
        {state === 'submitting' ? 'Sending…' : 'This should be correct'}
      </button>
      {state === 'error' && <p className="error">Couldn’t send your suggestion. Please try again.</p>}
      {showAuth && (
        <SuggestAuthPrompt
          onSubmitted={() => {
            setShowAuth(false);
            setState('done');
          }}
          suggest={() => api.suggestAnswer(cardUid, trimmed)}
          onCancel={() => setShowAuth(false)}
        />
      )}
    </>
  );
}

/**
 * Guest conversion for a suggestion: create an account or log in, then submit the captured
 * suggestion before flipping auth state (mirrors the discussion conversion).
 */
function SuggestAuthPrompt({
  suggest,
  onSubmitted,
  onCancel,
}: {
  suggest: () => Promise<void>;
  onSubmitted: () => void;
  onCancel: () => void;
}) {
  const { applyAuth } = useAuth();
  const [mode, setMode] = useState<'register' | 'login'>('register');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!email.trim() || !password) {
      setError('Enter your email and password.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const auth = mode === 'register' ? await api.register(email.trim(), password) : await api.login(email.trim(), password);
      setTokens(auth.accessToken, auth.refreshToken); // the suggestion below needs the bearer
      try {
        await suggest();
      } catch (err) {
        if (!(err instanceof ApiError && err.status === 409)) throw err;
      }
      applyAuth(auth); // flip auth state last; the suggestion is already saved
      onSubmitted();
    } catch (err) {
      setSubmitting(false);
      setError(messageForAuthError(err, mode));
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Sign in to suggest">
      <div className="modal-card">
        <h2>Suggest an answer</h2>
        <p className="muted">
          {mode === 'register' ? 'Create an account' : 'Log in'} to suggest this answer for review.
        </p>
        <form onSubmit={onSubmit} className="auth-form">
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
              autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
              onChange={(e) => {
                setPassword(e.target.value);
                setError(null);
              }}
            />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? '…' : `${mode === 'register' ? 'Create account' : 'Log in'} & suggest`}
          </button>
        </form>
        <div className="modal-actions">
          <button
            type="button"
            className="link-btn"
            disabled={submitting}
            onClick={() => {
              setMode((m) => (m === 'register' ? 'login' : 'register'));
              setError(null);
            }}
          >
            {mode === 'register' ? 'Have an account? Log in' : 'Need an account? Register'}
          </button>
          <button type="button" className="link-btn" onClick={onCancel} disabled={submitting}>
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}

function messageForAuthError(err: unknown, mode: 'register' | 'login'): string {
  if (err instanceof ApiError) {
    if (err.status === 409) return 'An account with that email already exists. Try logging in instead.';
    if (err.status === 401) return 'Incorrect email or password.';
    if (err.status === 400) return 'Enter a valid email and password.';
  }
  return mode === 'register'
    ? 'Could not create your account. Check your connection and try again.'
    : 'Could not log in. Check your connection and try again.';
}
