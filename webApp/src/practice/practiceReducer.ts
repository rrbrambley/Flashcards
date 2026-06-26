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

export type PracticeAction = { type: 'MARK_CORRECT' } | { type: 'MARK_INCORRECT' };

/** Seeds state from a deck's cards and a session (resumes at currentCardIndex with its counts). */
export function initPractice(
  cards: FlashcardDto[],
  session: Pick<PracticeSessionDto, 'currentCardIndex' | 'numCorrect' | 'numIncorrect'>,
): PracticeState {
  const lastIndex = Math.max(0, cards.length - 1);
  return {
    cards,
    index: Math.min(Math.max(session.currentCardIndex, 0), lastIndex),
    numCorrect: session.numCorrect,
    numIncorrect: session.numIncorrect,
    // The in-session streak is ephemeral per run: a resumed session starts fresh at 0.
    streak: 0,
    status: 'practicing',
  };
}

export function practiceReducer(state: PracticeState, action: PracticeAction): PracticeState {
  if (state.status === 'completed') return state;
  const correct = action.type === 'MARK_CORRECT';
  const marked: PracticeState = {
    ...state,
    numCorrect: state.numCorrect + (correct ? 1 : 0),
    numIncorrect: state.numIncorrect + (correct ? 0 : 1),
    streak: correct ? state.streak + 1 : 0,
  };
  // Marking the last card completes the session; otherwise advance.
  return state.index >= state.cards.length - 1
    ? { ...marked, status: 'completed' }
    : { ...marked, index: state.index + 1 };
}
