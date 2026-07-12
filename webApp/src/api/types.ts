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
  // The chosen avatar's key (one of the curated set), or null when unset. FLA-162.
  avatarKey?: string | null;
  // Resolved CDN URL for the chosen avatar, or null when unset / the CDN isn't configured (the UI
  // then renders an initials monogram). FLA-162.
  avatarUrl?: string | null;
  // The caller's resolved feature flags — each catalog flag key → its effective value (FLA-174).
  // Also available via GET /flags.
  flags?: Record<string, boolean>;
}

// PATCH /auth/me body (FLA-114/FLA-162). Per-field merge: an omitted field is left unchanged, a
// blank string clears it. So updating the avatar never disturbs the display name, and vice-versa.
export interface UpdateProfileRequest {
  displayName?: string;
  avatarKey?: string;
}

// GET /avatars — one option in the curated avatar catalog (FLA-162). Empty list when the CDN is
// unconfigured (graceful degradation → the picker is hidden).
export interface AvatarOption {
  key: string;
  url: string;
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

// GET /admin/flags — a feature flag as seen by the admin management UI (FLA-176). `enabled` is the
// global default state; the override lists carry per-user / per-role targeting.
export interface AdminFlagDto {
  key: string;
  description: string;
  enabled: boolean;
  userOverrides: FlagUserOverrideDto[];
  roleOverrides: FlagRoleOverrideDto[];
}

export interface FlagUserOverrideDto {
  userId: number;
  email: string;
  enabled: boolean;
}

export interface FlagRoleOverrideDto {
  roleKey: string;
  enabled: boolean;
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
  // Whether this session presents cards in a randomized order, and the seed that reproduces it
  // (FLA-200). Applied client-side by `orderCards`. Defaulted server-side (unshuffled, seed 0).
  shuffle: boolean;
  shuffleSeed: number;
}

export interface CreateSessionRequest {
  deckId: number;
  mode?: string;
  // Randomize card order for a newly-created session (FLA-200); ignored when resuming (the stored
  // order wins). The server mints `shuffleSeed` once when true.
  shuffle?: boolean;
}

export interface UpdateProgressRequest {
  currentCardIndex: number;
  numCorrect: number;
  numIncorrect: number;
}

// One recorded answer in a session's append-only log (FLA-99): backs the in-session streak + an
// end-of-session review. `answerUid` is client-minted for idempotency; `sequence` is 0-based play
// order; `cardUid` (FLA-113) joins back to the card; `submittedText` is the typed/picked answer.
export interface PracticeAnswer {
  answerUid: string;
  cardUid: string;
  correct: boolean;
  sequence: number;
  answeredAtMillis: number;
  submittedText?: string | null;
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

// GET /streaks/calendar?month=YYYY-MM — the days of `month` the user completed a session (FLA-170),
// for the activity calendar. `activeDays` are day-of-month ints (1–31); `current`/`longest` are the
// overall streak (so the calendar header needs no second request).
export interface StreakCalendarResponse {
  month: string;
  activeDays: number[];
  current: number;
  longest: number;
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
  // The author's avatar URL, or null/absent when unset (→ initials monogram). FLA-162.
  authorAvatarUrl?: string | null;
  content: string;
  // Present on a reply (one level deep); null/absent on a top-level message.
  parentMessageId?: number | null;
  createdAtMillis: number;
  // Whether a moderator removed this message (FLA-118). When true, `content` is blank and the UI
  // renders a tombstone. Omitted/absent → not deleted.
  deleted?: boolean;
}

// GET /admin/answer-suggestions — one open answer suggestion in the review queue (FLA-130).
// Backend⇄web admin contract (mirrors backend AnswerSuggestionDto), not part of the shared SDK.
export interface AnswerSuggestion {
  id: number;
  cardUid: string;
  suggestedAnswer: string;
  deckId: number;
  deckTitle: string;
  question: string;
  currentAnswer: string;
  suggesterDisplayName: string;
  createdAtMillis: number;
}

// GET /admin/discussions/reports — one open report in the moderation queue (FLA-118). Backend⇄web
// admin contract (mirrors backend ReportedMessageDto), not part of the cross-platform SDK.
export interface ReportedMessage {
  reportId: number;
  reason?: string | null;
  status: string;
  reportedAtMillis: number;
  reporterDisplayName: string;
  messageId: number;
  cardUid: string;
  authorDisplayName: string;
  content: string;
  deleted: boolean;
  messageCreatedAtMillis: number;
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
  // Current in-session streak (FLA-99) — the trailing consecutive-correct run; 0 = none (flame hidden).
  streak: number;
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
