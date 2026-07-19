import { useState } from 'react';
import { api } from '../api/client';
import type { FlashcardDto } from '../api/types';
import { buildChoices } from './grading/multipleChoice';
import { gradeTextAnswer } from './grading/textAnswer';
import { MultipleChoice } from './components/MultipleChoice';
import type { PracticeMode } from './modes/types';

interface BatchPracticeRunnerProps {
  // Null for a guest: the batch runs entirely in-memory and isn't persisted.
  sessionId: number | null;
  cards: FlashcardDto[];
  // Test or Multiple Choice only (#293) — Classic has no objective grade to defer.
  mode: PracticeMode;
  onAgain: () => void;
  onExit: () => void;
}

/**
 * "Grade at the end" runner (#293): shows every card in one scrollable list so the user can answer in
 * any order (and skip back to a hard one), then grades the whole session on Submit and reveals
 * right/wrong inline. Single sitting — answers live in memory; leaving before Submit discards them.
 * Signed-in runs record the answer batch + complete the session on Submit; guests grade locally only.
 */
export function BatchPracticeRunner({ sessionId, cards, mode, onAgain, onExit }: BatchPracticeRunnerProps) {
  const isTest = mode.key === 'test';
  // Multiple-choice options per card, built once so they don't reshuffle on re-render.
  const [choices] = useState<string[][]>(() => (isTest ? [] : cards.map((c) => buildChoices(c, cards))));
  // Per-card entry: a typed string (Test) or a selected option index (Multiple Choice); null/'' = unanswered.
  const [entries, setEntries] = useState<(string | number | null)[]>(() => cards.map(() => (isTest ? '' : null)));
  const [results, setResults] = useState<boolean[] | null>(null);
  const submitted = results !== null;

  const isAnswered = (i: number) => (isTest ? (entries[i] as string).trim() !== '' : entries[i] != null);
  const answeredCount = cards.filter((_, i) => isAnswered(i)).length;
  const correctCount = results?.filter(Boolean).length ?? 0;

  const setEntry = (i: number, value: string | number) =>
    setEntries((prev) => prev.map((e, j) => (j === i ? value : e)));

  const gradeCard = (card: FlashcardDto, i: number): boolean => {
    if (isTest) return gradeTextAnswer(entries[i] as string, card.answer, card.alternativeAnswers ?? []).correct;
    const pick = entries[i];
    return pick != null && choices[i][pick as number]?.trim() === card.answer.trim();
  };

  const submittedText = (i: number): string | null => {
    if (isTest) return (entries[i] as string) || null;
    const pick = entries[i];
    return pick != null ? choices[i][pick as number] : null;
  };

  const submit = async () => {
    const graded = cards.map((c, i) => gradeCard(c, i));
    setResults(graded);
    // Signed-in: log the whole batch (sequence = list order) and complete the session — best effort,
    // so a network hiccup still shows the local results.
    if (sessionId !== null) {
      const now = Date.now();
      const batch = cards.map((c, i) => ({
        answerUid: crypto.randomUUID(),
        cardUid: c.cardUid ?? '',
        correct: graded[i],
        sequence: i,
        answeredAtMillis: now,
        submittedText: submittedText(i),
      }));
      await api.recordAnswers(sessionId, batch).catch(() => {});
      await api.completeSession(sessionId, Intl.DateTimeFormat().resolvedOptions().timeZone).catch(() => {});
    }
  };

  return (
    <div className="batch-practice">
      {submitted && (
        <div className="batch-score">
          <h2>
            You got {correctCount} of {cards.length}
          </h2>
          <div className="practice-actions">
            <button onClick={onAgain}>Practice again</button>
            <button className="secondary" onClick={onExit}>
              Done
            </button>
          </div>
        </div>
      )}

      <ol className="batch-list">
        {cards.map((card, i) => {
          const correct = results?.[i];
          const state = !submitted ? '' : correct ? ' correct' : ' incorrect';
          const correctIndex = isTest || !submitted ? null : choices[i].indexOf(card.answer.trim());
          return (
            <li key={card.cardUid ?? i} className={`batch-item${state}`}>
              <div className="batch-prompt">
                <span className="batch-number">{i + 1}</span>
                {card.question && <span className="batch-question">{card.question}</span>}
                {card.imageUrl && <img src={card.imageUrl} alt="" className="batch-image" />}
              </div>

              {isTest ? (
                <input
                  type="text"
                  className="batch-input"
                  value={entries[i] as string}
                  disabled={submitted}
                  aria-label={`Answer for question ${i + 1}`}
                  onChange={(e) => setEntry(i, e.target.value)}
                />
              ) : (
                <MultipleChoice
                  options={choices[i]}
                  onSelect={(idx) => setEntry(i, idx)}
                  selectedIndex={entries[i] as number | null}
                  correctIndex={correctIndex}
                  disabled={submitted}
                />
              )}

              {submitted && (
                <div className="batch-result">
                  <span className="review-outcome" aria-label={correct ? 'correct' : 'incorrect'}>
                    {correct ? '✓' : '✗'}
                  </span>
                  <span className="batch-correct-answer">{card.answer}</span>
                </div>
              )}
            </li>
          );
        })}
      </ol>

      {!submitted && (
        <div className="batch-submit-bar">
          <button className="batch-submit" onClick={submit} disabled={answeredCount === 0}>
            Submit ({answeredCount}/{cards.length})
          </button>
        </div>
      )}
    </div>
  );
}
