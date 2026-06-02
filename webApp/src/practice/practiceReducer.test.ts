import { describe, it, expect } from 'vitest';
import { initPractice, practiceReducer, type PracticeState } from './practiceReducer';
import type { FlashcardDto } from '../api/types';

const cards: FlashcardDto[] = [
  { question: 'Q1', answer: 'A1' },
  { question: 'Q2', answer: 'A2' },
  { question: 'Q3', answer: 'A3' },
];

const fresh = (): PracticeState =>
  initPractice(cards, { currentCardIndex: 0, numCorrect: 0, numIncorrect: 0 });

describe('initPractice', () => {
  it('starts at the front of the first card', () => {
    const s = fresh();
    expect(s).toMatchObject({ index: 0, numCorrect: 0, numIncorrect: 0, isFlipped: false, status: 'practicing' });
  });

  it('resumes at the session index with restored counts', () => {
    const s = initPractice(cards, { currentCardIndex: 1, numCorrect: 2, numIncorrect: 1 });
    expect(s).toMatchObject({ index: 1, numCorrect: 2, numIncorrect: 1 });
  });

  it('clamps an out-of-range index into the card range', () => {
    expect(initPractice(cards, { currentCardIndex: 99, numCorrect: 0, numIncorrect: 0 }).index).toBe(2);
    expect(initPractice(cards, { currentCardIndex: -5, numCorrect: 0, numIncorrect: 0 }).index).toBe(0);
  });
});

describe('practiceReducer', () => {
  it('FLIP toggles the face', () => {
    const flipped = practiceReducer(fresh(), { type: 'FLIP' });
    expect(flipped.isFlipped).toBe(true);
    expect(practiceReducer(flipped, { type: 'FLIP' }).isFlipped).toBe(false);
  });

  it('MARK_CORRECT increments correct, advances, and resets the flip', () => {
    const s = practiceReducer({ ...fresh(), isFlipped: true }, { type: 'MARK_CORRECT' });
    expect(s).toMatchObject({ numCorrect: 1, numIncorrect: 0, index: 1, isFlipped: false, status: 'practicing' });
  });

  it('MARK_INCORRECT increments incorrect and advances', () => {
    const s = practiceReducer(fresh(), { type: 'MARK_INCORRECT' });
    expect(s).toMatchObject({ numCorrect: 0, numIncorrect: 1, index: 1 });
  });

  it('marking the last card completes the session (no further advance)', () => {
    const onLast: PracticeState = { ...fresh(), index: 2, numCorrect: 2 };
    const s = practiceReducer(onLast, { type: 'MARK_CORRECT' });
    expect(s).toMatchObject({ status: 'completed', index: 2, numCorrect: 3 });
  });

  it('ignores marks once completed', () => {
    const completed: PracticeState = { ...fresh(), status: 'completed', numCorrect: 3 };
    expect(practiceReducer(completed, { type: 'MARK_CORRECT' })).toBe(completed);
  });
});
