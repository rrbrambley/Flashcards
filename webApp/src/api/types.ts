// TypeScript mirrors of the backend's shared @Serializable DTOs
// (com.rrbrambley.flashcards.shared.api).

export interface FlashcardDto {
  question: string;
  answer: string;
  imageUrl?: string | null;
}

export interface FlashcardDeckDto {
  id: number;
  title: string;
  flashcards: FlashcardDto[];
  // Whether the current user may edit this deck. Omitted (treated as true) for older payloads;
  // false for the read-only global catalog deck.
  editable?: boolean;
}

export interface CreateDeckRequest {
  title: string;
  flashcards: FlashcardDto[];
}

export interface AuthResponse {
  token: string;
  userId: number;
}

export interface ErrorResponse {
  error: string;
  message?: string | null;
}

export interface ImageUploadResponse {
  url: string;
}
