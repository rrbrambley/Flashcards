import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { gradeTextAnswer } from './textAnswer';
import { buildChoices } from './multipleChoice';
import { matchSpokenChoice } from './voiceChoice';
import type { FlashcardDto } from '../../api/types';

// Parity guard (FLA-81): asserts the web's grading matches the canonical golden fixture that the
// shared Kotlin's jvmTest suite also loads (shared/.../GradingParityFixtureTest.kt). The fixture is
// the single source of truth — if someone changes the typo threshold or the choice rules on only one
// platform, that platform's run fails against it, so drift between web and mobile is caught in CI.
//
// We read the file via node:fs (Vitest runs in Node) rather than an `import`, so the fixture can live
// outside webApp's root without tripping Vite's filesystem-allow restrictions.

interface TextCase {
  name: string;
  input: string;
  answer: string;
  alternativeAnswers?: string[];
  expectedCorrect: boolean;
  expectedSimilarity?: number;
}

interface ChoiceCase {
  name: string;
  cards: { question: string; answer: string }[];
  cardIndex: number;
  count: number;
  expectedSize: number;
  correct: string;
  allowedDistractors: string[];
}

interface VoiceChoiceCase {
  name: string;
  transcript: string;
  options: string[];
  /** null = "didn't catch that" — the panel must re-prompt rather than pick a best guess. */
  expectedIndex: number | null;
}

const here = dirname(fileURLToPath(import.meta.url));
const fixturePath = resolve(here, '../../../../testFixtures/practice-grading/grading-fixtures.json');
const fixtures: {
  textGrading: TextCase[];
  multipleChoice: ChoiceCase[];
  voiceChoiceMatch: VoiceChoiceCase[];
} = JSON.parse(
  readFileSync(fixturePath, 'utf-8'),
);

describe('grading parity (golden fixture)', () => {
  it.each(fixtures.textGrading)('text grading: $name', (c) => {
    const { correct, similarity } = gradeTextAnswer(c.input, c.answer, c.alternativeAnswers ?? []);
    expect(correct).toBe(c.expectedCorrect);
    if (c.expectedSimilarity !== undefined) {
      expect(similarity).toBeCloseTo(c.expectedSimilarity, 9);
    }
  });

  // The floor and the margin live in the fixture, not in either implementation, so neither side can
  // be retuned alone (#388/#389). These are the numbers most likely to be tweaked after real use.
  it.each(fixtures.voiceChoiceMatch)('voice choice: $name', (c) => {
    expect(matchSpokenChoice(c.transcript, c.options)).toBe(c.expectedIndex);
  });

  it.each(fixtures.multipleChoice)('multiple choice: $name', (c) => {
    // The Fisher–Yates RNG streams differ between JS and Kotlin, so the fixture asserts
    // seed-independent properties (size, includes-correct, distractor pool, no dupes) rather than
    // exact ordering — see the fixture's note. A fixed RNG only makes the run reproducible.
    const deck = c.cards as FlashcardDto[];
    const choices = buildChoices(deck[c.cardIndex], deck, c.count, () => 0);

    expect(choices.length).toBe(c.expectedSize);
    expect(choices).toContain(c.correct);
    expect(new Set(choices).size).toBe(choices.length); // no duplicates
    for (const distractor of choices.filter((x) => x !== c.correct)) {
      expect(c.allowedDistractors).toContain(distractor);
    }
  });
});
