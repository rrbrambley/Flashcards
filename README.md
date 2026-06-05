# Flashcards

A multiplatform flashcards app for self-testing across subjects. The goal is a shared
product across **Android, iOS, web, and a backend**, sharing business logic via Kotlin
Multiplatform (KMP) while keeping a native UI per platform.

You can build decks of cards (term + definition, with an optional image on the front),
practice them in spaced sessions, and sync across devices through the backend. The Android
app is offline-first; the web app is a thin client over the same API.

## Status

| Component | State |
|-----------|-------|
| KMP monorepo (`shared` library) | ✅ Done |
| Ktor + Postgres backend (auth, decks, sessions, home feed, image uploads) | ✅ Done |
| Android app — Compose, offline-first, synced to the backend | ✅ Done |
| Email/password + Google sign-in (Android & web) | ✅ Done |
| Flashcard images (front side), uploaded to S3 and served via CloudFront | ✅ Done |
| Web app (React/TypeScript) — auth, library (search + sort), deck create/edit, practice | ✅ Done |
| JWT access + refresh tokens, transparent refresh-on-401, logout (server-side revocation) | ✅ Done |
| iOS app (SwiftUI) | ⏳ Not started |

## Repository layout

A single Gradle build with three modules, plus a separate web app:

```
Flashcards/
├── shared/        KMP library — @Serializable API DTOs + a Ktor HTTP client
│                  (FlashcardApiClient), targeting android, iOS, and jvm.
│                  Consumed by androidApp and the backend.
├── androidApp/    Android app (Jetpack Compose, MVVM, Hilt, Room). Offline-first.
├── iosApp/        SwiftUI app consuming the shared framework (XcodeGen project, not in the Gradle build).
├── backend/       Ktor server (Netty) + Exposed + Postgres. The API the apps sync against.
├── webApp/        React + TypeScript + Vite SPA (its own npm toolchain, not in the Gradle build).
├── docker-compose.yml          Local Postgres for the backend
└── gradle/libs.versions.toml   Version catalog (single source for deps)
```

The shared offline-first data layer (domain, Room-KMP database, repositories) and the Ktor
client + token refresh live in `shared/` and are reused by both apps; iOS gets a one-call
`createIosFlashcardSdk(...)` factory. The `iosApp/` SwiftUI app is under active development.

## Tech stack

- **Language:** Kotlin 2.2.10 (JVM toolchain 11), Kotlin Multiplatform
- **Android:** Jetpack Compose, Hilt (DI), Room (local cache), Ktor client (OkHttp),
  DataStore, Coil (images), Credential Manager (Google sign-in)
- **Backend:** Ktor 3.2.x, Exposed + HikariCP, PostgreSQL, BCrypt, kotlinx.serialization,
  AWS SDK for Kotlin (S3)
- **Web:** React 19 + TypeScript + Vite
- **Shared:** kotlinx.serialization DTOs + a Ktor `FlashcardApiClient` reused across platforms

## Prerequisites

- **JDK 11+** and the Android SDK (easiest via [Android Studio](https://developer.android.com/studio)).
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
(`demo@flashcards.dev` / `demo`). The backend ships a seeded **Country Flags** deck.

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
creates its schema and seeds a demo user (`demo@flashcards.dev` / `demo`), a fixed dev
**refresh** token (`demo-token`), and the global "Country Flags" deck.

### Smoke-test the API

Auth uses short-lived **access JWTs** plus an opaque **refresh token**, so protected routes
need an access token (a bare `demo-token` won't authenticate). Exchange the seeded refresh
token for one, then call a protected route:

```bash
ACCESS=$(curl -s http://localhost:8080/auth/refresh \
  -H 'Content-Type: application/json' -d '{"refreshToken":"demo-token"}' | jq -r .accessToken)

curl -s "http://localhost:8080/decks" -H "Authorization: Bearer $ACCESS"
# -> the first page of decks, including the seeded Country Flags deck
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
which is allowed by the backend's CORS config out of the box. It has parity with the Android
client's core flows: auth, the deck library (with title search + sort), deck create/edit, and
a practice screen (routed at `/decks/:id/practice`).

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

Endpoints require an **access-token JWT** as a bearer except the public auth routes
(`/auth/register`, `/auth/login`, `/auth/google`, `/auth/refresh`). When an access token
expires the client transparently calls `/auth/refresh` and retries. Bodies are the
`@Serializable` DTOs in `shared/src/commonMain/.../api/`.

| Method + Path | Purpose |
|---------------|---------|
| `POST /auth/register`, `POST /auth/login` | Issue an access JWT + a refresh token |
| `POST /auth/google` | Exchange a Google ID token for access + refresh tokens (requires `GOOGLE_WEB_CLIENT_ID`) |
| `POST /auth/refresh` | Exchange a refresh token for a fresh access token (rotates the refresh token) |
| `POST /auth/logout` | Revoke a refresh token server-side (ends the session) |
| `GET /decks`, `GET /decks/{id}` | List (cursor-paginated: `?limit&cursor`) / fetch decks (a user's decks + the global catalog) |
| `POST /decks`, `PUT /decks/{id}`, `DELETE /decks/{id}` | Create / update / delete a deck (owner-scoped; the seeded deck is read-only) |
| `POST /sessions` | Start or resume a practice session for a deck |
| `GET /sessions?active=`, `GET /sessions/{id}` | List (cursor-paginated) / fetch practice sessions |
| `PATCH /sessions/{id}`, `POST /sessions/{id}/complete` | Update progress / complete a session |
| `GET /home` | Server-computed home feed |
| `POST /images` | Upload a flashcard image; returns its CloudFront URL (requires S3 config) |

> The `/auth/*` routes are rate-limited per client IP (`429` on exceed). List endpoints return a
> `Page<T>` envelope (`{ items, nextCursor }`); pass `nextCursor` back as `cursor` for the next page.

---

## Building & testing

```bash
./gradlew :androidApp:assembleDebug         # build the Android APK
./gradlew :androidApp:testDebugUnitTest     # Android unit tests
./gradlew :androidApp:connectedDebugAndroidTest  # instrumented tests, incl. the Room migration test (needs a device/emulator)
./gradlew :shared:build                     # build shared for android/iOS/JVM
./gradlew :shared:jvmTest                   # shared commonTest on the JVM host
./gradlew :backend:build                    # build the backend
./gradlew :backend:test                     # backend integration tests (Testcontainers)
cd webApp && npm run build && npm run lint && npm run test  # build + lint + test the web app
```

CI (`.github/workflows/ci.yml`) runs ktlint, the JVM unit tests (backend/android/shared) with
coverage, the web build/lint/tests, and the **instrumented tests on an emulator** (which covers
the Room migration test).

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

See [CONTRIBUTING.md](CONTRIBUTING.md) for conventions and the pre-PR checklist, and
[SECURITY.md](SECURITY.md) for how secrets are handled and how to report a vulnerability.

## License

[MIT](LICENSE) © Rob Brambley
