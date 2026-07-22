import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/auth-context';
import { BackHeader } from '../decks/BackHeader';
import { PRACTICE_MODES } from './modes';
import { exitTarget, fromState } from './exitTarget';

/**
 * Configures a practice run before it starts: pick a mode (the primary decision), adjust settings
 * (Shuffle, default On — FLA-200), then Start. Selecting a mode persists it rather than starting
 * immediately, so the user can finish configuring (and so more settings can slot in below Shuffle
 * later). Start routes to the runner as `?mode=<key>&shuffle=<0|1>`.
 */
export function ModeChooser({ deckId }: { deckId: number }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { token, isEnabled } = useAuth();
  const isGuest = !token;
  // A mode is offered only when its feature flag is on (FLA-213). Guests carry no flags (the flag
  // endpoints are authed), so — like the discussions kill switch — they see every mode.
  const modes = PRACTICE_MODES.filter((mode) => isGuest || isEnabled(mode.flagKey));
  // Offer the "Questions" subset field only when its flag is on (FLA-219); guests carry no flags → shown.
  const questionsEnabled = isGuest || isEnabled('practice_question_count');
  // Offer the "Grade at the end" toggle only when its flag is on (#293); guests carry no flags → shown.
  const gradeAtEndEnabled = isGuest || isEnabled('practice_grade_at_end');
  // Offer the "Timed" toggle only when its flag is on (#289); guests carry no flags → shown.
  const timerEnabled = isGuest || isEnabled('practice_timer');
  // Carry the practice referrer (FLA-168) through the chooser so the runner exits to it too.
  const from = fromState(location.state);
  const exit = exitTarget(from, isGuest);
  const [selectedMode, setSelectedMode] = useState<string | null>(null);
  const [shuffle, setShuffle] = useState(true);
  const [gradeAtEnd, setGradeAtEnd] = useState(false);
  // A per-session time limit as mm:ss (#289); default 1:00. Kept as strings so the fields can be blank
  // mid-edit, parsed to total seconds on Start.
  const [timed, setTimed] = useState(false);
  const [minutes, setMinutes] = useState('1');
  const [seconds, setSeconds] = useState('0');
  const [deckTitle, setDeckTitle] = useState<string | null>(null);
  // The deck's card count = the max questions; the field defaults to it (practice the whole deck).
  const [maxQuestions, setMaxQuestions] = useState(0);
  const [questions, setQuestions] = useState<number | null>(null);

  // Fetch the deck for its title (header) + card count (the Questions max); guests read the public
  // catalog. Best effort — the header falls back to "Practice" until (or if) it loads.
  useEffect(() => {
    let active = true;
    (isGuest ? api.getCatalogDeck(deckId) : api.getDeck(deckId))
      .then((deck) => {
        if (!active) return;
        setDeckTitle(deck.title);
        setMaxQuestions(deck.flashcards.length);
        setQuestions(deck.flashcards.length);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [deckId, isGuest]);

  // Grade-at-the-end only applies to the objectively-graded modes (#293) — not Classic's self-graded flip.
  const canGradeAtEnd = gradeAtEndEnabled && (selectedMode === 'test' || selectedMode === 'multiple_choice');

  // Total seconds from the mm:ss fields (#289), at least 1 (the backend rejects < 1); null when off.
  const timeLimitSeconds =
    timerEnabled && timed
      ? Math.max(1, (parseInt(minutes, 10) || 0) * 60 + (parseInt(seconds, 10) || 0))
      : null;

  const start = () => {
    if (!selectedMode) return;
    let url = `/decks/${deckId}/practice?mode=${selectedMode}&shuffle=${shuffle ? 1 : 0}`;
    // Only a real subset (< the whole deck) needs the param; the runner treats its absence as "all".
    if (questionsEnabled && questions != null && questions < maxQuestions) url += `&questions=${questions}`;
    if (canGradeAtEnd && gradeAtEnd) url += '&gradeAtEnd=1';
    if (timeLimitSeconds != null) url += `&timeLimit=${timeLimitSeconds}`;
    navigate(url, { state: { from } });
  };

  return (
    <div className="app">
      <BackHeader
        title={deckTitle ? `Practice ${deckTitle}` : 'Practice'}
        backTo={exit.to}
        backLabel={exit.label}
      />
      <main className="container">
        <h2 className="section-heading">Choose a mode</h2>
        {modes.length === 0 && <p className="muted">No practice modes are available right now.</p>}
        <ul className="mode-list" role="radiogroup" aria-label="Practice mode">
          {modes.map((mode) => (
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
        {questionsEnabled && maxQuestions > 0 && (
          <label className="questions-field">
            <span className="questions-field-label">Questions (max {maxQuestions})</span>
            <input
              type="number"
              min={1}
              max={maxQuestions}
              value={questions ?? ''}
              aria-label={`Questions (max ${maxQuestions})`}
              onChange={(e) => {
                const raw = e.target.value;
                if (raw === '') {
                  setQuestions(null);
                  return;
                }
                // Clamp to 1..max so the field can't request more cards than the deck has, or fewer than one.
                setQuestions(Math.max(1, Math.min(maxQuestions, Math.floor(Number(raw)))));
              }}
            />
          </label>
        )}

        <label className="shuffle-toggle">
          <input type="checkbox" checked={shuffle} onChange={(e) => setShuffle(e.target.checked)} />
          <span className="shuffle-toggle-label">Shuffle cards</span>
          <span className="muted">Practice in a random order</span>
        </label>

        {/* Always shown (when flagged), but disabled unless a gradeable mode is picked — Classic is a
            self-graded flip, so there's nothing to defer. */}
        {gradeAtEndEnabled && (
          <label className={`shuffle-toggle${canGradeAtEnd ? '' : ' disabled'}`}>
            <input
              type="checkbox"
              checked={canGradeAtEnd && gradeAtEnd}
              disabled={!canGradeAtEnd}
              onChange={(e) => setGradeAtEnd(e.target.checked)}
            />
            <span className="shuffle-toggle-label">Grade at the end</span>
            <span className="muted">
              {canGradeAtEnd ? 'Answer every card, then submit to see your score' : 'Available for Test & Multiple Choice'}
            </span>
          </label>
        )}

        {timerEnabled && (
          <>
            <label className="shuffle-toggle">
              <input type="checkbox" checked={timed} onChange={(e) => setTimed(e.target.checked)} />
              <span className="shuffle-toggle-label">Timed</span>
              <span className="muted">Auto-submit when the clock runs out</span>
            </label>
            {timed && (
              <div className="timed-field">
                <span className="questions-field-label">Time limit</span>
                <div className="mmss-input">
                  <input
                    type="number"
                    min={0}
                    value={minutes}
                    aria-label="Minutes"
                    onChange={(e) => setMinutes(e.target.value.replace(/\D/g, ''))}
                  />
                  <span className="mmss-unit">min</span>
                  <input
                    type="number"
                    min={0}
                    max={59}
                    value={seconds}
                    aria-label="Seconds"
                    onChange={(e) => {
                      const n = e.target.value.replace(/\D/g, '');
                      // Clamp seconds to 0–59 so mm:ss stays well-formed; blank is allowed mid-edit.
                      setSeconds(n === '' ? '' : String(Math.min(59, Number(n))));
                    }}
                  />
                  <span className="mmss-unit">sec</span>
                </div>
              </div>
            )}
          </>
        )}

        <button type="button" className="start-practice" onClick={start} disabled={!selectedMode}>
          Start practice
        </button>
      </main>
    </div>
  );
}
