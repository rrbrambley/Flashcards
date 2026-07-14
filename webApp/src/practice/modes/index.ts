import type { PracticeMode } from './types';
import { ClassicMode } from './ClassicMode';
import { MultipleChoiceMode } from './MultipleChoiceMode';
import { TestMode } from './TestMode';

/**
 * The registry of practice modes. Adding a mode = add an entry here (and its component); it then
 * appears in the mode chooser and is dispatchable by its key. Order is the chooser's display order.
 */
export const PRACTICE_MODES: PracticeMode[] = [
  {
    key: 'flashcards',
    label: 'Classic',
    description: 'Flip the card and mark whether you knew it.',
    flagKey: 'practice_mode_classic',
    Component: ClassicMode,
  },
  {
    key: 'test',
    label: 'Test',
    description: 'Type the answer.',
    flagKey: 'practice_mode_test',
    Component: TestMode,
  },
  {
    key: 'multiple_choice',
    label: 'Multiple Choice',
    description: 'Pick the answer from four options.',
    flagKey: 'practice_mode_multiple_choice',
    Component: MultipleChoiceMode,
  },
];

export const DEFAULT_MODE = PRACTICE_MODES[0];

export function findMode(key: string | null | undefined): PracticeMode | undefined {
  return PRACTICE_MODES.find((m) => m.key === key);
}
