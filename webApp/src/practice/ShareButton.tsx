import { useState } from 'react';
import { shareDeck } from './share';

/** Shares a deck's public practice link (native share sheet or clipboard), with brief copied feedback. */
export function ShareButton({ deckId, title, className }: { deckId: number; title?: string; className?: string }) {
  const [copied, setCopied] = useState(false);

  const onShare = async () => {
    try {
      const how = await shareDeck(deckId, title);
      if (how === 'copied') {
        setCopied(true);
        setTimeout(() => setCopied(false), 1500);
      }
    } catch {
      // clipboard blocked / share failed — nothing actionable for the user
    }
  };

  return (
    <button className={className ?? 'secondary'} onClick={onShare} aria-label="Share deck">
      {copied ? 'Link copied!' : 'Share'}
    </button>
  );
}
