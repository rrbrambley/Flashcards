import { describe, it, expect } from 'vitest';
import { matchSpokenChoice, VOICE_CHOICE_FLOOR } from './voiceChoice';
import { TEXT_ANSWER_THRESHOLD } from './textAnswer';

const CAPITALS = ['Paris', 'Berlin', 'Madrid', 'Rome'];

describe('matchSpokenChoice', () => {
  it('matches an option said exactly, whatever the casing', () => {
    expect(matchSpokenChoice('paris', CAPITALS)).toBe(0);
    expect(matchSpokenChoice('  ROME  ', CAPITALS)).toBe(3);
  });

  /**
   * The reason this doesn't reuse Test's threshold. "the eiffel tower" scores ≈0.76 against
   * "Eiffel Tower" — under 0.85, so a strict grader would reject an utterance that obviously names
   * the option on screen.
   */
  it('matches a natural spoken phrasing that Test-mode grading would reject', () => {
    const options = ['Eiffel Tower', 'Colosseum', 'Sagrada Familia', 'Brandenburg Gate'];
    expect(matchSpokenChoice('the eiffel tower', options)).toBe(0);
  });

  it('returns null when nothing is close enough, rather than guessing', () => {
    expect(matchSpokenChoice('banana', CAPITALS)).toBeNull();
  });

  /**
   * The margin rule, and the case it exists for. A clipped transcript can sit 0.75 from *two*
   * options at once — comfortably over the floor, so the floor alone would happily pick whichever
   * scored first and record a definite answer the user never said. A near-tie is ambiguity, not a
   * weak match, and there's no un-grading it afterwards.
   */
  it('returns null for a near-tie rather than picking a winner', () => {
    const countries = ['Iran', 'Iraq', 'Portugal', 'Vietnam'];
    // "ira" is equidistant from both — each is one character away.
    expect(matchSpokenChoice('ira', countries)).toBeNull();
    // But a clean utterance still resolves: the margin rule isn't just blanket strictness.
    expect(matchSpokenChoice('iran', countries)).toBe(0);
  });

  // Two options being similar is fine, so long as the transcript clearly favours one of them.
  it('still matches when a similar option trails by more than the margin', () => {
    const years = ['1980', '1990', 'Cheese', 'Tuesday'];
    expect(matchSpokenChoice('1980', years)).toBe(0);
  });

  it('returns null for silence', () => {
    expect(matchSpokenChoice('', CAPITALS)).toBeNull();
    expect(matchSpokenChoice('   ', CAPITALS)).toBeNull();
  });

  // A deck with few cards can yield fewer than four choices.
  it('skips blank options instead of matching them', () => {
    expect(matchSpokenChoice('paris', ['Paris', '', '', ''])).toBe(0);
    expect(matchSpokenChoice('   ', ['', '', '', ''])).toBeNull();
  });

  it('handles a single option without a second to compare against', () => {
    expect(matchSpokenChoice('paris', ['Paris'])).toBe(0);
    expect(matchSpokenChoice('banana', ['Paris'])).toBeNull();
  });

  // Guards the comment in voiceChoice.ts: if someone "tidies up" by reusing Test's constant, the
  // eiffel-tower case above breaks. Pin the relationship so the intent survives a refactor.
  it('uses a floor well below Test-mode grading, which answers a different question', () => {
    expect(VOICE_CHOICE_FLOOR).toBeLessThan(TEXT_ANSWER_THRESHOLD);
  });
});
