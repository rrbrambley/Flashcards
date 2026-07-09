# Flashcards

A multiplatform flashcards app for self-testing across subjects. The goal is a shared
product across **Android, iOS, web, and a backend**, sharing business logic via Kotlin
Multiplatform (KMP) while keeping a native UI per platform.

You can build decks of cards (term + definition, with an optional front image and a category),
practice them three ways (classic flip, text-entry **Test**, **Multiple Choice**) with a live
in-session streak and an end-of-session per-card review, discuss individual cards on the shared
catalog, and sync across devices through the backend. The Android and iOS apps are offline-first;
the web app is a thin client over the same API. You can also browse and practice the public deck
catalog as a **guest**, with no account.

## Status

| Area | State |
|------|-------|
| KMP monorepo — `:shared:api` (Room-free API contract) + `:shared` (offline-first Room data layer + iOS framework); `build-logic` convention plugins | ✅ |
| Ktor + Postgres backend — auth, decks, sessions, home feed, images, streaks, discussions, RBAC | ✅ |
| Android app — Compose, offline-first, full feature set | ✅ |
| Web app — React/TypeScript, full feature set **+ admin UI** | ✅ |
| iOS app — SwiftUI, near-parity (everything below except the admin UI) | ✅ |
| Email/password + Google sign-in (all clients) | ✅ |
| Practice — classic flip, text-entry Test, Multiple Choice (all clients) | ✅ |
| In-session answer streak (live "N in a row") + daily practice streak | ✅ |
| End-of-session per-card review (correct/incorrect recap with your answers) | ✅ |
| Guest mode — browse + practice the public catalog without an account | ✅ |
| Card discussions on global decks — threaded, opt-in per deck, moderated (report / lock / delete) | ✅ |
| Answer suggestions ("this should be correct" on global-deck cards) with admin review | ✅ |
| Flashcard images (front side) — uploaded to S3, served via CloudFront | ✅ |
| Role-based access control — roles + feature permissions; manage the global catalog, user roles & moderation (web + operator CLI) | ✅ |
| JWT access + refresh tokens, transparent refresh-on-401, server-side logout | ✅ |

## Repository layout

A single Gradle build with four modules (`:shared` + its `:shared:api` child, `:androidApp`,
`:backend`) plus a `build-logic` included build, and a separate web app:

```
Flashcards/
├── shared/        KMP library — the offline-first data layer (domain, Room-KMP database,
│   │              repositories, sync) + the Kotlin/Native iOS framework. Used by androidApp & iosApp.
│   └── api/       :shared:api — the @Serializable API DTOs + Ktor FlashcardApiClient + token
│                  refresh. Room-free; the backend depends on only this, and :shared re-exports it.
├── build-logic/   Gradle convention plugins (SDK levels + JVM/Kotlin baseline), applied by each module.
├── androidApp/    Android app (Jetpack Compose, MVVM, Hilt). Offline-first.
├── iosApp/        SwiftUI app consuming the Shared framework (XcodeGen project, not in the Gradle build).
├── backend/       Ktor server (Netty) + Exposed + Postgres. The API the apps sync against.
├── webApp/        React + TypeScript + Vite SPA (its own npm toolchain, not in the Gradle build).
├── docker-compose.yml          Local Postgres for the backend
└── gradle/libs.versions.toml   Version catalog (single source for deps)
```

The HTTP API contract (DTOs + `FlashcardApiClient` + token refresh) lives in `:shared:api`, so
the backend can consume it without dragging in the mobile Room/SQLite layer. The offline-first
data layer (domain, Room-KMP database, repositories, sync) lives in `:shared` and is reused by both
apps — iOS gets a one-call `createIosFlashcardSdk(...)` factory and links it all as a single
`Shared.framework` (which re-exports `:shared:api`).

## Tech stack

- **Language:** Kotlin 2.2.10 (JVM toolchain 17), Kotlin Multiplatform
- **Android:** Jetpack Compose, Hilt (DI), Room-KMP (offline cache), Ktor client (OkHttp),
  DataStore, Coil (images), Credential Manager (Google sign-in)
- **iOS:** SwiftUI, the shared `Shared` Kotlin/Native framework (Room-KMP offline cache, Darwin
  Ktor engine), Keychain token storage; XcodeGen-generated project
- **Backend:** Ktor 3.2.x, Exposed + HikariCP, PostgreSQL, BCrypt, kotlinx.serialization,
  AWS SDK for Kotlin (S3), a profanity filter for discussion moderation
- **Web:** React 19 + TypeScript + Vite
- **Shared:** `:shared:api` (kotlinx.serialization DTOs + a Ktor `FlashcardApiClient` + token
  refresh) and `:shared` (the offline-first Room data layer) reused across platforms
- **Build:** Gradle with `build-logic` convention plugins + a version catalog

## Prerequisites

- **JDK 17+** and the Android SDK (easiest via [Android Studio](https://developer.android.com/studio)).
  Create `local.properties` with `sdk.dir=/path/to/Android/sdk` (gitignored) if Studio doesn't.
- **Docker** for Postgres and the backend's Testcontainers tests — via
  [Colima](https://github.com/abiosoft/colima) (`brew install colima docker docker-compose`)
  or Docker Desktop.
- **Node 20.19+ (or 22.12+)** and npm, for the web app.
- *(Optional)* an **AWS account** for image uploads and a **Google Cloud** OAuth client for
  Google sign-in. The app runs fully without either — see [Optional features](#optional-features).

---

## Quick start

```bash
git clone <your-fork-url> Flashcards && cd Flashcards

# 1. Backend + Postgres
docker compose up -d postgres        # Postgres on :5432 (see notes below)
./gradlew :backend:run               # serves at http://0.0.0.0:8080

# 2. Android app (in another terminal) — boot an emulator first
./gradlew :androidApp:installDebug

# 3. Web app (in another terminal)
cd webApp
npm install
cp .env.example .env                 # then edit if needed
npm run dev                          # http://localhost:5173
```

Register a new account in either client, or use the seeded demo login
(`demo@flashcards.dev` / `demo`, seeded as an **admin**). The backend ships a seeded global
deck catalog (**Flags of the World**, **National Capitals**, **U.S. State Capitals**,
**World Currencies**).

---

## Running the backend

> **Container runtime:** the commands below use `docker compose`. With Colima, run
> `colima start` first; with Docker Desktop, just make sure it's running. If Colima's
> daemon isn't found, export the socket:
> `export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"`.

> **Shortcut:** a root `Makefile` wraps the common dev loop — `make start` (Postgres + backend),
> `make stop`, `make restart`, `make logs`, `make status`, and `make web` (the web dev server).

### Default path (port 5432 free)

```bash
docker compose up -d postgres        # Postgres on :5432
./gradlew :backend:run               # serves at http://0.0.0.0:8080
```

### If you already run Postgres on 5432

Use a different host port and point the backend at it:

```bash
POSTGRES_PORT=5433 docker compose up -d postgres
DB_JDBC_URL=jdbc:postgresql://localhost:5433/flashcards ./gradlew :backend:run
```

The server is ready when it logs `Responding at http://0.0.0.0:8080`. On first boot it
creates its schema and seeds a demo user (`demo@flashcards.dev` / `demo`, granted the **admin**
role), a fixed dev **refresh** token (`demo-token`), the RBAC catalog (roles + permissions), and
the global deck catalog (Flags of the World, National Capitals, U.S. State Capitals, World
Currencies — the geography decks tagged "Geography").

### Smoke-test the API

Auth uses short-lived **access JWTs** plus an opaque **refresh token**, so protected routes
need an access token (a bare `demo-token` won't authenticate). Exchange the seeded refresh
token for one, then call a protected route:

```bash
ACCESS=$(curl -s http://localhost:8080/auth/refresh \
  -H 'Content-Type: application/json' -d '{"refreshToken":"demo-token"}' | jq -r .accessToken)

curl -s "http://localhost:8080/decks" -H "Authorization: Bearer $ACCESS"
# -> the first page of decks, including the seeded Flags of the World deck
```

(Or `POST /auth/login` with the demo credentials to get an `accessToken` the same way.)

### Teardown

```bash
docker compose down       # stop Postgres, keep data
docker compose down -v    # stop + wipe data (fresh seed next run)
colima stop               # stop the VM (Colima only)
```

---

## Running the Android app

The app's base URL is `http://10.0.2.2:8080` (`BuildConfig.BACKEND_BASE_URL`). `10.0.2.2`
is the **Android emulator's** alias for your host machine, so an emulator reaches a
locally-running backend with no extra setup.

1. Start the backend (above).
2. Boot an emulator and install the app:
   ```bash
   ./gradlew :androidApp:installDebug
   ```
   …or just Run the `androidApp` configuration in Android Studio.
3. Register or log in. (Google sign-in appears only if you've configured an OAuth client —
   see below; email/password always works.)

**Physical device:** `10.0.2.2` is emulator-only. Point `BACKEND_BASE_URL` (in
`androidApp/build.gradle.kts`) at your host's LAN IP (e.g. `http://192.168.x.x:8080`), put
the device on the same network, and add that host to
`androidApp/src/main/res/xml/network_security_config.xml` (it only allows cleartext to dev hosts).

---

## Running the iOS app

The Xcode project is **generated from `iosApp/project.yml` by [XcodeGen](https://github.com/yonsm/XcodeGen)** —
`Flashcards.xcodeproj` is not committed. Requires Xcode and `brew install xcodegen`.

```bash
cd iosApp
xcodegen generate          # writes Flashcards.xcodeproj from project.yml
open Flashcards.xcodeproj   # then Run the Flashcards scheme on a simulator
```

Or build/run headlessly:

```bash
xcodebuild -project iosApp/Flashcards.xcodeproj -scheme Flashcards \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

The app links the shared KMP code as a Kotlin/Native framework: a **"Compile Kotlin Framework"**
build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode` (driven by Xcode's
config/SDK/arch env vars) before Swift compiles, so Swift can `import Shared`. The first build is
slower while Gradle compiles the framework. The build phase needs a JDK 17+ on `PATH`/`JAVA_HOME`
(it falls back to `/usr/libexec/java_home`).

The backend URL is the per-config build setting `BACKEND_BASE_URL` in `project.yml` (default
`http://localhost:8080`, which the **simulator** uses to reach a backend on your Mac). For a
physical device, set the Debug `BACKEND_BASE_URL` to your host's LAN IP (e.g.
`http://192.168.x.x:8080`), re-run `xcodegen generate`, and add an App Transport Security
exception for that host (cleartext HTTP).

> Edit the project (targets, build settings, the shared-framework build phase) in
> `project.yml` and re-run `xcodegen generate` — never hand-edit the `.xcodeproj`.
> Xcode must have a simulator runtime matching its SDK; if a build reports
> "No simulator runtime version … available", run `xcodebuild -downloadPlatform iOS`.
> A physical-device build additionally needs the iOS device platform installed and a signing team.

---

## Running the web app

```bash
cd webApp
npm install
cp .env.example .env     # set VITE_API_BASE_URL / VITE_GOOGLE_WEB_CLIENT_ID if needed
npm run dev              # http://localhost:5173
```

The web app calls the backend at `VITE_API_BASE_URL` (default `http://localhost:8080`),
which is allowed by the backend's CORS config out of the box. It covers the same core flows as
the mobile apps — auth, the deck library (title + category search, sort), deck create/edit, all
three practice modes (routed at `/decks/:id/practice`) with the streak + end-of-session review,
card discussions, and guest browsing — and is the only client with the **admin** surfaces:
managing the global deck catalog (`/library/global`), assigning user roles (`/admin/users`), and
the content-moderation queues. Admin screens are gated on the user's permissions and redirect
otherwise.

> The dev server is pinned to **port 5173** (`vite.config.ts`, `strictPort`) because that's the
> origin registered with the Google OAuth client; if 5173 is taken it fails loudly rather than
> drifting to another port (which Google would reject with `origin_mismatch`).

---

## Optional features

The app is fully usable without these. They're opt-in and require resources in **your own**
cloud accounts — never reuse another deployment's identifiers.

### Sign in with Google

Without a client ID, the Google button is hidden (Android, web & iOS) and the backend's
`POST /auth/google` returns `503`; email/password sign-in works regardless.

1. In [Google Cloud Console](https://console.cloud.google.com/apis/credentials), create an
   **OAuth 2.0 Web application** client ID. Add `http://localhost:5173` to its authorized
   JavaScript origins (for the web app).
2. Provide the **Web** client ID to:
   - **Android build + backend:** add it to your user-global `~/.gradle/gradle.properties`
     (see [Configuration](#configuration)).
   - **Web app:** set `VITE_GOOGLE_WEB_CLIENT_ID` in `webApp/.env`.
3. **iOS** (optional): the Google Sign-In SDK on iOS issues an ID token whose audience is an
   **iOS** OAuth client ID, not the Web one. Create an **OAuth 2.0 iOS** client ID (bundle id
   `com.rrbrambley.flashcards`), then:
   - Copy `iosApp/Local.xcconfig.example` to `iosApp/Local.xcconfig` (gitignored) and set
     `GOOGLE_IOS_CLIENT_ID` (the client ID) and `GOOGLE_REVERSED_CLIENT_ID` (its reversed form,
     `com.googleusercontent.apps.<…>`, used as the OAuth callback URL scheme); re-run
     `xcodegen generate`.
   - Set `GOOGLE_IOS_CLIENT_ID` on the **backend** too (in `~/.gradle/gradle.properties` for
     `:backend:run`, or as an env var) — it accepts both the Web and iOS client IDs as valid token
     audiences.

The backend verifies Google ID tokens against the configured client ID(s) — the Web ID (web +
Android) and the iOS ID (iOS) — so they must match between each client and the backend.

### Image uploads (S3 + CloudFront)

Without S3/CloudFront configured, `POST /images` returns `503` and the image picker is
effectively disabled; everything else works. To enable it:

1. Create a **private S3 bucket** in your AWS account.
2. Put a **CloudFront distribution** in front of it (use an Origin Access Control so the
   bucket can stay private). Note the distribution domain → `CDN_BASE_URL`.
3. Create an IAM user/role with `s3:PutObject` on `arn:aws:s3:::<bucket>/images/*`, and make
   its credentials available via the **default AWS chain** — `AWS_ACCESS_KEY_ID` /
   `AWS_SECRET_ACCESS_KEY` env vars or `~/.aws/credentials`. **Never** put AWS secrets in the repo.
4. Set `S3_BUCKET`, `S3_REGION`, and `CDN_BASE_URL` for the backend (see Configuration).

Uploads go **through the backend** (client → `POST /images` → backend validates type/size →
S3 `PutObject` → returns the CloudFront URL), so no browser CORS config is needed on the bucket.
Accepted types: JPEG, PNG, WebP, GIF; max 5 MB.

The curated **profile avatars** (FLA-162) are served from the same CDN under an `avatars/` prefix
(`${CDN_BASE_URL}/avatars/<key>.png`) — source PNGs live in `assets/avatars/`, deployed with
`make avatars`. They degrade gracefully: with no CDN configured the avatar catalog is empty and
clients fall back to an initials monogram.

---

## Configuration

Personal/account-specific values are **not committed**. Set them in your user-global
`~/.gradle/gradle.properties` (Gradle reads it transparently for the Android build and
`:backend:run`) or as environment variables. The web app reads its own `webApp/.env`.

Example `~/.gradle/gradle.properties`:

```properties
GOOGLE_WEB_CLIENT_ID=<your-oauth-web-client-id>.apps.googleusercontent.com
S3_BUCKET=<your-bucket-name>
S3_REGION=<your-bucket-region>
CDN_BASE_URL=https://<your-distribution>.cloudfront.net
```

### Backend (env vars, with defaults from `application.conf`)

| Variable | Default | Purpose |
|----------|---------|---------|
| `PORT` | `8080` | HTTP port |
| `DB_JDBC_URL` | `jdbc:postgresql://localhost:5432/flashcards` | Postgres JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `flashcards` / `flashcards` | Postgres credentials |
| `DB_MAX_POOL_SIZE` | `5` | Hikari pool size |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated browser origins |
| `JWT_SECRET` | *(insecure dev default)* | HMAC256 signing key for access JWTs — **must** be overridden in production |
| `JWT_ISSUER` / `JWT_AUDIENCE` | `flashcards-backend` / `flashcards-clients` | Access-token issuer / audience claims |
| `JWT_ACCESS_TTL_SECONDS` | `900` | Access-token lifetime (15 min) |
| `JWT_REFRESH_TTL_SECONDS` | `2592000` | Refresh-token lifetime (30 days) |
| `RATE_LIMIT_AUTH_LIMIT` | `20` | Max `/auth/*` requests per IP per window |
| `RATE_LIMIT_AUTH_WINDOW_SECONDS` | `60` | Rate-limit window for `/auth/*` |
| `GOOGLE_WEB_CLIENT_ID` | *(unset → Google disabled)* | OAuth Web client ID (web + Android token audience) |
| `GOOGLE_IOS_CLIENT_ID` | *(unset)* | OAuth iOS client ID — additional accepted token audience for iOS sign-in |
| `S3_BUCKET` / `S3_REGION` / `CDN_BASE_URL` | *(unset → uploads disabled)* | Image storage |
| `S3_ENDPOINT` | *(unset → real AWS)* | Override for a local S3-compatible server (e.g. MinIO) |

> AWS credentials are **not** in this table — the backend reads them only from the default
> AWS chain. `:backend:run` also forwards these keys from `gradle.properties` if present.

### Web app (`webApp/.env`, copied from `.env.example`)

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Backend base URL |
| `VITE_GOOGLE_WEB_CLIENT_ID` | *(blank → button hidden)* | OAuth Web client ID |

### Android

`BACKEND_BASE_URL` is set in `androidApp/build.gradle.kts` (`http://10.0.2.2:8080` for the
emulator). `GOOGLE_WEB_CLIENT_ID` is read from Gradle properties into `BuildConfig`; blank
disables Google sign-in.

### iOS

`BACKEND_BASE_URL` is a per-config build setting in `iosApp/project.yml` (surfaced through
`Info.plist`, read via `AppConfig`). The simulator reaches `http://localhost:8080`; for a **device**
build, point `BACKEND_BASE_URL` at your host's LAN IP — the `Info.plist` includes an
`NSAllowsLocalNetworking` App Transport Security exception so the dev cleartext backend works (a
production build should use HTTPS and drop it). `GOOGLE_IOS_CLIENT_ID` / `GOOGLE_REVERSED_CLIENT_ID`
come from the gitignored `iosApp/Local.xcconfig` (template: `Local.xcconfig.example`) — blank/absent
hides the Google button; fill them in (and re-run `xcodegen generate`) to enable it. See
[Sign in with Google](#sign-in-with-google).

The app icon, launch screen (`LaunchLogo` / `LaunchBackground` in `Assets.xcassets`), display name,
and accent color are configured via `project.yml`'s `info:` block + the asset catalog. The bundled
app icon is a **placeholder** — replace `Assets.xcassets/AppIcon.appiconset/icon-1024.png` with final
brand art before release. **Distribution** (TestFlight/App Store) needs a real signing team: set
`DEVELOPMENT_TEAM` in `project.yml` and archive the `Flashcards` scheme (Release).

---

## API

Endpoints require an **access-token JWT** as a bearer except the public routes — the auth
entrypoints (`/auth/register`, `/auth/login`, `/auth/google`, `/auth/refresh`), the read-only
guest **catalog** (`/catalog/*`), and **reading** a card's discussion (`GET /discussions/{cardUid}/messages`).
When an access token expires the client transparently calls `/auth/refresh` and retries. Bodies are
the `@Serializable` DTOs in `shared/api/src/commonMain/.../api/` (the discussion + admin DTOs are
backend-only, mirrored by the web app).

| Method + Path | Purpose |
|---------------|---------|
| `POST /auth/register`, `POST /auth/login` | Issue an access JWT + a refresh token |
| `POST /auth/google` | Exchange a Google ID token for access + refresh tokens (requires `GOOGLE_WEB_CLIENT_ID`) |
| `POST /auth/refresh`, `POST /auth/logout` | Rotate a refresh token / revoke it server-side (end the session) |
| `GET /auth/me`, `PATCH /auth/me` | The caller's identity, roles + permissions / set the display name |
| `GET /decks`, `GET /decks/{id}` | List (cursor-paginated: `?limit&cursor`) / fetch decks (a user's decks + the global catalog) |
| `POST /decks`, `PUT /decks/{id}`, `DELETE /decks/{id}` | Create / update / delete a deck (owner-scoped; global decks writable only by an admin) |
| `GET /decks/global`, `POST /decks/global` | List / create global (ownerless) catalog decks — admin only (`manage_global_decks`) |
| `GET /catalog`, `GET /catalog/{id}` | **Public** — browse the global deck catalog without an account (guest mode) |
| `POST /sessions` | Start or resume a practice session for a deck (in a given `mode`) |
| `GET /sessions?active=`, `GET /sessions/{id}` | List (cursor-paginated) / fetch practice sessions |
| `PATCH /sessions/{id}`, `POST /sessions/{id}/complete` | Update progress / complete a session |
| `POST /sessions/{id}/answers`, `GET /sessions/{id}/answers` | Append to / read the per-card answer log (drives the streak + end-of-session review) |
| `GET /streaks` | The caller's daily practice streak (overall + per deck) |
| `GET /home` | Server-computed home feed |
| `POST /images` | Upload a flashcard image; returns its CloudFront URL (requires S3 config) |
| `GET /discussions/{cardUid}/messages` (public), `POST /discussions/{cardUid}/messages` | Read (paginated) / post a card discussion message (posting is auth-gated) |
| `POST /discussions/messages/{id}/report` | Report a discussion message |
| `PATCH /discussions/{cardUid}/lock`, `PATCH /discussions/decks/{deckId}/discussion` | Lock a thread / toggle discussions for a deck — admin only (`manage_discussions`) |
| `POST /cards/{cardUid}/answer-suggestions` | Suggest a typed answer as also-correct on a global-deck card |
| `GET /admin/users`, `GET /admin/roles` | List users (paginated + `?q=` email search) / the role catalog — admin only (`manage_roles`) |
| `POST /admin/users/{id}/roles`, `DELETE /admin/users/{id}/roles/{key}` | Grant / revoke a user's role — admin only |
| `GET /admin/discussions/reports`, `DELETE /admin/discussions/messages/{id}` | The reports queue / moderator-delete a message — admin only |
| `GET /admin/answer-suggestions`, `POST /admin/answer-suggestions/{id}/accept` (or `…/dismiss`) | Review queue / accept (adds the alternative answer) or dismiss a suggestion — admin only |

> The `/auth/*` routes are rate-limited per client IP (`429` on exceed); discussion posting is
> additionally rate-limited per user per thread. Login/register/google and `/auth/me` carry the
> user's `permissions` so clients can gate admin UI. List endpoints return a `Page<T>` envelope
> (`{ items, nextCursor }`); pass `nextCursor` back as `cursor` for the next page. Admin routes
> (`/admin/*`, `/decks/global`, discussion lock/toggle) require a feature permission and return `403`
> otherwise. Discussion content is plaintext-only, profanity-filtered, and auto-locks past a size threshold.

---

## Building & testing

```bash
./gradlew :androidApp:assembleDebug         # build the Android APK
./gradlew :androidApp:testDebugUnitTest     # Android unit tests
./gradlew :androidApp:connectedDebugAndroidTest  # instrumented tests, incl. the Room migration test (needs a device/emulator)
./gradlew :shared:build                     # build :shared (+ :shared:api) for android/iOS/JVM
./gradlew :shared:jvmTest :shared:api:jvmTest  # shared commonTest on the JVM host
./gradlew :backend:build                    # build the backend
./gradlew ktlintCheck                       # Kotlin lint across all modules
./gradlew :backend:test                     # backend integration tests (Testcontainers)
cd webApp && npm run build && npm run lint && npm run test  # build + lint + test the web app
```

CI (`.github/workflows/ci.yml`) runs ktlint, the JVM unit tests (backend/android/shared) with
coverage, and the web build/lint/tests on every relevant PR (jobs are **path-gated** so each runs
only when its area changed). Two expensive native jobs — **Android instrumented** (emulator;
covers the Room migration test) and **iOS** (a macOS runner: shared iOS tests + an `xcodebuild`
of the app) — are **opt-in**: they run only when their paths changed *and* the PR carries a label
(`ci:android` / `ci:ios` / `ci:native`), or via a manual workflow dispatch.

The backend tests start a real Postgres via Testcontainers, so they need Docker. Under
Colima they additionally need:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export DOCKER_API_VERSION=1.44     # Colima's engine requires API >= 1.44
./gradlew :backend:test
```

A one-time `~/.testcontainers.properties` with `docker.host=unix://$HOME/.colima/default/docker.sock`
also works.

---

## Contributing & security

Found a bug or have an idea? Open a
[GitHub issue](https://github.com/rrbrambley/Flashcards/issues/new/choose) and pick the **Bug** or
**Feature / improvement** template. Because this is a KMP monorepo, note which surfaces are affected
(backend, `:shared`, web, Android, iOS) — larger multi-surface features are usually tracked as an
**epic issue with a per-platform checklist**. Roadmap and known work live in
[Issues](https://github.com/rrbrambley/Flashcards/issues).

See [CONTRIBUTING.md](CONTRIBUTING.md) for conventions and the pre-PR checklist, and
[SECURITY.md](SECURITY.md) for how secrets are handled and how to report a vulnerability (please
**don't** file security issues as public GitHub issues).

## License

[MIT](LICENSE) © Rob Brambley
