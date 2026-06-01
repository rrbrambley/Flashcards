# Flashcards — Web App

A React + TypeScript + Vite single-page app for Flashcards. It's a thin client over the
backend API (auth, deck library, deck create/edit with optional front-of-card images).
Practice isn't implemented on the web yet — tapping a deck opens it for editing.

This app has its **own npm toolchain** and is **not** part of the repo's Gradle build.

## Run it

```bash
npm install
cp .env.example .env     # then fill in values if needed
npm run dev              # http://localhost:5173
```

Start the backend first (see the [root README](../README.md)). The dev server's origin
(`http://localhost:5173`) is in the backend's default CORS allow-list.

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Vite dev server with HMR |
| `npm run build` | Type-check + production build to `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | ESLint |

## Configuration

Copy `.env.example` to `.env` (gitignored) and set:

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Backend base URL |
| `VITE_GOOGLE_WEB_CLIENT_ID` | *(blank)* | OAuth Web client ID; blank hides the Google button |

Use the **same** Google client ID the backend verifies against. See the root README's
"Optional features" for the full Google/AWS setup. **Never commit `.env`.**

## Structure

- `src/api/` — `types.ts` (hand-written mirrors of the shared API DTOs) and `client.ts`
  (fetch-based API client; bearer token in `localStorage`).
- `src/auth/` — login/register + Google sign-in (Google Identity Services).
- `src/decks/` — library list and the create/edit deck form (`DeckForm`).
