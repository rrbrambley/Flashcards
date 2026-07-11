// The in-session streak (FLA-99) derived from the answer log, so a resumed session can restore it
// rather than starting from zero. The streak is the length of the trailing run of consecutive
// correct answers — a derived value, not a persisted one, since every graded answer is already
// recorded. Mirrors shared Kotlin `practice/grading/PracticeStreak.kt`.

/** @param correctByOrder each answer's correctness, in play order (i.e. sorted by sequence ascending). */
export function trailingCorrectStreak(correctByOrder: boolean[]): number {
  let streak = 0;
  for (let i = correctByOrder.length - 1; i >= 0 && correctByOrder[i]; i--) streak++;
  return streak;
}
