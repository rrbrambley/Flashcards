# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Flashcards is a Kotlin Multiplatform (KMP) monorepo for a self-testing flashcards product. It contains the Android app (`androidApp/`, Compose), a Ktor backend (`backend/`), a shared KMP library (`shared/`), and a React/TypeScript web app (`webApp/`). Reserved for a future phase: `iosApp/` (SwiftUI). The product supports email/password and Google sign-in, deck create/edit (with optional front-of-card images uploaded to S3 + CloudFront), and practice sessions; the Android app is offline-first and syncs to the backend.

The repo is one Gradle build with three modules: `:androidApp` (the AGP application), `:backend` (Ktor/Netty JVM server), and `:shared` (KMP library targeting `androidTarget`, the three iOS targets, and `jvm`). Both `:androidApp` and `:backend` depend on `:shared` for the API DTOs and HTTP client. **`webApp/` is NOT part of the Gradle build** — it has its own npm/Vite toolchain.

For setup, optional Google/AWS configuration, and the env-var reference, see `README.md`. Secrets are never committed (see `SECURITY.md`): per-developer config lives in `~/.gradle/gradle.properties` (Android + `:backend:run`) and `webApp/.env` (web), both untracked.

## Commands

Gradle tasks run via the wrapper from the repo root and are module-qualified.

```bash
# Android
./gradlew :androidApp:assembleDebug          # Build the debug APK
./gradlew :androidApp:installDebug           # Build + install on a connected device/emulator
./gradlew :androidApp:testDebugUnitTest      # Android unit tests (androidApp/src/test)
./gradlew :androidApp:connectedAndroidTest   # Instrumented tests (needs a device/emulator)
./gradlew :androidApp:lint                   # Android Lint

# Backend (needs Postgres; see README for docker-compose + DB_JDBC_URL)
./gradlew :backend:run                       # Run the server at http://0.0.0.0:8080
./gradlew :backend:build                     # Build the backend
./gradlew :backend:test                      # Integration tests (Testcontainers → real Postgres, needs Docker)

# shared KMP module
./gradlew :shared:build                      # Build for android/iOS/JVM
./gradlew :shared:jvmTest                    # Run commonTest on the JVM host
./gradlew :shared:compileKotlinMetadata      # Type-check commonMain (expect/actual)

./gradlew test                               # All JVM unit tests across modules

# Web app (separate npm toolchain, from webApp/)
cd webApp && npm install && npm run dev      # Dev server at http://localhost:5173
cd webApp && npm run build && npm run lint   # Production build + lint
```

Run a single unit test class or method:

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.rrbrambley.flashcards.practice.ui.FlashcardsViewModelTest"
./gradlew :androidApp:testDebugUnitTest --tests "*.HomeViewModelTest.uiState_startsAsLoading"
./gradlew :backend:test --tests "*.ApplicationFlowTest"
```

Dependencies are managed through the version catalog at `gradle/libs.versions.toml` — add/upgrade libraries and plugins there, not inline in a module's `build.gradle.kts`. `local.properties` (SDK path) is gitignored and machine-specific.

> **Backend tests need Docker.** Under Colima, also export `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`, `DOCKER_API_VERSION=1.44`, and `TESTCONTAINERS_RYUK_DISABLED=true` (the `:backend:test` task forwards these into the test workers). Ryuk must be disabled because it can't bind-mount Colima's socket path (`operation not supported`); leaving it on makes every Testcontainers test fail at container startup. The local Postgres for `:backend:run` is started via `docker-compose.yml`; if 5432 is taken, run with `POSTGRES_PORT=5433` and pass `DB_JDBC_URL=jdbc:postgresql://localhost:5433/flashcards`.

## The `shared` module

`shared/` is a KMP library with source sets `commonMain` / `androidMain` / `iosMain` / `jvmMain` (plus `commonTest`), namespace `com.rrbrambley.flashcards.shared`, `compileSdk = 36` / `minSdk = 26`. Its real content is the **HTTP API contract** in `commonMain/.../api/`: the `@Serializable` DTOs (`AuthDto`, `FlashcardDeckDto`, `FlashcardDto`, `PracticeSessionDto`, `HomeFeedDto`, `ImageDto`, the generic `Page<T>` envelope, the request/error DTOs) **and `FlashcardApiClient`** — a Ktor-client wrapper with one suspend function per endpoint (register/login/google/**refresh/logout**, decks CRUD, sessions, home, `uploadImage`). `GET /decks` and `GET /sessions` are **cursor-paginated** — they return `Page<T>` (`{ items, nextCursor }`) and the client adds `getAllDecks()`/`getAllSessions()` helpers that walk every page. `HttpClientFactory` exposes `createFlashcardHttpClient(engine)` (with timeout + retry + a response validator that maps non-2xx to a typed `ApiError`) so each platform supplies its own engine. The backend and Android both consume these DTOs; iOS will reuse `FlashcardApiClient` later. (A `Platform`/`Greeting` expect/actual placeholder also remains.) iOS framework *linking* requires Xcode; the iOS klib compiles headlessly.

## Architecture (androidApp)

Package-by-feature MVVM under `androidApp/src/main/java/com/rrbrambley/flashcards/`. Feature packages (`home`, `library`, `create`, `edit`, `practice`, `auth`) split into `ui` / `domain` / `data` sub-packages:

- **ui** — Compose screen(s), a `@HiltViewModel`, and a sealed-class/data-class `*UiState`. ViewModels expose a single `StateFlow<*UiState>` via `MutableStateFlow` + `asStateFlow()`; UI states are modeled as sealed classes (e.g. `FlashcardsUiState.Loading / ShowFlashcard / SessionCompleted / LoadingFailed`).
- **domain** — repository interfaces and plain Kotlin models, no Android/framework types.
- **data** — repository implementations, Room DAOs/entities, data sources, and the remote sync via the shared `FlashcardApiClient`.

The app is **offline-first**: ViewModels read from Room (`Flow`), and repositories sync with the backend through `FlashcardApiClient`, caching responses into Room keyed by backend ids (`@Upsert`). Data flows one direction: `Compose screen → ViewModel → Repository (interface in domain) → DataSource (Room DAO + remote client)`. Repository impls map Room `*Entity`/`*WithCards` rows to domain models (`toDomain()`); they never leak entities upward.

### Cross-cutting structure (important, non-obvious)

- The **top-level `domain` package** (`Flashcard`, `FlashcardDeck`, `FlashcardRepository`) holds models/interfaces shared across features. But the shared **Room database, DAOs, entities, and `FlashcardRepositoryImpl` all live under `practice/data/`** — the `practice` feature is the de facto home of shared persistence infrastructure, not a self-contained feature. `FlashcardsDatabase` (`practice/data/FlashcardsDatabase.kt`) registers all entities (`FlashcardDeckEntity`, `FlashcardEntity`, `PracticeSessionEntity`).
- Repositories are bound to interfaces via `@Binds` in `di/DataModule.kt`. When adding a repository, add its `@Binds` there. `di/DatabaseModule.kt` provides the DB + DAOs. `di/NetworkModule.kt` provides the Ktor `HttpClient` (OkHttp engine) and the shared `FlashcardApiClient`, configured with `BuildConfig.BACKEND_BASE_URL` and a `tokenProvider` backed by `data/auth/TokenStore` (DataStore).
- The Room DB uses **real migrations** (`practice/data/Migrations.kt`, registered via `addMigrations(*ALL_MIGRATIONS)`); `exportSchema = true` writes versioned schemas to `androidApp/schemas/`. `fallbackToDestructiveMigration` is applied **only in debug builds** — release builds must migrate. To change the schema: bump `version` in `FlashcardsDatabase`, build (KSP exports `schemas/.../N.json`), add a `Migration(N-1, N)` to `ALL_MIGRATIONS`, and extend `FlashcardsDatabaseMigrationTest` (an instrumented test run on the CI emulator). Adding a `@Query` is not a schema change and needs no migration.
- `core/StringProvider.kt` (+ `AndroidStringProvider`, bound in `DataModule`) resolves string resources outside Compose (ViewModels/repositories), where `stringResource()` isn't available; tests use a `FakeStringProvider`. User-facing copy lives in `res/values/strings.xml`.

### Auth

The `auth` package (`AuthRepository`, `AuthViewModel`, `GoogleSignIn`, `ui/AuthScreens.kt`) handles email/password + Google sign-in (Credential Manager + `googleid`). Auth is a **short-lived access JWT + an opaque, revocable refresh token**. The **`TokenStore` interface and the transparent refresh-on-401 flow both live in `:shared`** (`shared/.../api/TokenStore.kt` + `TokenRefreshAuth.kt`): `installTokenRefreshAuth(tokenStore, baseUrl)` is installed via the client factory's `configure` hook (in `NetworkModule`) and installs the Ktor client `Auth` (bearer) plugin so an expired access token is transparently refreshed on `401` (single-flight) and the request retried; logout revokes the refresh token server-side. Android supplies the storage: `data/auth/DataStoreTokenStore` (DataStore impl of the shared `TokenStore`); iOS will add a Keychain impl and reuse the same refresh flow. Launch is gated on having a token. `BuildConfig.GOOGLE_WEB_CLIENT_ID` (from Gradle properties) being blank disables the Google button.

### Navigation (no Navigation-Compose graph)

Despite depending on `navigation-compose`, the app navigates via **`Activity` + `Intent`**, not a `NavHost`:

- `MainActivity` is the launcher. It hosts a single `Scaffold` with a bottom `NavigationBar` whose three destinations (`Home`, `New`, `Library`) are an in-Composable `enum` + `rememberSaveable` state — switching tabs swaps the body Composable, it does not navigate.
- `FlashcardsActivity` (practice) and `EditDeckActivity` (edit) are separate `@AndroidEntryPoint` activities launched via `Intent` from `MainActivity`. Data is passed as intent extras using constants on each activity's companion object: `FlashcardsActivity.SESSION_ID_EXTRA`, `EditDeckActivity.DECK_ID_EXTRA`.

### DI & app setup

Hilt throughout (`@HiltAndroidApp` on `FlashcardsApplication`, `@AndroidEntryPoint` on every Activity, `@HiltViewModel` ViewModels obtained via `hiltViewModel()`). `FlashcardsApplication` also wires Coil 3's `SingletonImageLoader.Factory` (via `FlashcardsImageLoaderFactory`) for remote/SVG card images.

## Architecture (backend)

Ktor (Netty) under `backend/src/main/kotlin/com/rrbrambley/flashcards/backend/`. Feature packages each pair a `*Routes.kt` (HTTP layer, validates input and maps to/from shared DTOs) with a `*Repository.kt`/`*Service.kt` (Exposed persistence / logic): `auth`, `decks`, `sessions`, `home`, `images`, `health`. `Application.kt` is the entry point; `plugins/` configures `Routing`, `Security` (**JWT** bearer auth, `BEARER_AUTH`, with a `WWW-Authenticate` challenge so clients know to refresh), `Serialization`, `Cors`, `StatusPages`, `Monitoring` (MDC request/user ids), `RequestLimits`, and `RateLimiting` (per-IP throttle on `/auth/*`). Auth issues short-lived access JWTs (`auth/TokenService`) plus opaque refresh tokens persisted in `RefreshTokens` (rotated on `/auth/refresh`, with reuse detection that revokes the session); `/auth/logout` deletes the row. `db/` holds `DatabaseFactory` (HikariCP + Exposed, schema creation via `createMissingTablesAndColumns` + first-boot seed of the demo user, the demo *refresh* token `demo-token`, and the global Country Flags deck) and `Tables.kt`. `mapping/Mappers.kt` converts Exposed rows ↔ shared DTOs. `error/Exceptions.kt` + `StatusPages` map domain exceptions to HTTP codes (e.g. 400, 413 `PayloadTooLarge`, 415 `UnsupportedMediaType`, 429 from the rate limiter, 503 when an optional feature is unconfigured).

- **Pagination:** `GET /decks` (id desc) and `GET /sessions` (updatedAt+id desc) read `limit`/`cursor` query params (`routes/Pagination.kt`, default 20 / max 100, opaque base64 cursor) and respond with the shared `Page<T>`.
- **Config:** `resources/application.conf` reads env vars with `${?VAR}` (DB, `JWT_*`, `RATE_LIMIT_AUTH_*`, `GOOGLE_WEB_CLIENT_ID`, `CORS_ALLOWED_ORIGINS`, `S3_*`/`CDN_BASE_URL`, optional `S3_ENDPOINT`). `JWT_SECRET` has an insecure dev default that must be overridden in production. Unset optional groups degrade gracefully: no `GOOGLE_WEB_CLIENT_ID` → `/auth/google` 503; no S3 bucket/CDN → `/images` 503. The `:backend:run` task forwards these keys from `gradle.properties`/`~/.gradle/gradle.properties` so a plain run works locally.
- **Storage:** `storage/StorageService.kt` (interface) + `S3StorageService.kt` (AWS SDK for Kotlin S3 client). `images/ImageRoutes.kt` does multipart upload (5 MB cap; JPEG/PNG/WebP/GIF), `PutObject`s to `images/<uuid>.<ext>`, and returns the `CDN_BASE_URL`-prefixed URL. **AWS credentials come only from the default AWS chain** (`~/.aws` or env), never config.

## Architecture (webApp)

React 19 + TypeScript + Vite SPA in `webApp/` (react-router). `src/api/` has hand-written TS mirrors of the shared DTOs (`types.ts`, including `Page<T>`) and a fetch-based `client.ts` (access + refresh tokens in `localStorage`, with single-flight transparent refresh on `401`). `src/auth/` handles email/password + Google sign-in (Google Identity Services); `src/decks/` is the library (title search + A–Z/recently-practiced sort + cursor "Load more") + create/edit (`DeckForm` mirrors the Android fields, including front-of-card image upload via `POST /images`). Config comes from `import.meta.env` (`VITE_API_BASE_URL`, `VITE_GOOGLE_WEB_CLIENT_ID`) loaded from `webApp/.env` (gitignored; template in `.env.example`). `src/practice/` is the practice flow (a card-flip + reducer, parity with Android), routed at `/decks/:id/practice`. The dev server is pinned to port 5173 (`vite.config.ts`, `strictPort`) to match the registered Google OAuth origin. Tests use Vitest (`npm run test`).

## Testing conventions

JVM unit tests in `androidApp/src/test` focus on ViewModels and repositories with **hand-written fakes** that implement the domain interfaces (e.g. `FakeHomeRepository` nested in the test, `FakeStringProvider`) — no mocking framework. Repository tests that need the real `FlashcardApiClient` back it with a Ktor `MockEngine`. Coroutine/Flow tests use `kotlinx-coroutines-test`: `StandardTestDispatcher` + `Dispatchers.setMain/resetMain` in `@Before`/`@After`, `runTest`, and `scheduler.advanceUntilIdle()` to flush. Backend tests (`backend/src/test`, e.g. `ApplicationFlowTest`) drive the full app over HTTP against a **real Postgres via Testcontainers**, with a fake `StorageService` injected for image tests (no AWS needed). **Instrumented** tests live in `androidApp/src/androidTest` — notably `FlashcardsDatabaseMigrationTest` (Room `MigrationTestHelper` against the exported schemas); they run on an emulator (locally via `:androidApp:connectedDebugAndroidTest`, and in CI's emulator job), not in the JVM unit-test run. Web tests are Vitest (`webApp/src/**/*.test.ts(x)`).

## Notes

- `androidApp`: `minSdk = 26`, `compileSdk/targetSdk = 36`, Java/JVM 11. `shared` matches (`compileSdk = 36`, `minSdk = 26`); `backend` is JVM toolchain 11. Kotlin 2.2.10, Ktor 3.2.x (pinned — newer Ktor/serialization versions break the Kotlin/Native 2.2.10 iOS klibs).
