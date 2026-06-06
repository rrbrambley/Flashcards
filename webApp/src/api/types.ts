// TypeScript mirrors of the backend's shared @Serializable DTOs
// (com.rrbrambley.flashcards.shared.api).

// One page of a cursor-paginated list endpoint. `nextCursor` is an opaque token to pass back as
// the `cursor` query param for the next page, or null on the last page.
export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}

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
  accessToken: string;
  refreshToken: string;
  userId: number;
}

export interface ErrorResponse {
  error: string;
  message?: string | null;
}

export interface ImageUploadResponse {
  url: string;
}

export interface PracticeSessionDto {
  id: number;
  deckId: number;
  deckTitle: string;
  currentCardIndex: number;
  numCorrect: number;
  numIncorrect: number;
  isCompleted: boolean;
  createdAtMillis: number;
  updatedAtMillis: number;
}

export interface CreateSessionRequest {
  deckId: number;
}

export interface UpdateProgressRequest {
  currentCardIndex: number;
  numCorrect: number;
  numIncorrect: number;
}

// Home feed (GET /home). The action is a discriminated union mirroring the backend's
// sealed HomeButtonActionDto (kotlinx default class discriminator: "type").
export type HomeButtonAction =
  | { type: 'navigate_to_practice'; deckId: number }
  | { type: 'create_new_flashcard_set' }
  | { type: 'continue_practice'; sessionId: number };

export interface HomeButton {
  message: string;
  action: HomeButtonAction;
}

export interface HomeData {
  title: string;
  button?: HomeButton | null;
}
