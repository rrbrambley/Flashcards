// A public, shareable link to practice a deck. Global catalog decks are readable without an account,
// so a recipient (even a guest) can open this and start practicing (FLA-101).
export function deckShareUrl(deckId: number): string {
  return `${window.location.origin}/decks/${deckId}/practice`;
}

/**
 * Shares a deck's practice link: the native share sheet when available (mobile), otherwise copies the
 * URL to the clipboard. Returns how it was shared so the caller can show feedback. Throws only if both
 * paths fail (e.g. clipboard blocked).
 */
export async function shareDeck(deckId: number, title?: string): Promise<'shared' | 'copied'> {
  const url = deckShareUrl(deckId);
  if (typeof navigator !== 'undefined' && navigator.share) {
    try {
      await navigator.share({ title: title ? `Practice ${title}` : 'Practice flashcards', url });
      return 'shared';
    } catch {
      // user cancelled or share unavailable — fall through to copying the link
    }
  }
  await navigator.clipboard.writeText(url);
  return 'copied';
}
