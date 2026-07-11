import { describe, it, expect } from 'vitest';
import { trailingCorrectStreak } from './streak';

describe('trailingCorrectStreak', () => {
  it('is zero for an empty log', () => {
    expect(trailingCorrectStreak([])).toBe(0);
  });

  it('is the full count when every answer is correct', () => {
    expect(trailingCorrectStreak([true, true, true])).toBe(3);
  });

  it('is zero when the log ends on a miss', () => {
    expect(trailingCorrectStreak([true, true, false])).toBe(0);
  });

  it('counts only the trailing run, not earlier corrects', () => {
    expect(trailingCorrectStreak([true, false, true, true])).toBe(2);
  });

  it('handles single-answer logs', () => {
    expect(trailingCorrectStreak([true])).toBe(1);
    expect(trailingCorrectStreak([false])).toBe(0);
  });
});
