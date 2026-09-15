import { describe, it, expect } from 'vitest';
import { spokenNumberVariants } from './spokenNumbers';
import { pickSpokenAnswer } from './textAnswer';

/** Spoken numbers written as digits so a correctly spoken numeric answer can grade correct (#390). */
describe('spokenNumberVariants', () => {
  it('concatenates years read as two groups', () => {
    // The motivating case: arithmetic would give 99, which is not what anyone said.
    expect(spokenNumberVariants('nineteen eighty')).toContain('1980');
    expect(spokenNumberVariants('nineteen eighty four')).toContain('1984');
    expect(spokenNumberVariants('twenty twenty four')).toContain('2024');
    expect(spokenNumberVariants('nineteen oh five')).toContain('1905');
  });

  it('uses the arithmetic reading for plain numbers', () => {
    expect(spokenNumberVariants('twenty three')).toContain('23');
    expect(spokenNumberVariants('one hundred')).toContain('100');
    expect(spokenNumberVariants('one hundred and five')).toContain('105');
    expect(spokenNumberVariants('two thousand')).toContain('2000');
    expect(spokenNumberVariants('three million')).toContain('3000000');
  });

  it('rules out the concatenated reading when a scale word is present', () => {
    // "two thousand" is 2000, never "2" then "1000".
    expect(spokenNumberVariants('two thousand')).toEqual(['2000']);
  });

  it('keeps the words around a number', () => {
    expect(spokenNumberVariants('the year nineteen eighty')).toContain('the year 1980');
    expect(spokenNumberVariants('twenty three skidoo')).toContain('23 skidoo');
  });

  it('offers both readings when they differ', () => {
    const variants = spokenNumberVariants('nineteen eighty');
    expect(variants).toContain('1980'); // concatenated
    expect(variants).toContain('99'); // arithmetic
  });

  it('produces nothing for a transcript without number words', () => {
    expect(spokenNumberVariants('paris')).toEqual([]);
    expect(spokenNumberVariants('')).toEqual([]);
    expect(spokenNumberVariants('the quick brown fox')).toEqual([]);
  });
});

describe('pickSpokenAnswer', () => {
  it('matches a digit answer from spoken words', () => {
    // The whole point: this is graded wrong today.
    expect(pickSpokenAnswer(['nineteen eighty'], '1980')).toBe('1980');
    expect(pickSpokenAnswer(['twenty twenty four'], '2024')).toBe('2024');
    expect(pickSpokenAnswer(['twenty three'], '23')).toBe('23');
  });

  it('prefers the transcript as heard', () => {
    // A word answer must still win on the words, and be recorded as spoken — the digit variant
    // ("1 piece") is only ever reached when every original has already failed.
    expect(pickSpokenAnswer(['one piece'], 'One Piece')).toBe('one piece');
    expect(pickSpokenAnswer(['one'], 'one')).toBe('one');
  });

  it('still falls back to the top hypothesis', () => {
    expect(pickSpokenAnswer(['banana', 'bandana'], '1980')).toBe('banana');
    expect(pickSpokenAnswer(['seventeen'], 'Paris')).toBe('seventeen');
  });

  it('tries every hypothesis as heard before converting any', () => {
    expect(pickSpokenAnswer(['nineteen eighty', 'paris'], 'Paris')).toBe('paris');
  });

  it('matches an alternative answer too', () => {
    expect(pickSpokenAnswer(['nineteen eighty'], 'MCMLXXX', ['1980'])).toBe('1980');
  });

  it('returns undefined when there are no hypotheses', () => {
    expect(pickSpokenAnswer([], '1980')).toBeUndefined();
  });
});
