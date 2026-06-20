import { useEffect, useState, type FormEvent } from 'react';
import { api, ApiError } from '../api/client';
import { useAuth } from '../auth/auth-context';
import { setTokens } from '../auth/token';
import type { DiscussionMessage, DiscussionThread } from '../api/types';

interface DiscussionPanelProps {
  cardUid: string;
  /** Guests can read but must sign in to post (the conversion prompt, FLA-116). */
  isGuest: boolean;
  /** Admins (manage_discussions) get a lock/unlock control. */
  canModerate: boolean;
  onClose: () => void;
}

/**
 * The per-card discussion thread (FLA-116): paginated messages with one level of replies, a post box
 * (guests are prompted to sign in), and an admin lock toggle. Reads are public; posting needs auth.
 */
export function DiscussionPanel({ cardUid, isGuest, canModerate, onClose }: DiscussionPanelProps) {
  const [thread, setThread] = useState<DiscussionThread | null>(null);
  const [messages, setMessages] = useState<DiscussionMessage[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [content, setContent] = useState('');
  const [replyTo, setReplyTo] = useState<DiscussionMessage | null>(null);
  const [posting, setPosting] = useState(false);
  const [postError, setPostError] = useState<string | null>(null);
  // Holds the pending text while a guest completes the sign-in/up conversion.
  const [authPrompt, setAuthPrompt] = useState<{ content: string; parentMessageId?: number } | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([api.getDiscussionThread(cardUid), api.getDiscussionMessages(cardUid)])
      .then(([t, page]) => {
        if (!active) return;
        setThread(t);
        setMessages(page.items);
        setNextCursor(page.nextCursor);
      })
      .catch((err: unknown) => {
        if (active) setLoadError(err instanceof Error ? err.message : 'Could not load the discussion.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [cardUid]);

  const loadMore = async () => {
    if (!nextCursor) return;
    try {
      const page = await api.getDiscussionMessages(cardUid, { cursor: nextCursor });
      setMessages((m) => [...m, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch {
      // leave the existing messages; the user can retry
    }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const text = content.trim();
    if (!text) return;
    if (isGuest) {
      // Conversion: capture the pending message and ask the guest to sign in / register.
      setAuthPrompt({ content: text, parentMessageId: replyTo?.id });
      return;
    }
    setPosting(true);
    setPostError(null);
    try {
      const message = await api.postDiscussionMessage(cardUid, text, replyTo?.id);
      setMessages((m) => [...m, message]);
      setThread((t) => (t ? { ...t, messageCount: t.messageCount + 1 } : t));
      setContent('');
      setReplyTo(null);
    } catch (err) {
      setPostError(messageForPostError(err));
    } finally {
      setPosting(false);
    }
  };

  const toggleLock = async () => {
    if (!thread) return;
    try {
      setThread(await api.lockDiscussionThread(cardUid, !thread.isLocked));
    } catch {
      // ignore — the button can be retried
    }
  };

  const locked = thread?.isLocked ?? false;
  const topLevel = messages.filter((m) => m.parentMessageId == null);
  const repliesByParent = groupReplies(messages);

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Card discussion">
      <div className="modal-card discussion-panel">
        <header className="discussion-header">
          <h2>Discussion</h2>
          <div className="discussion-header-actions">
            {canModerate && thread && (
              <button type="button" className="link-btn" onClick={toggleLock}>
                {locked ? 'Unlock' : 'Lock'}
              </button>
            )}
            <button type="button" className="link-btn" onClick={onClose} aria-label="Close discussion">
              Close
            </button>
          </div>
        </header>

        <div className="discussion-body">
          {loading ? (
            <p className="muted">Loading…</p>
          ) : loadError ? (
            <p className="error">{loadError}</p>
          ) : topLevel.length === 0 ? (
            <p className="muted">No messages yet. Start the conversation!</p>
          ) : (
            <ul className="discussion-list">
              {topLevel.map((message) => (
                <li key={message.id}>
                  <DiscussionMessageView message={message} />
                  {!locked && (
                    <button type="button" className="link-btn discussion-reply-btn" onClick={() => setReplyTo(message)}>
                      Reply
                    </button>
                  )}
                  {repliesByParent.get(message.id)?.length ? (
                    <ul className="discussion-replies">
                      {repliesByParent.get(message.id)!.map((reply) => (
                        <li key={reply.id}>
                          <DiscussionMessageView message={reply} />
                        </li>
                      ))}
                    </ul>
                  ) : null}
                </li>
              ))}
              {nextCursor && (
                <li>
                  <button type="button" className="link-btn" onClick={loadMore}>
                    Load more
                  </button>
                </li>
              )}
            </ul>
          )}
        </div>

        {locked ? (
          <p className="muted discussion-locked">🔒 This discussion is locked.</p>
        ) : (
          <form className="discussion-form" onSubmit={submit}>
            {replyTo && (
              <div className="discussion-replying">
                Replying to {replyTo.authorDisplayName}
                <button type="button" className="link-btn" onClick={() => setReplyTo(null)}>
                  Cancel
                </button>
              </div>
            )}
            <textarea
              value={content}
              rows={2}
              maxLength={500}
              placeholder={isGuest ? 'Sign in to join the discussion…' : 'Add a message…'}
              aria-label="Your message"
              onChange={(e) => {
                setContent(e.target.value);
                setPostError(null);
              }}
            />
            {postError && <p className="error">{postError}</p>}
            <button type="submit" disabled={posting || !content.trim()}>
              {posting ? 'Posting…' : isGuest ? 'Sign in to post' : 'Post'}
            </button>
          </form>
        )}
      </div>

      {authPrompt && (
        <DiscussionAuthPrompt
          cardUid={cardUid}
          content={authPrompt.content}
          parentMessageId={authPrompt.parentMessageId}
          onCancel={() => setAuthPrompt(null)}
        />
      )}
    </div>
  );
}

function DiscussionMessageView({ message }: { message: DiscussionMessage }) {
  return (
    <div className="discussion-message">
      <div className="discussion-message-meta">
        <span className="discussion-author">{message.authorDisplayName}</span>
        <span className="muted discussion-time">{relativeTime(message.createdAtMillis)}</span>
      </div>
      <p className="discussion-content">{message.content}</p>
    </div>
  );
}

/**
 * Guest conversion (FLA-116): create an account or log in, then post the pending message before
 * flipping auth state (mirrors SavePrompt) so the message is saved as the new user.
 */
function DiscussionAuthPrompt({
  cardUid,
  content,
  parentMessageId,
  onCancel,
}: {
  cardUid: string;
  content: string;
  parentMessageId?: number;
  onCancel: () => void;
}) {
  const { applyAuth } = useAuth();
  const [mode, setMode] = useState<'register' | 'login'>('register');
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
      const auth = mode === 'register' ? await api.register(email.trim(), password) : await api.login(email.trim(), password);
      setTokens(auth.accessToken, auth.refreshToken); // the post below needs the bearer
      await api.postDiscussionMessage(cardUid, content, parentMessageId);
      applyAuth(auth); // flip auth state last (swaps the route tree); the message is already saved
    } catch (err) {
      setSubmitting(false);
      setError(messageForAuthError(err, mode));
    }
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Sign in to post">
      <div className="modal-card">
        <h2>Join the discussion</h2>
        <p className="muted">
          {mode === 'register' ? 'Create an account' : 'Log in'} to post your message and help others learn.
        </p>
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
              autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
              onChange={(e) => {
                setPassword(e.target.value);
                setError(null);
              }}
            />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? '…' : mode === 'register' ? 'Create account & post' : 'Log in & post'}
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

/** Groups replies (parentMessageId set) by their parent id, preserving order. */
function groupReplies(messages: DiscussionMessage[]): Map<number, DiscussionMessage[]> {
  const byParent = new Map<number, DiscussionMessage[]>();
  for (const message of messages) {
    if (message.parentMessageId == null) continue;
    const list = byParent.get(message.parentMessageId) ?? [];
    list.push(message);
    byParent.set(message.parentMessageId, list);
  }
  return byParent;
}

/** Compact relative time: "just now", "5m", "3h", "2d", else a date. */
function relativeTime(millis: number): string {
  const diff = Date.now() - millis;
  if (diff < 60_000) return 'just now';
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d`;
  return new Date(millis).toLocaleDateString();
}

function messageForPostError(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 429) return "You're posting too quickly. Please wait a bit.";
    if (err.status === 403) return 'This discussion is locked.';
    if (err.status === 400) return err.message || 'Your message was rejected.';
  }
  return 'Could not post your message. Check your connection and try again.';
}

function messageForAuthError(err: unknown, mode: 'register' | 'login'): string {
  if (err instanceof ApiError) {
    if (err.status === 409) return 'An account with that email already exists. Try logging in instead.';
    if (err.status === 401) return 'Incorrect email or password.';
    if (err.status === 400) return 'Enter a valid email and password.';
    if (err.status === 429) return "You're posting too quickly. Please wait a bit.";
  }
  return mode === 'register'
    ? 'Could not create your account. Check your connection and try again.'
    : 'Could not log in. Check your connection and try again.';
}
