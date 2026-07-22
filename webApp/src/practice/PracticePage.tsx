import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/auth-context';
import type { FlashcardDeckDto, FlashcardDto, PracticeAnswer, PracticeSessionDto } from '../api/types';
import { BackHeader } from '../decks/BackHeader';
import { ModeChooser } from './ModeChooser';
import { BatchPracticeRunner } from './BatchPracticeRunner';
import { DEFAULT_MODE, findMode, PRACTICE_MODES } from './modes';
import type { PracticeMode } from './modes/types';
import { ShareButton } from './ShareButton';
import { SavePrompt } from './SavePrompt';
import { DiscussionPanel } from './DiscussionPanel';
import { initPractice, practiceReducer } from './practiceReducer';
import { trailingCorrectStreak } from './grading/streak';
import { orderCards } from './shuffle';
import { exitTarget, fromState } from './exitTarget';
import { formatRemaining, useCountdown } from './useCountdown';
import { useExitGuard } from './useExitGuard';

// Resolves the practice mode from the `?mode=` query param. When the deck has only one registered
// mode we run it directly (preserving the one-click classic flow); with several modes and none
// chosen yet, we show the chooser. Once a mode is resolved, PracticeSession owns the run.
export function PracticePage() {
  const { id } = useParams();
  const deckId = Number(id);
  const [searchParams] = useSearchParams();
  const mode = findMode(searchParams.get('mode')) ?? (PRACTICE_MODES.length === 1 ? DEFAULT_MODE : undefined);
  // Shuffle defaults On (FLA-200): only an explicit `shuffle=0` opts into the deck's saved order.
  const shuffle = searchParams.get('shuffle') !== '0';
  // Practice a subset of the deck (FLA-219): `?questions=N` limits the run to N cards; absent = all.
  const questionsParam = Number(searchParams.get('questions'));
  const questionCount = Number.isFinite(questionsParam) && questionsParam >= 1 ? Math.floor(questionsParam) : null;
  // Grade the whole session at the end (#293): only Test + Multiple Choice have an objective grade to defer.
  const gradeAtEnd =
    searchParams.get('gradeAtEnd') === '1' && (mode?.key === 'test' || mode?.key === 'multiple_choice');
  // Optional per-session time limit in seconds (#289): `?timeLimit=N`; absent/<1 = untimed.
  const timeLimitParam = Number(searchParams.get('timeLimit'));
  const timeLimit = Number.isFinite(timeLimitParam) && timeLimitParam >= 1 ? Math.floor(timeLimitParam) : null;

  if (!mode) return <ModeChooser deckId={deckId} />;
  return (
    <PracticeSession
      key={mode.key}
      deckId={deckId}
      mode={mode}
      shuffle={shuffle}
      questionCount={questionCount}
      gradeAtEnd={gradeAtEnd}
      timeLimit={timeLimit}
    />
  );
}

// A positive, JS-safe (< 2^31) seed for a guest's in-memory shuffle (signed-in runs use the server's
// stored seed). Matches the server/mobile seed range so it feeds the same deterministic shuffle.
function newGuestSeed(): number {
  return Math.floor(Math.random() * (2 ** 31 - 1)) + 1;
}

type Progress = Pick<PracticeSessionDto, 'currentCardIndex' | 'numCorrect' | 'numIncorrect'>;

interface LoadedPractice {
  // Null for a guest (no account): practice runs entirely in-memory and is never persisted.
  sessionId: number | null;
  deckTitle: string;
  cards: FlashcardDto[];
  progress: Progress;
  // The in-session streak (FLA-99) restored from the answer log on resume; 0 for a fresh session.
  initialStreak: number;
  // Whether per-card discussions are available on this deck (FLA-116) — global deck + enabled.
  discussionsEnabled: boolean;
  // Whether this is a global (catalog) deck (FLA-120) — gates Test-mode answer suggestions (FLA-130).
  isGlobal: boolean;
  // Grade the whole session at the end (#293) — swaps the card-by-card runner for the batch list.
  gradeAtEnd: boolean;
  // Wall-clock deadline (epoch millis) for a timed session (#289), or null when untimed. Derived once
  // from the session's `createdAt + timeLimitSeconds` (signed-in) / mint time (guest) so it's stable.
  deadline: number | null;
}

const ZERO_PROGRESS: Progress = { currentCardIndex: 0, numCorrect: 0, numIncorrect: 0 };

function PracticeSession({
  deckId,
  mode,
  shuffle,
  questionCount,
  gradeAtEnd,
  timeLimit,
}: {
  deckId: number;
  mode: PracticeMode;
  shuffle: boolean;
  questionCount: number | null;
  gradeAtEnd: boolean;
  timeLimit: number | null;
}) {
  const navigate = useNavigate();
  const { token, can, isEnabled } = useAuth();
  const isGuest = !token;
  const [reloadToken, setReloadToken] = useState(0);
  const [data, setData] = useState<LoadedPractice | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loadedToken, setLoadedToken] = useState(-1);
  const loading = loadedToken !== reloadToken;
  // Resolves to [session, deck, effectiveShuffle, effectiveSeed]: signed-in runs take the server's
  // stored shuffle/seed (resume-authoritative); guests use the URL choice + a seed minted once here.
  const loadRef = useRef<{
    key: string;
    // ...plus the effective question count (null = whole deck, FLA-219), grade-at-end flag (#293), and
    // timed deadline (epoch millis, null = untimed, #289).
    promise: Promise<
      [PracticeSessionDto | null, FlashcardDeckDto, boolean, number, number | null, boolean, number | null]
    >;
  } | null>(null);

  // Latest in-memory progress, mirrored here so the back/close guard can read it without re-rendering.
  const progressRef = useRef<{ progress: Progress; status: 'practicing' | 'completed' }>({
    progress: ZERO_PROGRESS,
    status: 'practicing',
  });
  const [guestHasUnsaved, setGuestHasUnsaved] = useState(false);
  // Non-null while the "save your progress?" prompt is open; holds the snapshot to persist on signup.
  const [savePromptProgress, setSavePromptProgress] = useState<Progress | null>(null);
  // Whether the run has finished (results shown). Drives the single-sitting exit guard (#307): while a
  // timed / grade-at-the-end run is still in progress there's no back button + leaving is warned.
  const [runCompleted, setRunCompleted] = useState(false);

  useEffect(() => {
    let active = true;
    const key = `${deckId}:${mode.key}:${reloadToken}:${isGuest}:${shuffle}:${questionCount}:${gradeAtEnd}:${timeLimit}`;
    if (loadRef.current?.key !== key) {
      // Guests load the deck from the public catalog and never create a session; signed-in users
      // create/resume a server session (which carries any existing progress for this deck + mode).
      // A guest's shuffle seed is minted once here (in the cached promise) so re-renders don't re-roll
      // the order (the FLA-159 lesson); signed-in runs use the server's stored, resume-stable seed.
      // The timed deadline (#289) is likewise fixed once: a guest's from the mint time, a signed-in
      // run's from the session's stored createdAt (resume-stable — the clock keeps running while away).
      const promise: Promise<
        [PracticeSessionDto | null, FlashcardDeckDto, boolean, number, number | null, boolean, number | null]
      > = isGuest
        ? api
            .getCatalogDeck(deckId)
            .then((deck) => [
              null,
              deck,
              shuffle,
              shuffle ? newGuestSeed() : 0,
              questionCount,
              gradeAtEnd,
              timeLimit != null ? Date.now() + timeLimit * 1000 : null,
            ])
        : Promise.all([
            api.createSession(deckId, mode.key, shuffle, questionCount, gradeAtEnd, timeLimit),
            api.getDeck(deckId),
          ]).then(
            // Resume-authoritative: the server's stored choices win over the URL (an old link can't change a run).
            ([session, deck]) => [
              session,
              deck,
              session.shuffle,
              session.shuffleSeed,
              session.questionCount,
              session.gradeAtEnd,
              session.timeLimitSeconds != null ? session.createdAtMillis + session.timeLimitSeconds * 1000 : null,
            ],
          );
      loadRef.current = { key, promise };
    }
    loadRef.current.promise
      .then(async ([session, deck, effectiveShuffle, effectiveSeed, effectiveCount, effectiveGradeAtEnd, effectiveDeadline]) => {
        if (!active) return;
        if (deck.flashcards.length === 0) {
          setError('This deck has no cards to practice.');
        } else {
          setError(null);
          // On resume (a signed-in session that already has progress), restore the in-session streak
          // from the answer log — the trailing run of consecutive corrects (FLA-99). A fresh session
          // has no log, so it stays at 0.
          let initialStreak = 0;
          if (session != null && (session.currentCardIndex > 0 || session.numCorrect > 0 || session.numIncorrect > 0)) {
            const answers = await api.getAnswers(session.id).catch(() => []);
            initialStreak = trailingCorrectStreak([...answers].sort((a, b) => a.sequence - b.sequence).map((a) => a.correct));
          }
          if (!active) return;
          setData({
            sessionId: session?.id ?? null,
            deckTitle: deck.title,
            // Apply the (stable) shuffle order once at load, then take the subset (FLA-219); downstream
            // matches cards by cardUid. `?? length` avoids slice(0, null) → []; slice clamps if count > size.
            cards: orderCards(deck.flashcards, effectiveShuffle, effectiveSeed).slice(0, effectiveCount ?? deck.flashcards.length),
            progress: session ?? ZERO_PROGRESS,
            initialStreak,
            discussionsEnabled: deck.discussionsEnabled ?? false,
            isGlobal: deck.isGlobal ?? false,
            gradeAtEnd: effectiveGradeAtEnd,
            deadline: effectiveDeadline,
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
  }, [deckId, mode.key, reloadToken, isGuest, shuffle, questionCount, gradeAtEnd, timeLimit]);

  const onProgress = useCallback(
    (progress: Progress, status: 'practicing' | 'completed') => {
      progressRef.current = { progress, status };
      const touched = progress.currentCardIndex > 0 || progress.numCorrect > 0 || progress.numIncorrect > 0;
      setGuestHasUnsaved(isGuest && status === 'practicing' && touched);
      setRunCompleted(status === 'completed');
    },
    [isGuest],
  );

  // "Practice again" reloads a fresh run — re-arm the guard + reload.
  const practiceAgain = () => {
    setRunCompleted(false);
    setReloadToken((t) => t + 1);
  };

  // A single-sitting run (timed / grade-at-the-end, #306) isn't saved, so while it's in progress hide
  // the back button and warn on any exit attempt (#307). Off once it completes (results shown).
  const isSingleSitting = data != null && (data.gradeAtEnd || data.deadline != null);
  const guardActive = isSingleSitting && !runCompleted;
  useExitGuard(guardActive, 'Leaving now ends this session and your progress won’t be saved. Leave anyway?');

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

  // "Back" returns to where practice was launched from (FLA-168): the referrer is passed in router
  // state by the entry points (Home / Library), so the exit mirrors the native back-stack.
  const location = useLocation();
  const exit = exitTarget(fromState(location.state), isGuest);

  const requestExit = () => {
    const { progress, status } = progressRef.current;
    const touched = progress.currentCardIndex > 0 || progress.numCorrect > 0 || progress.numIncorrect > 0;
    if (isGuest && status === 'practicing' && touched) {
      setSavePromptProgress(progress);
    } else {
      navigate(exit.to);
    }
  };

  return (
    <div className="app">
      <BackHeader
        title={(!loading && data?.deckTitle) || 'Practice'}
        backLabel={exit.label}
        onBack={requestExit}
        hideBack={guardActive}
        right={<ShareButton deckId={deckId} title={data?.deckTitle} className="link-btn" />}
      />
      <main className="container">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : error ? (
          <p className="error">{error}</p>
        ) : !data ? (
          <p className="muted">Loading…</p>
        ) : data.gradeAtEnd ? (
          // Grade-at-the-end (#293): answer all cards in a list, then submit to grade. No card-by-card
          // progress/streak/persistence-per-card — the batch runner records the whole batch on Submit.
          <BatchPracticeRunner
            key={reloadToken}
            sessionId={data.sessionId}
            cards={data.cards}
            mode={mode}
            deadline={data.deadline}
            onCompleted={() => setRunCompleted(true)}
            onAgain={practiceAgain}
            onExit={() => navigate(exit.to)}
          />
        ) : (
          <PracticeRunner
            key={reloadToken}
            sessionId={data.sessionId}
            cards={data.cards}
            progress={data.progress}
            initialStreak={data.initialStreak}
            mode={mode}
            // Gate the discuss surface on the `discussions` feature flag (FLA-180). Guests carry no
            // flags, so keep their (read-only) discussions unflagged rather than hiding them.
            discussionsEnabled={data.discussionsEnabled && (isGuest || isEnabled('discussions'))}
            isGlobal={data.isGlobal}
            isGuest={isGuest}
            canModerate={can('manage_discussions')}
            deadline={data.deadline}
            onProgress={onProgress}
            onAgain={practiceAgain}
            onExit={() => navigate(exit.to)}
          />
        )}
      </main>

      {savePromptProgress && (
        <SavePrompt
          deckId={deckId}
          mode={mode.key}
          shuffle={shuffle}
          questionCount={questionCount}
          progress={savePromptProgress}
          onCancel={() => setSavePromptProgress(null)}
          onLeave={() => navigate(exit.to)}
        />
      )}
    </div>
  );
}

interface PracticeRunnerProps {
  sessionId: number | null;
  cards: FlashcardDto[];
  progress: Progress;
  initialStreak: number;
  mode: PracticeMode;
  discussionsEnabled: boolean;
  isGlobal: boolean;
  isGuest: boolean;
  canModerate: boolean;
  // Wall-clock deadline (epoch millis) for a timed session (#289), or null when untimed.
  deadline: number | null;
  onProgress: (progress: Progress, status: 'practicing' | 'completed') => void;
  onAgain: () => void;
  onExit: () => void;
}

// Mode-agnostic session loop: owns progress/score (reducer), best-effort persistence (signed-in
// only), and the completion summary. The current mode renders the card and reports each outcome.
function PracticeRunner({
  sessionId,
  cards,
  progress,
  initialStreak,
  mode,
  discussionsEnabled,
  isGlobal,
  isGuest,
  canModerate,
  deadline,
  onProgress,
  onAgain,
  onExit,
}: PracticeRunnerProps) {
  const [state, dispatch] = useReducer(practiceReducer, initPractice(cards, progress, initialStreak));
  // Timed session (#289): count down to the deadline; on expiry, end the run wherever it is.
  const { remainingMs, expired } = useCountdown(deadline);
  // Overall streak after this completion (FLA-106); null until loaded / for guests (no session).
  const [streak, setStreak] = useState<number | null>(null);
  // The session's answer log for the end-of-session review (FLA-149); null until the session
  // completes (or for guests, who have no session).
  const [review, setReview] = useState<PracticeAnswer[] | null>(null);
  // The card whose discussion panel is open (FLA-116), or null when closed.
  const [discussCardUid, setDiscussCardUid] = useState<string | null>(null);

  // Mirror progress up so the parent's leave-guard can read it (and persist on guest save).
  useEffect(() => {
    onProgress({ currentCardIndex: state.index, numCorrect: state.numCorrect, numIncorrect: state.numIncorrect }, state.status);
  }, [state, onProgress]);

  // Append this answer to the session's log (FLA-99) — backs the in-session streak + an end-of-session
  // review. sequence = answers recorded so far (0-based play order). Best-effort, signed-in only.
  const recordAnswer = useCallback(
    (card: FlashcardDto, correct: boolean, submittedText?: string) => {
      if (sessionId == null || !card.cardUid) return;
      api
        .recordAnswers(sessionId, [
          {
            answerUid: crypto.randomUUID(),
            cardUid: card.cardUid,
            correct,
            sequence: state.numCorrect + state.numIncorrect,
            answeredAtMillis: Date.now(),
            submittedText: submittedText ?? null,
          },
        ])
        .catch(() => {});
    },
    [state, sessionId],
  );

  // Complete the session, then read the daily streak + the answer log only after completion lands —
  // so the streak reflects the day just earned and the review includes the final answer (FLA-149).
  // Used both when the last card finishes and when a timed run expires mid-session (#289). Best-effort.
  const completeNow = useCallback(() => {
    if (sessionId == null) return;
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
    api
      .completeSession(sessionId, tz)
      .then(() => Promise.all([api.getStreaks(tz), api.getAnswers(sessionId)]))
      .then(([s, answers]) => {
        setStreak(s.overall.current);
        setReview(answers);
      })
      .catch(() => {});
  }, [sessionId]);

  // Persist the session aggregate after a card is finished — or complete it on the last card.
  // Best-effort; never blocks the UI.
  const persistProgress = useCallback(
    (wasLast: boolean, nextIndex: number, numCorrect: number, numIncorrect: number) => {
      if (sessionId == null) return;
      if (wasLast) {
        completeNow();
      } else {
        api.updateProgress(sessionId, { currentCardIndex: nextIndex, numCorrect, numIncorrect }).catch(() => {});
      }
    },
    [sessionId, completeNow],
  );

  // Timed session (#289): when the countdown expires, end the run wherever it is + complete it.
  useEffect(() => {
    if (expired && state.status === 'practicing') {
      dispatch({ type: 'EXPIRE' });
      completeNow();
    }
  }, [expired, state.status, completeNow]);

  // Classic: grade + advance in one motion (its swipe has no verdict to dwell on). State is stale
  // this tick, so the persisted score is computed from `correct`.
  const mark = useCallback(
    (correct: boolean, submittedText?: string) => {
      if (state.status !== 'practicing') return;
      const wasLast = state.index >= state.cards.length - 1;
      recordAnswer(state.cards[state.index], correct, submittedText);
      dispatch({ type: 'GRADE', correct });
      dispatch({ type: 'ADVANCE' });
      persistProgress(
        wasLast,
        state.index + 1,
        state.numCorrect + (correct ? 1 : 0),
        state.numIncorrect + (correct ? 0 : 1),
      );
    },
    [state, recordAnswer, persistProgress],
  );

  // Test/Multiple-Choice grade when the verdict is revealed — score + streak update on the answer
  // itself (the badge appears here), without advancing.
  const gradeCard = useCallback(
    (correct: boolean, submittedText?: string) => {
      if (state.status !== 'practicing') return;
      recordAnswer(state.cards[state.index], correct, submittedText);
      dispatch({ type: 'GRADE', correct });
    },
    [state, recordAnswer],
  );

  // …then advance on "Next". The GRADE has re-rendered, so `state` already holds the post-grade score.
  const advanceCard = useCallback(() => {
    if (state.status !== 'practicing') return;
    const wasLast = state.index >= state.cards.length - 1;
    dispatch({ type: 'ADVANCE' });
    persistProgress(wasLast, state.index + 1, state.numCorrect, state.numIncorrect);
  }, [state, persistProgress]);

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
        {streak != null && streak > 0 && (
          <p className="streak-badge" title="Days in a row with a completed practice">
            🔥 {streak} day streak
          </p>
        )}
        {/* Per-card recap of the run (FLA-149): each logged answer in play order, joined to its card. */}
        {review && review.length > 0 && (
          <div className="review">
            <h3 className="review-heading">Review</h3>
            <ul className="review-list">
              {[...review]
                .sort((a, b) => a.sequence - b.sequence)
                .map((ans) => {
                  const card = cards.find((c) => !!c.cardUid && c.cardUid === ans.cardUid);
                  return (
                    <li key={ans.answerUid} className={`review-item ${ans.correct ? 'correct' : 'incorrect'}`}>
                      <span className="review-outcome" aria-label={ans.correct ? 'correct' : 'incorrect'}>
                        {ans.correct ? '✓' : '✗'}
                      </span>
                      {card?.imageUrl && <img src={card.imageUrl} alt="" className="review-image" />}
                      <div className="review-text">
                        {card?.question && <span className="review-prompt">{card.question}</span>}
                        <span className="review-answer">{card?.answer ?? '—'}</span>
                        {ans.submittedText && <span className="review-submitted">You answered: {ans.submittedText}</span>}
                      </div>
                    </li>
                  );
                })}
            </ul>
          </div>
        )}
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
  const currentCard = state.cards[state.index];
  // Discussions need a stable cardUid (FLA-113) and the deck opted in (FLA-116).
  const canDiscuss = discussionsEnabled && !!currentCard.cardUid;
  return (
    <div className="practice">
      {/* Timed session (#289): a live m:ss countdown, urgent styling in the last 10s. */}
      {deadline != null && (
        <div className="practice-timer-row">
          <span
            className={`practice-timer${remainingMs <= 10000 ? ' urgent' : ''}`}
            aria-label="time remaining"
          >
            ⏱ {formatRemaining(remainingMs)}
          </span>
        </div>
      )}
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

      {/* Live in-session streak (FLA-99): appears at 2+ in a row, with milestone emphasis at 5+. */}
      {state.streak >= 2 && (
        <div className="session-streak-row">
          <span
            className={`session-streak${state.streak >= 5 ? ' hot' : ''}`}
            aria-label={`${state.streak} correct in a row`}
          >
            🔥 {state.streak} in a row
          </span>
        </div>
      )}

      {/* Keyed by index so each card gets a fresh mode instance (flip/input/selection reset). */}
      <ModeComponent
        key={state.index}
        card={currentCard}
        cards={state.cards}
        onResult={mark}
        onGraded={gradeCard}
        onAdvance={advanceCard}
        onDiscuss={canDiscuss ? () => setDiscussCardUid(currentCard.cardUid ?? null) : undefined}
        canSuggest={isGlobal && !!currentCard.cardUid}
        isGuest={isGuest}
      />

      {discussCardUid && (
        <DiscussionPanel
          cardUid={discussCardUid}
          isGuest={isGuest}
          canModerate={canModerate}
          onClose={() => setDiscussCardUid(null)}
        />
      )}
    </div>
  );
}
