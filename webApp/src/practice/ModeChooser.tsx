import { useNavigate } from 'react-router-dom';
import { BackHeader } from '../decks/BackHeader';
import { PRACTICE_MODES } from './modes';

/**
 * Lets the user pick how to practice a deck. Each option routes to the practice runner with the
 * chosen mode (`?mode=<key>`), which start-or-resumes that mode's session. Driven by the mode
 * registry, so new modes appear here automatically.
 */
export function ModeChooser({ deckId }: { deckId: number }) {
  const navigate = useNavigate();
  return (
    <div className="app">
      <BackHeader title="Choose a mode" />
      <main className="container">
        <ul className="mode-list">
          {PRACTICE_MODES.map((mode) => (
            <li key={mode.key}>
              <button className="mode-option" onClick={() => navigate(`/decks/${deckId}/practice?mode=${mode.key}`)}>
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
