import type { PracticeMode } from './types';
import { ClassicMode } from './ClassicMode';

/**
 * The registry of practice modes. Adding a mode = add an entry here (and its component); it then
 * appears in the mode chooser and is dispatchable by its key. Order is the chooser's display order.
 */
export const PRACTICE_MODES: PracticeMode[] = [
  {
    key: 'flashcards',
    label: 'Classic',
    description: 'Flip the card and mark whether you knew it.',
    Component: ClassicMode,
  },
];

export const DEFAULT_MODE = PRACTICE_MODES[0];

export function findMode(key: string | null | undefined): PracticeMode | undefined {
  return PRACTICE_MODES.find((m) => m.key === key);
}
