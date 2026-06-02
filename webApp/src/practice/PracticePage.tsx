import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';
import type { FlashcardDeckDto, FlashcardDto, PracticeSessionDto } from '../api/types';
import { BackHeader } from '../decks/BackHeader';
import { PracticeCard } from './PracticeCard';
import { initPractice, practiceReducer } from './practiceReducer';

interface LoadedPractice {
  sessionId: number;
  deckTitle: string;
  cards: FlashcardDto[];
  session: PracticeSessionDto;
}

export function PracticePage() {
  const { id } = useParams();
  const deckId = Number(id);
  const navigate = useNavigate();
  const [reloadToken, setReloadToken] = useState(0);
  const [data, setData] = useState<LoadedPractice | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Derived loading: true until the resolve for the current reloadToken lands, so the
  // effect doesn't have to setState synchronously to show the loading state on reload.
  const [loadedToken, setLoadedToken] = useState(-1);
  const loading = loadedToken !== reloadToken;
  // Dedupe the start-session request across React StrictMode's double-invoked effect (and
  // rapid remounts): both runs share one in-flight createSession, so we never create
  // duplicate sessions for the same deck.
  const loadRef = useRef<{ key: string; promise: Promise<[PracticeSessionDto, FlashcardDeckDto]> } | null>(null);

  useEffect(() => {
    let active = true;
    const key = `${deckId}:${reloadToken}`;
    if (loadRef.current?.key !== key) {
      // createSession starts a fresh session or resumes the deck's active one.
      loadRef.current = { key, promise: Promise.all([api.createSession(deckId), api.getDeck(deckId)]) };
    }
    loadRef.current.promise
      .then(([session, deck]) => {
        if (!active) return;
        if (deck.flashcards.length === 0) {
          setError('This deck has no cards to practice.');
        } else {
          setError(null);
          setData({ sessionId: session.id, deckTitle: deck.title, cards: deck.flashcards, session });
        }
        setLoadedToken(reloadToken);
      })
      .catch((err: unknown) => {
        if (!active) return;
        setError(err instanceof Error ? err.message : 'Could not start practice.');
        setLoadedToken(reloadToken);
      });
    return () => {
      active = false;
    };
  }, [deckId, reloadToken]);

  return (
    <div className="app">
      <BackHeader title={(!loading && data?.deckTitle) || 'Practice'} />
      <main className="container">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : error ? (
          <p className="error">{error}</p>
        ) : !data ? (
          <p className="muted">Loading…</p>
        ) : (
          <PracticeRunner
            key={reloadToken}
            sessionId={data.sessionId}
            cards={data.cards}
            session={data.session}
            onAgain={() => setReloadToken((t) => t + 1)}
            onExit={() => navigate('/library')}
          />
        )}
      </main>
    </div>
  );
}

interface PracticeRunnerProps {
  sessionId: number;
  cards: FlashcardDto[];
  session: PracticeSessionDto;
  onAgain: () => void;
  onExit: () => void;
}

function PracticeRunner({ sessionId, cards, session, onAgain, onExit }: PracticeRunnerProps) {
  const [state, dispatch] = useReducer(practiceReducer, initPractice(cards, session));

  const mark = useCallback(
    (correct: boolean) => {
      if (state.status !== 'practicing') return;
      const wasLast = state.index >= state.cards.length - 1;
      dispatch({ type: correct ? 'MARK_CORRECT' : 'MARK_INCORRECT' });
      // Best-effort persistence (mirrors Android): never block the UI on the network.
      if (wasLast) {
        api.completeSession(sessionId).catch(() => {});
      } else {
        api
          .updateProgress(sessionId, {
            currentCardIndex: state.index + 1,
            numCorrect: state.numCorrect + (correct ? 1 : 0),
            numIncorrect: state.numIncorrect + (correct ? 0 : 1),
          })
          .catch(() => {});
      }
    },
    [state, sessionId],
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight') mark(true);
      else if (e.key === 'ArrowLeft') mark(false);
      else if (e.key === ' ' || e.key === 'Enter') {
        e.preventDefault();
        dispatch({ type: 'FLIP' });
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [mark]);

  if (state.status === 'completed') {
    return (
      <div className="practice-complete">
        <h2>Practice complete</h2>
        <p className="muted">
          You reviewed {cards.length} card{cards.length === 1 ? '' : 's'}.
        </p>
        <div className="score-row">
          <span className="score-chip incorrect">{state.numIncorrect}</span>
          <span className="score-chip correct">{state.numCorrect}</span>
        </div>
        <div className="practice-actions">
          <button onClick={onAgain}>Practice again</button>
          <button className="secondary" onClick={onExit}>
            Back to library
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="practice">
      <div className="score-row">
        <span className="score-chip incorrect" aria-label="incorrect count">
          {state.numIncorrect}
        </span>
        <span className="muted practice-progress">
          {state.index + 1} / {state.cards.length}
        </span>
        <span className="score-chip correct" aria-label="correct count">
          {state.numCorrect}
        </span>
      </div>

      <PracticeCard
        card={state.cards[state.index]}
        isFlipped={state.isFlipped}
        onFlip={() => dispatch({ type: 'FLIP' })}
        onSwipeLeft={() => mark(false)}
        onSwipeRight={() => mark(true)}
      />

      <p className="muted practice-hint">Tap to flip · swipe or ← / → (or the buttons) to mark</p>

      <div className="practice-actions">
        <button className="secondary mark-incorrect" onClick={() => mark(false)}>
          ✗ Still learning
        </button>
        <button className="mark-correct" onClick={() => mark(true)}>
          ✓ Got it
        </button>
      </div>
    </div>
  );
}
