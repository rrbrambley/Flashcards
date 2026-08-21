import type { FlashcardDto, PracticeSessionDto } from '../api/types';

// Mode-agnostic session progress: index, score, and completion. Anything mode-specific (a flip, a
// typed answer, a selected choice) lives in the mode component, not here.
export interface PracticeState {
  cards: FlashcardDto[];
  index: number;
  numCorrect: number;
  numIncorrect: number;
  // Current consecutive-correct run within this session (FLA-99): grows on each correct, resets to
  // 0 on a miss. In-session only (distinct from the daily practice streak, FLA-106).
  streak: number;
  // 'timeUp' sits between the two (#375): the countdown expired on a card, which is held with its
  // answer revealed before the recap — otherwise the question the user was working on is the one whose
  // answer they never see. CONTINUE leaves it.
  status: 'practicing' | 'timeUp' | 'completed';
  // Whether the card at [index] has already been scored. Test/Multiple-Choice GRADE when the verdict
  // is revealed and only ADVANCE on "Next", so the runner sits on an *answered* card for as long as
  // the user reads it. Without this, a countdown expiring in that window records the same card a
  // second time as unanswered-and-wrong (#398).
  currentCardGraded: boolean;
}

// GRADE scores the current card (and the in-session streak) without moving on, so the streak badge
// surfaces on the revealed answer; ADVANCE moves to the next card (or completes). Classic dispatches
// both together (its swipe grades and advances at once); Test/Multiple-Choice GRADE on the verdict,
// then ADVANCE on Next. EXPIRE ends the run wherever it is — the timed-session countdown hitting 0 (#289).
export type PracticeAction =
  | { type: 'GRADE'; correct: boolean }
  | { type: 'ADVANCE' }
  | { type: 'EXPIRE' }
  | { type: 'TIME_UP' }
  | { type: 'CONTINUE' };

/** Seeds state from a deck's cards and a session (resumes at currentCardIndex with its counts). */
export function initPractice(
  cards: FlashcardDto[],
  session: Pick<PracticeSessionDto, 'currentCardIndex' | 'numCorrect' | 'numIncorrect'>,
  // The in-session streak (FLA-99) restored from the answer log on resume — a derived value, not a
  // persisted one (see grading/streak.ts). Defaults to 0 for a fresh session / empty log.
  initialStreak = 0,
): PracticeState {
  const lastIndex = Math.max(0, cards.length - 1);
  return {
    cards,
    index: Math.min(Math.max(session.currentCardIndex, 0), lastIndex),
    numCorrect: session.numCorrect,
    numIncorrect: session.numIncorrect,
    streak: initialStreak,
    status: 'practicing',
    currentCardGraded: false,
  };
}

export function practiceReducer(state: PracticeState, action: PracticeAction): PracticeState {
  if (state.status === 'completed') return state;
  // During the reveal (#375) the run is over: only CONTINUE applies, so a late GRADE/ADVANCE — a
  // Classic swipe already in flight, say — can't score again or move the index behind it.
  if (state.status === 'timeUp') {
    return action.type === 'CONTINUE' ? { ...state, status: 'completed' } : state;
  }
  // Two ways a timed run ends (#289). EXPIRE ends it outright — used when the deadline had already
  // passed before play began, i.e. resuming a run whose time ran out while away: there's no card the
  // user was working on, so there's nothing to reveal and nothing to mark wrong.
  if (action.type === 'EXPIRE') return { ...state, status: 'completed' };
  // TIME_UP is the clock running out *during* play: hold the card that was up so its answer can be
  // shown (#375). It counts as a miss — it went unanswered — which is also what puts it in the recap,
  // since that list is built from recorded answers.
  if (action.type === 'TIME_UP') {
    // Only counts as a miss if the card was actually unanswered. Expiring while the user reads a
    // verdict they already earned must not re-score it — GRADE has been here, and taking the miss
    // anyway both double-counts the card and wipes a streak the answer had just extended (#398).
    if (state.currentCardGraded) return { ...state, status: 'timeUp' };
    return { ...state, status: 'timeUp', numIncorrect: state.numIncorrect + 1, streak: 0 };
  }
  if (action.type === 'GRADE') {
    // Score + streak, staying on the current card so the badge shows on the revealed answer.
    return {
      ...state,
      numCorrect: state.numCorrect + (action.correct ? 1 : 0),
      numIncorrect: state.numIncorrect + (action.correct ? 0 : 1),
      streak: action.correct ? state.streak + 1 : 0,
      currentCardGraded: true,
    };
  }
  // ADVANCE: move to the next card, or complete after the last.
  return state.index >= state.cards.length - 1
    ? { ...state, status: 'completed' }
    : { ...state, index: state.index + 1, currentCardGraded: false };
}
