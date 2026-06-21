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
  // Extra answers accepted alongside `answer` when grading free-text Test mode (FLA-109).
  // Omitted/absent on older payloads → treated as none.
  alternativeAnswers?: string[];
  // Stable per-card id assigned by the backend, preserved across edits (FLA-113). Blank/absent for a
  // not-yet-saved card; the edit form round-trips it so ids aren't regenerated.
  cardUid?: string;
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
  // Whether per-card discussions are available (FLA-115) — true only for a global deck with
  // discussions enabled. Omitted/absent → treated as off.
  discussionsEnabled?: boolean;
  // Whether this is a global (catalog) deck (FLA-120) — independent of owner; admin-toggled.
  // Omitted/absent → treated as not global.
  isGlobal?: boolean;
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
  // Explicit public display name, or null when unset (attribution then falls back to the email
  // local-part). FLA-114.
  displayName?: string | null;
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

// GET /streaks — the user's practice streak (consecutive days with a completed session). `overall`
// spans all decks; `decks` carries the same per deck (returned for future per-deck UI).
export interface StreakDto {
  current: number;
  longest: number;
}

export interface DeckStreakDto {
  deckId: number;
  current: number;
  longest: number;
}

export interface StreaksResponse {
  overall: StreakDto;
  decks: DeckStreakDto[];
}

// Card discussions (FLA-116), mirroring the backend⇄web contract in backend/.../discussions.
export interface DiscussionThread {
  cardUid: string;
  isLocked: boolean;
  messageCount: number;
}

export interface DiscussionMessage {
  id: number;
  authorDisplayName: string;
  content: string;
  // Present on a reply (one level deep); null/absent on a top-level message.
  parentMessageId?: number | null;
  createdAtMillis: number;
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

// Per-session detail on a "continue practice" home item: mode, score so far, and progress.
export interface HomeSessionInfo {
  mode: string;
  numCorrect: number;
  numIncorrect: number;
  currentCardIndex: number;
  totalCards: number;
}

export interface HomeData {
  title: string;
  button?: HomeButton | null;
  // Present on "continue practice" items so the card can show mode + progress; null otherwise.
  session?: HomeSessionInfo | null;
  // Section header this item belongs to (e.g. "Continue studying"); consecutive items sharing a
  // section render under one header. Null/absent for a flat, header-less item.
  section?: string | null;
}
