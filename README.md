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
| Web app (React/TypeScript) — auth, library, deck create/edit | ✅ Done |
| iOS app (SwiftUI) | ⏳ Not started |
| Logout, web practice screen | ⏳ Planned |

## Repository layout

A single Gradle build with three modules, plus a separate web app:

```
Flashcards/
├── shared/        KMP library — @Serializable API DTOs + a Ktor HTTP client
│                  (FlashcardApiClient), targeting android, iOS, and jvm.
│                  Consumed by androidApp and the backend.
├── androidApp/    Android app (Jetpack Compose, MVVM, Hilt, Room). Offline-first.
├── backend/       Ktor server (Netty) + Exposed + Postgres. The API the apps sync against.
├── webApp/        React + TypeScript + Vite SPA (its own npm toolchain, not in the Gradle build).
├── docker-compose.yml          Local Postgres for the backend
└── gradle/libs.versions.toml   Version catalog (single source for deps)
```

Reserved for a later phase: `iosApp/`.

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
creates its schema and seeds a demo user, a fixed dev token (`demo-token`), and the global
"Country Flags" deck.

### Smoke-test the API

```bash
curl -s http://localhost:8080/decks -H "Authorization: Bearer demo-token"
# -> the seeded Country Flags deck
```

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

## Running the web app

```bash
cd webApp
npm install
cp .env.example .env     # set VITE_API_BASE_URL / VITE_GOOGLE_WEB_CLIENT_ID if needed
npm run dev              # http://localhost:5173
```

The web app calls the backend at `VITE_API_BASE_URL` (default `http://localhost:8080`),
which is allowed by the backend's CORS config out of the box. Practice isn't implemented on
the web yet — tapping a deck opens it for editing.

---

## Optional features

The app is fully usable without these. They're opt-in and require resources in **your own**
cloud accounts — never reuse another deployment's identifiers.

### Sign in with Google

Without a client ID, the Google button is hidden (Android & web) and the backend's
`POST /auth/google` returns `503`; email/password sign-in works regardless.

1. In [Google Cloud Console](https://console.cloud.google.com/apis/credentials), create an
   **OAuth 2.0 Web application** client ID. Add `http://localhost:5173` to its authorized
   JavaScript origins (for the web app).
2. Provide the **same** client ID to all three places:
   - **Android build + backend:** add it to your user-global `~/.gradle/gradle.properties`
     (see [Configuration](#configuration)).
   - **Web app:** set `VITE_GOOGLE_WEB_CLIENT_ID` in `webApp/.env`.

The backend verifies Google ID tokens against this client ID, so it must match across clients.

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
| `GOOGLE_WEB_CLIENT_ID` | *(unset → Google disabled)* | OAuth Web client ID to verify ID tokens |
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

---

## API

All endpoints require a bearer token except register/login. Bodies are the `@Serializable`
DTOs in `shared/src/commonMain/.../api/`.

| Method + Path | Purpose |
|---------------|---------|
| `POST /auth/register`, `POST /auth/login` | Issue a bearer token |
| `POST /auth/google` | Exchange a Google ID token for a bearer token (requires `GOOGLE_WEB_CLIENT_ID`) |
| `GET /decks`, `GET /decks/{id}` | List / fetch decks (a user's decks + the global catalog) |
| `POST /decks`, `PUT /decks/{id}` | Create / update a deck (owner-scoped; the seeded deck is read-only) |
| `POST /sessions` | Start or resume a practice session for a deck |
| `GET /sessions?active=true`, `GET /sessions/{id}` | List / fetch practice sessions |
| `PATCH /sessions/{id}`, `POST /sessions/{id}/complete` | Update progress / complete a session |
| `GET /home` | Server-computed home feed |
| `POST /images` | Upload a flashcard image; returns its CloudFront URL (requires S3 config) |

---

## Building & testing

```bash
./gradlew :androidApp:assembleDebug         # build the Android APK
./gradlew :androidApp:testDebugUnitTest     # Android unit tests
./gradlew :shared:build                     # build shared for android/iOS/JVM
./gradlew :backend:build                    # build the backend
./gradlew :backend:test                     # backend integration tests (Testcontainers)
cd webApp && npm run build && npm run lint  # build + lint the web app
```

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
