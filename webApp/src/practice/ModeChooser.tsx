import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BackHeader } from '../decks/BackHeader';
import { PRACTICE_MODES } from './modes';

/**
 * Lets the user pick how to practice a deck. Each option routes to the practice runner with the
 * chosen mode (`?mode=<key>`) and the Shuffle preference (`&shuffle=<0|1>`), which start-or-resumes
 * that mode's session. Driven by the mode registry, so new modes appear here automatically.
 *
 * Shuffle defaults On (FLA-200): a randomized card order for the run, unless the user turns it off to
 * practice in the deck's saved order.
 */
export function ModeChooser({ deckId }: { deckId: number }) {
  const navigate = useNavigate();
  const [shuffle, setShuffle] = useState(true);
  return (
    <div className="app">
      <BackHeader title="Choose a mode" />
      <main className="container">
        <label className="shuffle-toggle">
          <input type="checkbox" checked={shuffle} onChange={(e) => setShuffle(e.target.checked)} />
          <span className="shuffle-toggle-label">Shuffle cards</span>
          <span className="muted">Practice in a random order</span>
        </label>
        <ul className="mode-list">
          {PRACTICE_MODES.map((mode) => (
            <li key={mode.key}>
              <button
                className="mode-option"
                onClick={() => navigate(`/decks/${deckId}/practice?mode=${mode.key}&shuffle=${shuffle ? 1 : 0}`)}
              >
                <span className="mode-option-label">{mode.label}</span>
                <span className="muted">{mode.description}</span>
              </button>
            </li>
          ))}
        </ul>
      </main>
    </div>
  );
}
