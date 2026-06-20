import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/auth-context';
import type { FlashcardDeckDto } from '../api/types';
import { DeckLibrary } from './DeckLibrary';

/**
 * The admin "manage global decks" view: the same deck-library UI as the personal Library, but
 * scoped to the global (ownerless) catalog and offering only the "New global deck" action.
 * Gated on `manage_global_decks` — non-admins are bounced to their own library.
 */
export function GlobalLibraryPage() {
  const { signOut, can } = useAuth();
  const navigate = useNavigate();

  if (!can('manage_global_decks')) {
    return <Navigate to="/library" replace />;
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>Global decks</h1>
        <nav className="app-header-nav">
          <Link to="/library" className="link-btn">
            Library
          </Link>
          <button className="link-btn" onClick={signOut}>
            Sign out
          </button>
        </nav>
      </header>

      <DeckLibrary
        fetchPage={(cursor) => api.getGlobalDecks(cursor ? { cursor } : {})}
        emptyMessage="No global decks yet — create the first one."
        actions={<button onClick={() => navigate('/create?global=1')}>+ New global deck</button>}
        showPractice={false}
        renderDeckControls={can('manage_discussions') ? (deck) => <DiscussionToggle deck={deck} /> : undefined}
      />
    </div>
  );
}

/** Admin per-deck switch for card discussions (FLA-116); manages its own optimistic state. */
function DiscussionToggle({ deck }: { deck: FlashcardDeckDto }) {
  const [enabled, setEnabled] = useState(deck.discussionsEnabled ?? false);
  const [saving, setSaving] = useState(false);

  const toggle = async () => {
    const next = !enabled;
    setSaving(true);
    try {
      const updated = await api.setDeckDiscussionsEnabled(deck.id, next);
      setEnabled(updated.discussionsEnabled ?? next);
    } catch {
      // leave the switch as-is; the admin can retry
    } finally {
      setSaving(false);
    }
  };

  return (
    <label className="deck-discussions-toggle">
      <span className="switch">
        <input
          type="checkbox"
          role="switch"
          checked={enabled}
          disabled={saving}
          onChange={toggle}
          aria-label={`Discussions for ${deck.title}`}
        />
        <span className="switch-track" aria-hidden="true" />
      </span>
      <span className="switch-label">Discussions</span>
    </label>
  );
}
