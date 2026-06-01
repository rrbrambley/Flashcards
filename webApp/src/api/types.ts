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
