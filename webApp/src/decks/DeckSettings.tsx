import { useState } from 'react';
import { api } from '../api/client';
import type { FlashcardDeckDto } from '../api/types';
import { ToggleSwitch } from './ToggleSwitch';

interface DeckSettingsProps {
  deck: FlashcardDeckDto;
  canManageGlobal: boolean;
  canManageDiscussions: boolean;
}

/**
 * Admin-only "Deck settings" section on the edit page (FLA-119): an extensible list of per-deck
 * feature toggles. The first are **Global** (whether the deck appears in the shared catalog) and a
 * **Global deck settings** subsection — grayed out until the deck is global — holding the
 * **Discussions** toggle (discussions are global-only). Each switch saves immediately and keeps its
 * own optimistic state from the server's response. New toggles slot in as more rows/subsections.
 */
export function DeckSettings({ deck, canManageGlobal, canManageDiscussions }: DeckSettingsProps) {
  const [isGlobal, setIsGlobal] = useState(deck.isGlobal ?? false);
  const [discussionsEnabled, setDiscussionsEnabled] = useState(deck.discussionsEnabled ?? false);
  const [savingGlobal, setSavingGlobal] = useState(false);
  const [savingDiscussions, setSavingDiscussions] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggleGlobal = async () => {
    const next = !isGlobal;
    setSavingGlobal(true);
    setError(null);
    try {
      const updated = await api.setDeckGlobal(deck.id, next);
      setIsGlobal(updated.isGlobal ?? next);
      // Discussions are global-only, so turning Global off makes the server report them off.
      setDiscussionsEnabled(updated.discussionsEnabled ?? false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update the Global setting.');
    } finally {
      setSavingGlobal(false);
    }
  };

  const toggleDiscussions = async () => {
    const next = !discussionsEnabled;
    setSavingDiscussions(true);
    setError(null);
    try {
      const updated = await api.setDeckDiscussionsEnabled(deck.id, next);
      setDiscussionsEnabled(updated.discussionsEnabled ?? next);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update Discussions.');
    } finally {
      setSavingDiscussions(false);
    }
  };

  return (
    <section className="deck-settings" aria-label="Deck settings">
      <h2 className="deck-settings-title">Deck settings</h2>

      {canManageGlobal && (
        <div className="deck-settings-row">
          <div className="deck-settings-text">
            <span className="deck-settings-name">Global</span>
            <span className="deck-settings-desc">Show this deck in the shared catalog for every user.</span>
          </div>
          <ToggleSwitch ariaLabel="Global deck" checked={isGlobal} onChange={toggleGlobal} disabled={savingGlobal} />
        </div>
      )}

      {canManageDiscussions && (
        // Native fieldset[disabled] also disables the inner switch; the class grays it out.
        <fieldset className="deck-settings-subsection" disabled={!isGlobal}>
          <legend>Global deck settings</legend>
          <div className="deck-settings-row">
            <div className="deck-settings-text">
              <span className="deck-settings-name">Discussions</span>
              <span className="deck-settings-desc">Let learners discuss each card after answering it.</span>
            </div>
            <ToggleSwitch
              ariaLabel="Card discussions"
              checked={discussionsEnabled}
              onChange={toggleDiscussions}
              disabled={!isGlobal || savingDiscussions}
            />
          </div>
        </fieldset>
      )}

      {error && <p className="error">{error}</p>}
    </section>
  );
}
