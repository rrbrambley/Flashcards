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
  /** Report the outcome for this card; advances the session. Call exactly once per card. */
  onResult: (correct: boolean) => void;
}

/** A selectable practice mode: its persisted key, display copy, and the component that runs it. */
export interface PracticeMode {
  /** Stable key persisted on the session (sent to the backend; e.g. "flashcards"). */
  key: string;
  label: string;
  description: string;
  Component: ComponentType<PracticeModeProps>;
}
