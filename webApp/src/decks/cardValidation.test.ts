import { describe, it, expect } from 'vitest';
import { isComplete, isStarted, type CardFields } from './cardValidation';

const card = (over: Partial<CardFields> = {}): CardFields => ({
  term: '',
  definition: '',
  imageUrl: null,
  ...over,
});

describe('cardValidation', () => {
  it('an empty card is neither started nor complete', () => {
    expect(isStarted(card())).toBe(false);
    expect(isComplete(card())).toBe(false);
  });

  it('a term alone is started but not complete', () => {
    expect(isStarted(card({ term: 'Hola' }))).toBe(true);
    expect(isComplete(card({ term: 'Hola' }))).toBe(false);
  });

  it('a definition alone is started but not complete', () => {
    expect(isComplete(card({ definition: 'Hello' }))).toBe(false);
  });

  it('term + definition is complete', () => {
    expect(isComplete(card({ term: 'Hola', definition: 'Hello' }))).toBe(true);
  });

  it('image + definition is complete even without a term', () => {
    expect(isComplete(card({ definition: 'Canada', imageUrl: 'https://cdn/flag.png' }))).toBe(true);
  });

  it('an image alone is started but not complete', () => {
    const c = card({ imageUrl: 'https://cdn/flag.png' });
    expect(isStarted(c)).toBe(true);
    expect(isComplete(c)).toBe(false);
  });

  it('whitespace does not count as content', () => {
    const c = card({ term: '   ', definition: '   ' });
    expect(isStarted(c)).toBe(false);
    expect(isComplete(c)).toBe(false);
  });
});
