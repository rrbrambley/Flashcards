# Flashcards — Web App

A React + TypeScript + Vite single-page app for Flashcards. It's a client over the backend API
covering the Android client's core flows — auth, the deck library (title + category search,
sort), deck create/edit (optional front-of-card images + a Category) — plus extras the other
clients don't have yet: three **practice modes** (classic flip, text-entry Test, Multiple Choice;
routed at `/decks/:id/practice`) and **admin** screens (manage the global deck catalog; assign
user roles) gated on the signed-in user's permissions.

This app has its **own npm toolchain** and is **not** part of the repo's Gradle build.

## Run it

```bash
npm install
cp .env.example .env     # then fill in values if needed
npm run dev              # http://localhost:5173
```

Start the backend first (see the [root README](../README.md)). The dev server is pinned to
**port 5173** (`vite.config.ts`, `strictPort`) — that origin is in the backend's default CORS
allow-list and is the one registered with the Google OAuth client, so it fails loudly instead
of drifting to another port if 5173 is taken.

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Vite dev server with HMR |
| `npm run build` | Type-check + production build to `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | ESLint |
| `npm run test` | Vitest unit/component tests (`test:coverage` for coverage) |

## Configuration

Copy `.env.example` to `.env` (gitignored) and set:

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Backend base URL |
| `VITE_GOOGLE_WEB_CLIENT_ID` | *(blank)* | OAuth Web client ID; blank hides the Google button |

Use the **same** Google client ID the backend verifies against. See the root README's
"Optional features" for the full Google/AWS setup. **Never commit `.env`.**

## Structure

- `src/api/` — `types.ts` (hand-written mirrors of the shared API DTOs, including the
  `Page<T>` envelope, plus the web-only `AdminUserDto`/`RoleDto`) and `client.ts` (fetch-based API
  client; access + refresh tokens in `localStorage`, with single-flight transparent refresh on `401`).
- `src/auth/` — login/register + Google sign-in (Google Identity Services), and an `AuthContext`
  that stores the user's `permissions` and exposes `can(permission)` to gate admin UI.
- `src/decks/` — a reusable `DeckLibrary` (title + tag search + sort, cursor "Load more") used by
  the personal library and the admin global-catalog view (`/library/global`), plus the create/edit
  deck form (`DeckForm`, including the optional Category).
- `src/admin/` — the admin RBAC screen (`/admin/users`): a searchable, paginated user list with
  grant/revoke role controls.
- `src/practice/` — a mode-agnostic runner (reducer + session persistence) delegating to a
  registered mode view (`modes/ClassicMode`, `modes/TestMode`, `modes/MultipleChoiceMode`), routed
  at `/decks/:id/practice`.
