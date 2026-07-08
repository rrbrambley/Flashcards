// Registers @testing-library/jest-dom matchers (toBeInTheDocument, toBeDisabled, …)
// on Vitest's expect.
import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach } from 'vitest';

// Vitest globals are off, so register Testing Library's DOM cleanup explicitly.
afterEach(cleanup);

// Raise the async-util timeout from RTL's 1000ms default. Loaded CI runners occasionally take longer
// than 1s to flush a render after a userEvent + mocked-API state update, so `findBy*`/`waitFor` would
// spuriously time out (e.g. "Unable to find … Q2") even though the app is correct. 5s absorbs the
// jitter with ample margin while still failing a genuinely stuck test.
configure({ asyncUtilTimeout: 5000 });

// Node 22+/25 expose a partial Web Storage `localStorage` global (no `clear`, etc.) that
// shadows jsdom's. Install a clean in-memory implementation so storage is deterministic
// across Node versions.
function createMemoryStorage(): Storage {
  let store: Record<string, string> = {};
  return {
    get length() {
      return Object.keys(store).length;
    },
    clear() {
      store = {};
    },
    getItem(key: string) {
      return Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null;
    },
    key(index: number) {
      return Object.keys(store)[index] ?? null;
    },
    removeItem(key: string) {
      delete store[key];
    },
    setItem(key: string, value: string) {
      store[key] = String(value);
    },
  };
}

Object.defineProperty(globalThis, 'localStorage', {
  value: createMemoryStorage(),
  configurable: true,
  writable: true,
});
