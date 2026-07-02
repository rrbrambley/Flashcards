import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { AvatarOption } from '../api/types';
import { BackHeader } from '../decks/BackHeader';
import { Avatar } from '../components/Avatar';
import { StreakCalendar } from './StreakCalendar';
import { useAuth } from '../auth/auth-context';

/**
 * Account settings (FLA-114/FLA-162). The public display name shown on discussion messages (blank
 * reverts to the email local-part) and the profile avatar — a curated set the user picks from (no
 * uploads, to keep the surface moderation-free). Loads /auth/me + /avatars on mount; the display
 * name saves via the form, while picking/removing an avatar PATCHes /auth/me immediately.
 */
export function SettingsPage() {
  const { setProfile } = useAuth();
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [emailPrefix, setEmailPrefix] = useState('');
  const [avatarKey, setAvatarKey] = useState<string | null>(null);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  const [avatars, setAvatars] = useState<AvatarOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savingAvatar, setSavingAvatar] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let active = true;
    Promise.all([api.getMe(), api.getAvatars().catch(() => [] as AvatarOption[])])
      .then(([me, catalog]) => {
        if (!active) return;
        setEmail(me.email);
        setEmailPrefix(me.email.split('@')[0] ?? '');
        setDisplayName(me.displayName ?? '');
        setAvatarKey(me.avatarKey ?? null);
        setAvatarUrl(me.avatarUrl ?? null);
        setAvatars(catalog);
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
      const me = await api.updateProfile({ displayName: displayName.trim() });
      setDisplayName(me.displayName ?? '');
      setProfile({ displayName: me.displayName ?? null, avatarUrl: me.avatarUrl ?? null });
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save your settings.');
    } finally {
      setSaving(false);
    }
  };

  // Picking an avatar (or removing it, via key === '') saves immediately so the choice is one tap.
  const chooseAvatar = async (key: string) => {
    if (savingAvatar) return;
    setSavingAvatar(true);
    setAvatarError(null);
    try {
      const me = await api.updateProfile({ avatarKey: key });
      setAvatarKey(me.avatarKey ?? null);
      setAvatarUrl(me.avatarUrl ?? null);
      setProfile({ displayName: me.displayName ?? null, avatarUrl: me.avatarUrl ?? null });
    } catch (err) {
      setAvatarError(err instanceof Error ? err.message : 'Could not update your avatar.');
    } finally {
      setSavingAvatar(false);
    }
  };

  return (
    <div className="app">
      <BackHeader title="Settings" backTo="/" backLabel="Home" />
      <main className="container">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : (
          <>
            <section className="settings-section settings-section-activity">
              <h2 className="settings-section-title">Practice activity</h2>
              <StreakCalendar />
            </section>

            <section className="settings-section">
              <h2 className="settings-section-title">Avatar</h2>
              <div className="avatar-current">
                <Avatar url={avatarUrl} name={displayName || emailPrefix} size={64} />
                <div className="avatar-current-text">
                  <span className="field-hint">
                    {avatars.length > 0
                      ? 'Pick an avatar shown on your profile and discussion posts.'
                      : 'Avatars are unavailable right now.'}
                  </span>
                  {avatarKey && (
                    <button
                      type="button"
                      className="link-btn"
                      onClick={() => chooseAvatar('')}
                      disabled={savingAvatar}
                    >
                      Remove avatar
                    </button>
                  )}
                </div>
              </div>
              {avatars.length > 0 && (
                <ul className="avatar-picker" role="radiogroup" aria-label="Choose an avatar">
                  {avatars.map((option) => (
                    <li key={option.key}>
                      <button
                        type="button"
                        role="radio"
                        aria-checked={option.key === avatarKey}
                        aria-label={option.key}
                        className={`avatar-option${option.key === avatarKey ? ' avatar-option-selected' : ''}`}
                        onClick={() => chooseAvatar(option.key)}
                        disabled={savingAvatar}
                      >
                        <Avatar url={option.url} name={option.key} size={56} />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {avatarError && <p className="error">{avatarError}</p>}
            </section>

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
          </>
        )}
      </main>
    </div>
  );
}
