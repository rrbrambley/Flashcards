import { describe, it, expect } from 'vitest';
import { gradeTextAnswer } from './textAnswer';

describe('gradeTextAnswer', () => {
  it('accepts an exact match', () => {
    expect(gradeTextAnswer('Paris', 'Paris')).toMatchObject({ correct: true, similarity: 1 });
  });

  it('ignores case and surrounding/collapsing whitespace', () => {
    expect(gradeTextAnswer('  pARiS ', 'Paris').correct).toBe(true);
    expect(gradeTextAnswer('new   york', 'New York').correct).toBe(true);
  });

  it('accepts a typo within tolerance', () => {
    // One missing letter in an 11-char word → ~0.91 similarity, above the 0.85 threshold.
    const { correct, similarity } = gradeTextAnswer('Mississipi', 'Mississippi');
    expect(similarity).toBeGreaterThanOrEqual(0.85);
    expect(correct).toBe(true);
  });

  it('rejects an answer below tolerance', () => {
    expect(gradeTextAnswer('cat', 'dog').correct).toBe(false);
    expect(gradeTextAnswer('Berlin', 'Paris').correct).toBe(false);
  });

  it('rejects an empty answer', () => {
    expect(gradeTextAnswer('', 'Paris')).toMatchObject({ correct: false, similarity: 0 });
  });
});
