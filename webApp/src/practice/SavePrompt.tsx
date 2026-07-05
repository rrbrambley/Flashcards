import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import { useAuth } from '../auth/auth-context';
import { setTokens } from '../auth/token';

interface SavePromptProps {
  deckId: number;
  mode: string;
  // The guest's shuffle choice, carried onto the saved session so it stays shuffled (FLA-200). The
  // server mints its own seed, so the resumed order may differ; progress (index) is preserved.
  shuffle: boolean;
  progress: { currentCardIndex: number; numCorrect: number; numIncorrect: number };
  onCancel: () => void;
  onLeave: () => void;
}

/**
 * Shown when a guest tries to leave an in-progress practice session (FLA-101): create an account now
 * to save the session, leave anyway (losing progress), or cancel. On register we persist the session
 * to the backend *before* flipping auth state, so the save is durable; then we land on the logged-in
 * home where the session appears under "Continue studying".
 */
export function SavePrompt({ deckId, mode, shuffle, progress, onCancel, onLeave }: SavePromptProps) {
  const { applyAuth } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const save = async (event: FormEvent) => {
    event.preventDefault();
    if (!email.trim() || !password) {
      setError('Enter your email and password.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      // Register directly (not via the auth context) so we can persist the session BEFORE the auth
      // state flips and swaps the route tree — avoids a race with a re-mounted practice page.
      const auth = await api.register(email.trim(), password);
      setTokens(auth.accessToken, auth.refreshToken); // the next two calls need the bearer
      const session = await api.createSession(deckId, mode, shuffle);
      await api.updateProgress(session.id, progress);
      applyAuth(auth); // now flip the in-memory auth state…
      navigate('/'); // …and land on the logged-in home (session shows under "Continue studying")
    } catch (err) {
      setSubmitting(false);
      setError(messageFor(err));
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Save your progress">
      <div className="modal-card">
        <h2>Save your progress?</h2>
        <p className="muted">
          You'll lose your progress if you leave. Create an account to save this session and pick up
          where you left off.
        </p>
        <form onSubmit={save} className="auth-form">
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
              autoComplete="new-password"
              onChange={(e) => {
                setPassword(e.target.value);
                setError(null);
              }}
            />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? '…' : 'Create account & save'}
          </button>
        </form>
        <div className="modal-actions">
          <button className="link-btn" onClick={onLeave} disabled={submitting}>
            Leave without saving
          </button>
          <button className="link-btn" onClick={onCancel} disabled={submitting}>
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}

function messageFor(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.status) {
      case 409:
        return 'An account with that email already exists. Try logging in instead.';
      case 400:
        return 'Enter a valid email and a password.';
    }
  }
  return 'Something went wrong. Check your connection and try again.';
}
