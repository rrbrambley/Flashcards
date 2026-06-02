export interface CardFields {
  term: string;
  definition: string;
  imageUrl: string | null;
}

// A card is complete with a definition plus either a term or an image (image-only allowed).
export const isComplete = (c: CardFields): boolean =>
  c.definition.trim() !== '' && (c.term.trim() !== '' || c.imageUrl != null);

export const isStarted = (c: CardFields): boolean =>
  c.term.trim() !== '' || c.definition.trim() !== '' || c.imageUrl != null;
