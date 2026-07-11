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
  status: 'practicing' | 'completed';
}

// GRADE scores the current card (and the in-session streak) without moving on, so the streak badge
// surfaces on the revealed answer; ADVANCE moves to the next card (or completes). Classic dispatches
// both together (its swipe grades and advances at once); Test/Multiple-Choice GRADE on the verdict,
// then ADVANCE on Next.
export type PracticeAction = { type: 'GRADE'; correct: boolean } | { type: 'ADVANCE' };

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
  };
}

export function practiceReducer(state: PracticeState, action: PracticeAction): PracticeState {
  if (state.status === 'completed') return state;
  if (action.type === 'GRADE') {
    // Score + streak, staying on the current card so the badge shows on the revealed answer.
    return {
      ...state,
      numCorrect: state.numCorrect + (action.correct ? 1 : 0),
      numIncorrect: state.numIncorrect + (action.correct ? 0 : 1),
      streak: action.correct ? state.streak + 1 : 0,
    };
  }
  // ADVANCE: move to the next card, or complete after the last.
  return state.index >= state.cards.length - 1
    ? { ...state, status: 'completed' }
    : { ...state, index: state.index + 1 };
}
