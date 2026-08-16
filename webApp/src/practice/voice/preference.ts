import { useCallback, useState } from 'react';

/**
 * Whether the user wants to answer by voice.
 *
 * Local only — deliberately not on the session DTO (#386). Voice is how you're answering right now,
 * not a property of the run: grading, resume and the recap are identical either way, so persisting it
 * would buy nothing for a backend column, a Room migration and three clients' session-creation chains.
 *
 * Defaults to **off**: opting into a microphone prompt should be explicit.
 */
export const VOICE_INPUT_KEY = 'practice.voiceInput';

export function readVoiceInputPreference(): boolean {
  try {
    return localStorage.getItem(VOICE_INPUT_KEY) === 'true';
  } catch {
    // A preference isn't worth throwing over — Safari's locked-down storage modes do throw.
    return false;
  }
}

export function writeVoiceInputPreference(enabled: boolean): void {
  try {
    localStorage.setItem(VOICE_INPUT_KEY, String(enabled));
  } catch {
    /* see above */
  }
}

/** The preference as state, so a toggle and the runner stay in step within a page. */
export function useVoiceInputPreference(): [boolean, (enabled: boolean) => void] {
  const [enabled, setEnabled] = useState(readVoiceInputPreference);
  const update = useCallback((next: boolean) => {
    setEnabled(next);
    writeVoiceInputPreference(next);
  }, []);
  return [enabled, update];
}
