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
    expect(s).toMatchObject({ index: 0, numCorrect: 0, numIncorrect: 0, status: 'practicing' });
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
  it('GRADE scores the current card without advancing', () => {
    const s = practiceReducer(fresh(), { type: 'GRADE', correct: true });
    expect(s).toMatchObject({ numCorrect: 1, numIncorrect: 0, index: 0, status: 'practicing' });
  });

  it('GRADE incorrect scores a miss without advancing', () => {
    const s = practiceReducer(fresh(), { type: 'GRADE', correct: false });
    expect(s).toMatchObject({ numCorrect: 0, numIncorrect: 1, index: 0 });
  });

  it('ADVANCE moves to the next card without changing the score', () => {
    const graded = practiceReducer(fresh(), { type: 'GRADE', correct: true });
    const s = practiceReducer(graded, { type: 'ADVANCE' });
    expect(s).toMatchObject({ numCorrect: 1, numIncorrect: 0, index: 1, status: 'practicing' });
  });

  it('ADVANCE past the last card completes the session', () => {
    const onLast: PracticeState = { ...fresh(), index: 2, numCorrect: 3 };
    const s = practiceReducer(onLast, { type: 'ADVANCE' });
    expect(s).toMatchObject({ status: 'completed', index: 2, numCorrect: 3 });
  });

  it('ignores actions once completed', () => {
    const completed: PracticeState = { ...fresh(), status: 'completed', numCorrect: 3 };
    expect(practiceReducer(completed, { type: 'GRADE', correct: true })).toBe(completed);
    expect(practiceReducer(completed, { type: 'ADVANCE' })).toBe(completed);
  });

  it('grows the streak on consecutive correct and resets it on a miss (FLA-99)', () => {
    let s = initPractice(cards, { currentCardIndex: 0, numCorrect: 0, numIncorrect: 0 });
    expect(s.streak).toBe(0);
    s = practiceReducer(s, { type: 'GRADE', correct: true });
    expect(s.streak).toBe(1);
    s = practiceReducer(s, { type: 'GRADE', correct: true });
    expect(s.streak).toBe(2);
    s = practiceReducer(s, { type: 'GRADE', correct: false });
    expect(s.streak).toBe(0);
    s = practiceReducer(s, { type: 'GRADE', correct: true });
    expect(s.streak).toBe(1);
  });

  it('starts a resumed session at streak 0', () => {
    expect(initPractice(cards, { currentCardIndex: 1, numCorrect: 2, numIncorrect: 1 }).streak).toBe(0);
  });

  it('ends outright when the deadline had already passed before play began (#289)', () => {
    const s = initPractice(cards, { currentCardIndex: 0, numCorrect: 0, numIncorrect: 0 });
    const done = practiceReducer(s, { type: 'EXPIRE' });
    // Resuming a run whose time ran out while away: no card was up, so nothing to reveal or mark wrong.
    expect(done.status).toBe('completed');
    expect(done.numIncorrect).toBe(0);
  });

  it('holds the expired card with its answer before completing, counting it as a miss (#375)', () => {
    const s = initPractice(cards, { currentCardIndex: 1, numCorrect: 1, numIncorrect: 0 });

    const expired = practiceReducer(s, { type: 'TIME_UP' });
    // Not 'completed': the card is held so its answer can be shown before the recap.
    expect(expired.status).toBe('timeUp');
    expect(expired.index).toBe(1);
    // It went unanswered, so it counts as a miss — which is also what puts it in the recap, built
    // from recorded answers.
    expect(expired.numIncorrect).toBe(1);
    expect(expired.streak).toBe(0);

    expect(practiceReducer(expired, { type: 'CONTINUE' }).status).toBe('completed');
  });

  it('ignores further grading once the clock has expired', () => {
    const expired = practiceReducer(
      initPractice(cards, { currentCardIndex: 0, numCorrect: 0, numIncorrect: 0 }),
      { type: 'TIME_UP' },
    );
    // GRADE/ADVANCE after expiry must not score again or move on — only CONTINUE applies.
    expect(practiceReducer(expired, { type: 'GRADE', correct: true }).numCorrect).toBe(0);
    expect(practiceReducer(expired, { type: 'ADVANCE' }).index).toBe(0);
  });
});
