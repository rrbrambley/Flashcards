import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/auth-context';
import { BackHeader } from '../decks/BackHeader';
import { PRACTICE_MODES } from './modes';

/**
 * Configures a practice run before it starts: pick a mode (the primary decision), adjust settings
 * (Shuffle, default On — FLA-200), then Start. Selecting a mode persists it rather than starting
 * immediately, so the user can finish configuring (and so more settings can slot in below Shuffle
 * later). Start routes to the runner as `?mode=<key>&shuffle=<0|1>`.
 */
export function ModeChooser({ deckId }: { deckId: number }) {
  const navigate = useNavigate();
  const { token } = useAuth();
  const isGuest = !token;
  const [selectedMode, setSelectedMode] = useState<string | null>(null);
  const [shuffle, setShuffle] = useState(true);
  const [deckTitle, setDeckTitle] = useState<string | null>(null);

  // Fetch the deck title for the header ("Practice <deck>"); guests read the public catalog. Best
  // effort — the header falls back to "Practice" until (or if) it loads.
  useEffect(() => {
    let active = true;
    (isGuest ? api.getCatalogDeck(deckId) : api.getDeck(deckId))
      .then((deck) => {
        if (active) setDeckTitle(deck.title);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [deckId, isGuest]);

  const start = () => {
    if (!selectedMode) return;
    navigate(`/decks/${deckId}/practice?mode=${selectedMode}&shuffle=${shuffle ? 1 : 0}`);
  };

  return (
    <div className="app">
      <BackHeader
        title={deckTitle ? `Practice ${deckTitle}` : 'Practice'}
        backTo={isGuest ? '/' : '/library'}
        backLabel={isGuest ? 'Catalog' : 'Library'}
      />
      <main className="container">
        <h2 className="section-heading">Choose a mode</h2>
        <ul className="mode-list" role="radiogroup" aria-label="Practice mode">
          {PRACTICE_MODES.map((mode) => (
            <li key={mode.key}>
              <button
                type="button"
                role="radio"
                aria-checked={selectedMode === mode.key}
                className={`mode-option${selectedMode === mode.key ? ' selected' : ''}`}
                onClick={() => setSelectedMode(mode.key)}
              >
                <span className="mode-option-label">{mode.label}</span>
                <span className="muted">{mode.description}</span>
              </button>
            </li>
          ))}
        </ul>

        <h2 className="section-heading">Settings</h2>
        <label className="shuffle-toggle">
          <input type="checkbox" checked={shuffle} onChange={(e) => setShuffle(e.target.checked)} />
          <span className="shuffle-toggle-label">Shuffle cards</span>
          <span className="muted">Practice in a random order</span>
        </label>

        <button type="button" className="start-practice" onClick={start} disabled={!selectedMode}>
          Start practice
        </button>
      </main>
    </div>
  );
}
