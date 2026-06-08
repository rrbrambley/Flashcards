import { describe, it, expect } from 'vitest';
import { buildChoices } from './multipleChoice';
import type { FlashcardDto } from '../../api/types';

const deck: FlashcardDto[] = [
  { question: 'France', answer: 'Paris' },
  { question: 'Japan', answer: 'Tokyo' },
  { question: 'Italy', answer: 'Rome' },
  { question: 'Spain', answer: 'Madrid' },
  { question: 'Germany', answer: 'Berlin' },
];

// Deterministic RNG (always 0) → a fixed shuffle, so results are stable across runs.
const fixed = () => 0;

describe('buildChoices', () => {
  it('returns up to 4 options including the correct answer', () => {
    const choices = buildChoices(deck[0], deck, 4, fixed);
    expect(choices.length).toBe(4);
    expect(choices).toContain('Paris');
  });

  it('draws unique distractors from other cards, never the correct answer', () => {
    const choices = buildChoices(deck[0], deck, 4, fixed);
    const distractors = choices.filter((c) => c !== 'Paris');
    expect(distractors).toHaveLength(3);
    for (const d of distractors) {
      expect(['Tokyo', 'Rome', 'Madrid', 'Berlin']).toContain(d);
    }
    expect(new Set(choices).size).toBe(choices.length); // no duplicates
  });

  it('de-duplicates case-insensitively and skips blank answers', () => {
    const dupes: FlashcardDto[] = [
      { question: 'A', answer: 'Paris' }, // correct
      { question: 'B', answer: 'paris' }, // duplicate of correct (case)
      { question: 'C', answer: '   ' }, // blank
      { question: 'D', answer: 'Tokyo' },
      { question: 'E', answer: 'tokyo' }, // duplicate distractor
    ];
    const choices = buildChoices(dupes[0], dupes, 4, fixed);
    expect(choices).toContain('Paris');
    expect(choices.filter((c) => c !== 'Paris')).toEqual(['Tokyo']);
  });

  it('yields fewer than 4 options for a small deck', () => {
    const small: FlashcardDto[] = [
      { question: 'France', answer: 'Paris' },
      { question: 'Japan', answer: 'Tokyo' },
    ];
    expect(buildChoices(small[0], small, 4, fixed).slice().sort()).toEqual(['Paris', 'Tokyo']);
  });

  it('is deterministic with a seeded RNG', () => {
    expect(buildChoices(deck[0], deck, 4, fixed)).toEqual(buildChoices(deck[0], deck, 4, fixed));
  });
});
