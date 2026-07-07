/**
 * Where the practice "back"/exit returns to (FLA-168). Guests only have the catalog (at `/`), so they
 * always return there. Signed-in users return to the referrer passed in router state by the entry
 * point (Home / Library) — mirroring the native back-stack — and fall back to the Home hub for a
 * shared deep link with no origin.
 */
export function exitTarget(from: string | undefined, isGuest: boolean): { to: string; label: string } {
  if (isGuest) return { to: '/', label: 'Catalog' };
  if (from === '/') return { to: '/', label: 'Home' };
  if (from?.startsWith('/library')) return { to: from, label: 'Library' };
  return { to: '/', label: 'Home' };
}

/** Reads the practice referrer that entry points stash in router `location.state`. */
export function fromState(state: unknown): string | undefined {
  return (state as { from?: string } | null)?.from;
}
