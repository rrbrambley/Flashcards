import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/auth-context';
import type { FlashcardDeckDto, FlashcardDto, PracticeSessionDto } from '../api/types';
import { BackHeader } from '../decks/BackHeader';
import { ModeChooser } from './ModeChooser';
import { DEFAULT_MODE, findMode, PRACTICE_MODES } from './modes';
import type { PracticeMode } from './modes/types';
import { ShareButton } from './ShareButton';
import { SavePrompt } from './SavePrompt';
import { initPractice, practiceReducer } from './practiceReducer';

// Resolves the practice mode from the `?mode=` query param. When the deck has only one registered
// mode we run it directly (preserving the one-click classic flow); with several modes and none
// chosen yet, we show the chooser. Once a mode is resolved, PracticeSession owns the run.
export function PracticePage() {
  const { id } = useParams();
  const deckId = Number(id);
  const [searchParams] = useSearchParams();
  const mode = findMode(searchParams.get('mode')) ?? (PRACTICE_MODES.length === 1 ? DEFAULT_MODE : undefined);

  if (!mode) return <ModeChooser deckId={deckId} />;
  return <PracticeSession key={mode.key} deckId={deckId} mode={mode} />;
}

type Progress = Pick<PracticeSessionDto, 'currentCardIndex' | 'numCorrect' | 'numIncorrect'>;

interface LoadedPractice {
  // Null for a guest (no account): practice runs entirely in-memory and is never persisted.
  sessionId: number | null;
  deckTitle: string;
  cards: FlashcardDto[];
  progress: Progress;
}

const ZERO_PROGRESS: Progress = { currentCardIndex: 0, numCorrect: 0, numIncorrect: 0 };

function PracticeSession({ deckId, mode }: { deckId: number; mode: PracticeMode }) {
  const navigate = useNavigate();
  const { token } = useAuth();
  const isGuest = !token;
  const [reloadToken, setReloadToken] = useState(0);
  const [data, setData] = useState<LoadedPractice | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loadedToken, setLoadedToken] = useState(-1);
  const loading = loadedToken !== reloadToken;
  const loadRef = useRef<{ key: string; promise: Promise<[PracticeSessionDto | null, FlashcardDeckDto]> } | null>(null);

  // Latest in-memory progress, mirrored here so the back/close guard can read it without re-rendering.
  const progressRef = useRef<{ progress: Progress; status: 'practicing' | 'completed' }>({
    progress: ZERO_PROGRESS,
    status: 'practicing',
  });
  const [guestHasUnsaved, setGuestHasUnsaved] = useState(false);
  // Non-null while the "save your progress?" prompt is open; holds the snapshot to persist on signup.
  const [savePromptProgress, setSavePromptProgress] = useState<Progress | null>(null);

  useEffect(() => {
    let active = true;
    const key = `${deckId}:${mode.key}:${reloadToken}:${isGuest}`;
    if (loadRef.current?.key !== key) {
      // Guests load the deck from the public catalog and never create a session; signed-in users
      // create/resume a server session (which carries any existing progress for this deck + mode).
      const promise: Promise<[PracticeSessionDto | null, FlashcardDeckDto]> = isGuest
        ? api.getCatalogDeck(deckId).then((deck) => [null, deck])
        : Promise.all([api.createSession(deckId, mode.key), api.getDeck(deckId)]);
      loadRef.current = { key, promise };
    }
    loadRef.current.promise
      .then(([session, deck]) => {
        if (!active) return;
        if (deck.flashcards.length === 0) {
          setError('This deck has no cards to practice.');
        } else {
          setError(null);
          setData({
            sessionId: session?.id ?? null,
            deckTitle: deck.title,
            cards: deck.flashcards,
            progress: session ?? ZERO_PROGRESS,
          });
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
  }, [deckId, mode.key, reloadToken, isGuest]);

  const onProgress = useCallback(
    (progress: Progress, status: 'practicing' | 'completed') => {
      progressRef.current = { progress, status };
      const touched = progress.currentCardIndex > 0 || progress.numCorrect > 0 || progress.numIncorrect > 0;
      setGuestHasUnsaved(isGuest && status === 'practicing' && touched);
    },
    [isGuest],
  );

  // Warn on tab close / reload while a guest has unsaved progress.
  useEffect(() => {
    if (!guestHasUnsaved) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [guestHasUnsaved]);

  const leaveTo = isGuest ? '/' : '/library';

  const requestExit = () => {
    const { progress, status } = progressRef.current;
    const touched = progress.currentCardIndex > 0 || progress.numCorrect > 0 || progress.numIncorrect > 0;
    if (isGuest && status === 'practicing' && touched) {
      setSavePromptProgress(progress);
    } else {
      navigate(leaveTo);
    }
  };

  return (
    <div className="app">
      <BackHeader
        title={(!loading && data?.deckTitle) || 'Practice'}
        backLabel={isGuest ? 'Catalog' : 'Library'}
        onBack={requestExit}
        right={<ShareButton deckId={deckId} title={data?.deckTitle} className="link-btn" />}
      />
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
            progress={data.progress}
            mode={mode}
            onProgress={onProgress}
            onAgain={() => setReloadToken((t) => t + 1)}
            onExit={() => navigate(leaveTo)}
          />
        )}
      </main>

      {savePromptProgress && (
        <SavePrompt
          deckId={deckId}
          mode={mode.key}
          progress={savePromptProgress}
          onCancel={() => setSavePromptProgress(null)}
          onLeave={() => navigate(leaveTo)}
        />
      )}
    </div>
  );
}

interface PracticeRunnerProps {
  sessionId: number | null;
  cards: FlashcardDto[];
  progress: Progress;
  mode: PracticeMode;
  onProgress: (progress: Progress, status: 'practicing' | 'completed') => void;
  onAgain: () => void;
  onExit: () => void;
}

// Mode-agnostic session loop: owns progress/score (reducer), best-effort persistence (signed-in
// only), and the completion summary. The current mode renders the card and reports each outcome.
function PracticeRunner({ sessionId, cards, progress, mode, onProgress, onAgain, onExit }: PracticeRunnerProps) {
  const [state, dispatch] = useReducer(practiceReducer, initPractice(cards, progress));

  // Mirror progress up so the parent's leave-guard can read it (and persist on guest save).
  useEffect(() => {
    onProgress({ currentCardIndex: state.index, numCorrect: state.numCorrect, numIncorrect: state.numIncorrect }, state.status);
  }, [state, onProgress]);

  const mark = useCallback(
    (correct: boolean) => {
      if (state.status !== 'practicing') return;
      const wasLast = state.index >= state.cards.length - 1;
      dispatch({ type: correct ? 'MARK_CORRECT' : 'MARK_INCORRECT' });
      // Best-effort persistence (signed-in only; guests have no session). Never block the UI.
      if (sessionId == null) return;
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
            Done
          </button>
        </div>
      </div>
    );
  }

  const ModeComponent = mode.Component;
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

      {/* Keyed by index so each card gets a fresh mode instance (flip/input/selection reset). */}
      <ModeComponent key={state.index} card={state.cards[state.index]} cards={state.cards} onResult={mark} />
    </div>
  );
}
