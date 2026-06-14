import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { deckShareUrl, shareDeck } from './share';

describe('share', () => {
  beforeEach(() => {
    vi.stubGlobal('location', { origin: 'https://flashcards.example' });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('builds a public practice URL for a deck', () => {
    expect(deckShareUrl(7)).toBe('https://flashcards.example/decks/7/practice');
  });

  it('copies the link to the clipboard when native share is unavailable', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });

    const how = await shareDeck(7, 'Flags');

    expect(how).toBe('copied');
    expect(writeText).toHaveBeenCalledWith('https://flashcards.example/decks/7/practice');
  });

  it('uses the native share sheet when available', async () => {
    const share = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { share });

    const how = await shareDeck(7, 'Flags');

    expect(how).toBe('shared');
    expect(share).toHaveBeenCalledWith({ title: 'Practice Flags', url: 'https://flashcards.example/decks/7/practice' });
  });
});
