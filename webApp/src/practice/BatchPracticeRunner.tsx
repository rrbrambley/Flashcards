import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import type { FlashcardDto } from '../api/types';
import { buildChoices } from './grading/multipleChoice';
import { gradeTextAnswer } from './grading/textAnswer';
import { MultipleChoice } from './components/MultipleChoice';
import type { PracticeMode } from './modes/types';
import { formatRemaining, useCountdown } from './useCountdown';

interface BatchPracticeRunnerProps {
  // Null for a guest: the batch runs entirely in-memory and isn't persisted.
  sessionId: number | null;
  cards: FlashcardDto[];
  // Test or Multiple Choice only (#293) — Classic has no objective grade to defer.
  mode: PracticeMode;
  // Wall-clock deadline (epoch millis) for a timed session (#289), or null when untimed.
  deadline: number | null;
  onAgain: () => void;
  onExit: () => void;
}

// A finished card in the results screen: the played card, whether it was graded correct, and what
// the user actually submitted (null = left blank / skipped).
interface BatchResult {
  card: FlashcardDto;
  correct: boolean;
  submittedText: string | null;
}

/**
 * "Grade at the end" runner (#293): shows every card in one scrollable list so the user can answer in
 * any order (and skip back to a hard one), then grades the whole session on Submit. Rather than
 * revealing right/wrong inline (which left the user stranded at the bottom of the list — #298), Submit
 * swaps in the same "Practice complete" results screen the card-by-card runner uses, scrolled to top.
 * Single sitting — answers live in memory; leaving before Submit discards them. Signed-in runs record
 * the answer batch + complete the session on Submit; guests grade locally only.
 */
export function BatchPracticeRunner({ sessionId, cards, mode, deadline, onAgain, onExit }: BatchPracticeRunnerProps) {
  const isTest = mode.key === 'test';
  // Multiple-choice options per card, built once so they don't reshuffle on re-render.
  const [choices] = useState<string[][]>(() => (isTest ? [] : cards.map((c) => buildChoices(c, cards))));
  // Per-card entry: a typed string (Test) or a selected option index (Multiple Choice); null/'' = unanswered.
  const [entries, setEntries] = useState<(string | number | null)[]>(() => cards.map(() => (isTest ? '' : null)));
  // Null until Submit; then the per-card grade that drives the results screen.
  const [results, setResults] = useState<BatchResult[] | null>(null);
  // Timed session (#289): count down to the deadline; on expiry, auto-submit whatever's been answered.
  const { remainingMs, expired } = useCountdown(deadline);

  const isAnswered = (i: number) => (isTest ? (entries[i] as string).trim() !== '' : entries[i] != null);
  const answeredCount = cards.filter((_, i) => isAnswered(i)).length;

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
    const graded: BatchResult[] = cards.map((card, i) => ({
      card,
      correct: gradeCard(card, i),
      submittedText: submittedText(i),
    }));
    setResults(graded);
    // Swap-to-results changes the page height; land the user at the top of the score (#298).
    window.scrollTo({ top: 0 });
    // Signed-in: log the whole batch (sequence = list order) and complete the session — best effort,
    // so a network hiccup still shows the local results.
    if (sessionId !== null) {
      const now = Date.now();
      const batch = graded.map((g, i) => ({
        answerUid: crypto.randomUUID(),
        cardUid: g.card.cardUid ?? '',
        correct: g.correct,
        sequence: i,
        answeredAtMillis: now,
        submittedText: g.submittedText,
      }));
      await api.recordAnswers(sessionId, batch).catch(() => {});
      await api.completeSession(sessionId, Intl.DateTimeFormat().resolvedOptions().timeZone).catch(() => {});
    }
  };

  // Timed session (#289): auto-submit whatever's answered when the countdown expires. A ref keeps the
  // effect calling the latest `submit` (its closure over `entries`) without re-firing every keystroke.
  const submitRef = useRef(submit);
  useEffect(() => {
    submitRef.current = submit;
  });
  useEffect(() => {
    if (expired && results === null) void submitRef.current();
  }, [expired, results]);

  // Results screen: reuse the card-by-card runner's "Practice complete" layout (#298) so all the
  // grade-at-the-end feedback lives on one screen at the top, not stranded inline below the list.
  if (results !== null) {
    const numCorrect = results.filter((r) => r.correct).length;
    const numIncorrect = results.length - numCorrect;
    return (
      <div className="practice-complete">
        <h2>Practice complete</h2>
        <p className="muted">
          You reviewed {results.length} card{results.length === 1 ? '' : 's'}.
        </p>
        <div className="score-row">
          <span className="score-chip incorrect">{numIncorrect}</span>
          <span className="score-chip correct">{numCorrect}</span>
        </div>
        <div className="review">
          <h3 className="review-heading">Review</h3>
          <ul className="review-list">
            {results.map((r, i) => (
              <li key={r.card.cardUid ?? i} className={`review-item ${r.correct ? 'correct' : 'incorrect'}`}>
                <span className="review-outcome" aria-label={r.correct ? 'correct' : 'incorrect'}>
                  {r.correct ? '✓' : '✗'}
                </span>
                {r.card.imageUrl && <img src={r.card.imageUrl} alt="" className="review-image" />}
                <div className="review-text">
                  {r.card.question && <span className="review-prompt">{r.card.question}</span>}
                  <span className="review-answer">{r.card.answer}</span>
                  {r.submittedText && <span className="review-submitted">You answered: {r.submittedText}</span>}
                </div>
              </li>
            ))}
          </ul>
        </div>
        <div className="practice-actions">
          <button onClick={onAgain}>Practice again</button>
          <button className="secondary" onClick={onExit}>
            Done
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="batch-practice">
      {/* Timed session (#289): a live m:ss countdown, urgent styling in the last 10s. */}
      {deadline != null && (
        <div className="practice-timer-row">
          <span
            className={`practice-timer${remainingMs <= 10000 ? ' urgent' : ''}`}
            aria-label="time remaining"
          >
            ⏱ {formatRemaining(remainingMs)}
          </span>
        </div>
      )}
      <ol className="batch-list">
        {cards.map((card, i) => (
          <li key={card.cardUid ?? i} className="batch-item">
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
                aria-label={`Answer for question ${i + 1}`}
                onChange={(e) => setEntry(i, e.target.value)}
              />
            ) : (
              <MultipleChoice
                options={choices[i]}
                onSelect={(idx) => setEntry(i, idx)}
                selectedIndex={entries[i] as number | null}
                correctIndex={null}
                disabled={false}
              />
            )}
          </li>
        ))}
      </ol>

      <div className="batch-submit-bar">
        <button className="batch-submit" onClick={submit} disabled={answeredCount === 0}>
          Submit ({answeredCount}/{cards.length})
        </button>
      </div>
    </div>
  );
}
