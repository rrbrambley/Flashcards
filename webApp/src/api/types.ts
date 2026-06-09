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
  // Category tags. The backend stores a list; the UI surfaces only the first as the "Category".
  // Omitted on older payloads → treated as untagged.
  tags?: string[];
}

export interface CreateDeckRequest {
  title: string;
  flashcards: FlashcardDto[];
  tags?: string[];
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  userId: number;
  // The user's effective feature permissions (e.g. 'manage_global_decks'). May be absent on older responses.
  permissions?: string[];
}

// GET /auth/me — the current user's identity, roles, and effective permissions.
export interface MeResponse {
  userId: number;
  email: string;
  roles: string[];
  permissions: string[];
}

// GET /admin/users — a user as seen by the admin RBAC UI.
export interface AdminUserDto {
  id: number;
  email: string;
  roles: string[];
}

// GET /admin/roles — a code-defined role from the catalog (read-only on the web).
export interface RoleDto {
  key: string;
  description: string;
  permissions: string[];
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
  // The practice mode this session runs in (flashcards / test / multiple_choice). Defaulted server-side.
  mode: string;
  createdAtMillis: number;
  updatedAtMillis: number;
}

export interface CreateSessionRequest {
  deckId: number;
  mode?: string;
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
