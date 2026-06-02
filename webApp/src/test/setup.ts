// Registers @testing-library/jest-dom matchers (toBeInTheDocument, toBeDisabled, …)
// on Vitest's expect.
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Vitest globals are off, so register Testing Library's DOM cleanup explicitly.
afterEach(cleanup);

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
