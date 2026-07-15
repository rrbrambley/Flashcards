import type { ComponentType } from 'react';
import type { FlashcardDto } from '../../api/types';

/**
 * The contract every practice mode implements. The runner owns the session loop (index, score,
 * persistence, completion); a mode renders the current card however it likes, decides whether the
 * user got it right, and calls [onResult]. Keeping this surface small lets a future "Learn" mode
 * compose the existing modes' building blocks.
 */
export interface PracticeModeProps {
  /** The card to present. */
  card: FlashcardDto;
  /** The full deck (e.g. so multiple-choice can draw distractors from other cards' answers). */
  cards: FlashcardDto[];
  /**
   * Grade + advance in one step — for modes with no verdict to dwell on (Classic's swipe). Scores
   * the card and moves on. Call exactly once per card. (Test/Multiple-Choice use [onGraded]/[onAdvance].)
   */
  onResult: (correct: boolean, submittedText?: string) => void;
  /**
   * Score this card (and the in-session streak) when the verdict is revealed, *without* advancing —
   * so the streak badge surfaces on the answer itself. Pair with [onAdvance] on "Next". Call once.
   */
  onGraded: (correct: boolean, submittedText?: string) => void;
  /** Advance to the next card (the "Next" affordance after a graded verdict). */
  onAdvance: () => void;
  /**
   * Opens the card's discussion thread (FLA-116). Provided only when the deck has discussions
   * enabled; a mode renders a 💬 control once the answer is revealed. Absent → no discussion UI.
   */
  onDiscuss?: () => void;
  /**
   * Whether the user may suggest an alternative answer for this card (FLA-130) — true on a global
   * deck's card. Test mode shows a "This should be correct" action on the incorrect verdict.
   */
  canSuggest?: boolean;
  /** Whether the current user is a signed-out guest (gates the sign-in conversion for suggestions). */
  isGuest?: boolean;
}

/** A selectable practice mode: its persisted key, display copy, and the component that runs it. */
export interface PracticeMode {
  /** Stable key persisted on the session (sent to the backend; e.g. "flashcards"). */
  key: string;
  label: string;
  description: string;
  /** Feature flag gating this mode's availability in the chooser (FLA-213). */
  flagKey: string;
  Component: ComponentType<PracticeModeProps>;
}
