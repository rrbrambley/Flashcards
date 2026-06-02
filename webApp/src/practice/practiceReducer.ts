import type { FlashcardDto, PracticeSessionDto } from '../api/types';

export interface PracticeState {
  cards: FlashcardDto[];
  index: number;
  numCorrect: number;
  numIncorrect: number;
  isFlipped: boolean;
  status: 'practicing' | 'completed';
}

export type PracticeAction = { type: 'FLIP' } | { type: 'MARK_CORRECT' } | { type: 'MARK_INCORRECT' };

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
    isFlipped: false,
    status: 'practicing',
  };
}

export function practiceReducer(state: PracticeState, action: PracticeAction): PracticeState {
  switch (action.type) {
    case 'FLIP':
      return { ...state, isFlipped: !state.isFlipped };
    case 'MARK_CORRECT':
    case 'MARK_INCORRECT': {
      if (state.status === 'completed') return state;
      const correct = action.type === 'MARK_CORRECT';
      const marked: PracticeState = {
        ...state,
        numCorrect: state.numCorrect + (correct ? 1 : 0),
        numIncorrect: state.numIncorrect + (correct ? 0 : 1),
        isFlipped: false,
      };
      // Marking the last card completes the session; otherwise advance.
      return state.index >= state.cards.length - 1
        ? { ...marked, status: 'completed' }
        : { ...marked, index: state.index + 1 };
    }
    default:
      return state;
  }
}
