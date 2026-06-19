import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import { BackHeader } from '../decks/BackHeader';

/**
 * Account settings (FLA-114). Currently just the public display name shown on discussion messages;
 * blank reverts to the email local-part. Loads /auth/me on mount and saves via PATCH /auth/me.
 */
export function SettingsPage() {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [emailPrefix, setEmailPrefix] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let active = true;
    api
      .getMe()
      .then((me) => {
        if (!active) return;
        setEmail(me.email);
        setEmailPrefix(me.email.split('@')[0] ?? '');
        setDisplayName(me.displayName ?? '');
      })
      .catch((err: unknown) => {
        if (active) setError(err instanceof Error ? err.message : 'Could not load your settings.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const me = await api.updateProfile(displayName.trim());
      setDisplayName(me.displayName ?? '');
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save your settings.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="app">
      <BackHeader title="Settings" backTo="/" backLabel="Home" />
      <main className="container">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : (
          <form className="create-form" onSubmit={save}>
            <label>
              Email
              <input type="email" value={email} disabled />
            </label>
            <label>
              Display name
              <input
                type="text"
                value={displayName}
                placeholder={emailPrefix}
                maxLength={80}
                onChange={(e) => {
                  setDisplayName(e.target.value);
                  setSaved(false);
                }}
              />
              <span className="field-hint">
                Shown on discussions. Leave blank to use “{emailPrefix}”.
              </span>
            </label>
            {error && <p className="error">{error}</p>}
            {saved && <p className="success">Saved.</p>}
            <button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          </form>
        )}
      </main>
    </div>
  );
}
