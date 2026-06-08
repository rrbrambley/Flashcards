# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Flashcards is a Kotlin Multiplatform (KMP) monorepo for a self-testing flashcards product. It contains the Android app (`androidApp/`, Compose), a Ktor backend (`backend/`), a shared KMP library (`shared/`), a React/TypeScript web app (`webApp/`), and an in-progress SwiftUI iOS app (`iosApp/`) consuming the shared framework. The product supports email/password and Google sign-in, deck create/edit (with optional front-of-card images uploaded to S3 + CloudFront), and practice sessions; the Android app is offline-first and syncs to the backend.

The repo is one Gradle build with three modules: `:androidApp` (the AGP application), `:backend` (Ktor/Netty JVM server), and `:shared` (KMP library targeting `androidTarget`, the three iOS targets, and `jvm`). Both `:androidApp` and `:backend` depend on `:shared` for the API DTOs and HTTP client. **`webApp/` and `iosApp/` are NOT part of the Gradle build** — `webApp/` has its own npm/Vite toolchain; `iosApp/` is an Xcode project (it pulls the `Shared` framework from `:shared` via an Xcode build phase).

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
./gradlew :backend:admin --args="user list"  # Operator admin CLI (or: make admin ARGS="user list")

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

`shared/` is a KMP library with source sets `commonMain` / `androidMain` / `iosMain` / `jvmMain` (plus `commonTest`), namespace `com.rrbrambley.flashcards.shared`, `compileSdk = 36` / `minSdk = 26`. It holds two things: the **HTTP API contract** and the **offline-first data layer** (both shared across Android and iOS).

- **API contract** (`commonMain/.../api/`): the `@Serializable` DTOs (`AuthDto`, `FlashcardDeckDto`, `FlashcardDto`, `PracticeSessionDto`, `HomeFeedDto`, `ImageDto`, the generic `Page<T>` envelope, the request/error DTOs) **and `FlashcardApiClient`** — a Ktor-client wrapper with one suspend function per endpoint (register/login/google/refresh/logout, decks CRUD, sessions, home, `uploadImage`). `GET /decks` and `GET /sessions` are **cursor-paginated** (`Page<T>` = `{ items, nextCursor }`; the client adds `getAllDecks()`/`getAllSessions()` helpers that walk every page). `HttpClientFactory.createFlashcardHttpClient(engine, configure)` adds timeout + retry + a response validator mapping non-2xx to a typed `ApiError`, taking a per-platform engine.
- **Data layer** (`commonMain`): `shared/domain/` holds the domain models (`Flashcard`, `FlashcardDeck`, `PracticeSession`, the home-feed models) + repository interfaces (`FlashcardRepository`, `PracticeSessionRepository`, `HomeRepository`) + the `HomeFeedStrings` interface. `practice/data/` holds the **Room-KMP** database — entities, DAOs, `FlashcardsDatabase` (`@ConstructedBy`), `Migrations` (KMP `Migration.migrate(SQLiteConnection)`) — plus `FlashcardRepositoryImpl`/`PracticeSessionRepositoryImpl`; `home/data/HomeRepositoryImpl` and `data/mapping/` (entity/DTO mappers) round it out. The DB runs on the **bundled SQLite driver** via a per-platform `RoomDatabase.Builder` factory (`flashcardsDatabaseBuilder`, Android `Context` / iOS Documents dir) finalized by `createFlashcardsDatabase(builder)`. Schemas export to `shared/schemas/`; repository + mapper tests live in `commonTest` (run on JVM + iOS).

- **Auth + composition** (`commonMain`): the `TokenStore` interface and the transparent refresh-on-401 bearer flow live in `api/` (`TokenStore.kt`, `TokenRefreshAuth.kt` — `installTokenRefreshAuth(tokenStore, baseUrl)`, installed via the client factory's `configure` hook). `api/FlashcardApiClientFactory.kt` (`createFlashcardApiClient(engine, baseUrl, tokenStore)`) composes the client + refresh; `shared/FlashcardSdk.kt` (`buildFlashcardSdk`) wires the three repositories over an `apiClient` + `FlashcardsDatabase`. **iOS** gets a one-call entry point in `iosMain` — `createIosFlashcardSdk(baseUrl, tokenStore, homeFeedStrings)` (`shared/IosFlashcardSdk.kt`) builds everything over the **Darwin** (`ktor-client-darwin`) engine + the iOS Room builder; `DefaultHomeFeedStrings` supplies English copy until iOS localizes.

The backend consumes the DTOs + client; Android consumes everything (assembling the repositories via Hilt rather than `buildFlashcardSdk`); iOS reuses the same layer through `createIosFlashcardSdk`. Room uses **KSP 2.3.9** (decoupled KSP2 — the first to support AGP 9's KMP-library android target). (A `Platform`/`Greeting` expect/actual placeholder also remains.) iOS framework *linking* requires Xcode; the iOS klib compiles headlessly.

## Architecture (androidApp)

Package-by-feature MVVM under `androidApp/src/main/java/com/rrbrambley/flashcards/`. The **domain models, repository interfaces + implementations, and the Room database all live in `:shared`** (see "The `shared` module"); androidApp holds the Compose UI + ViewModels and a few Android-only pieces. Feature packages (`home`, `library`, `create`, `edit`, `practice`, `auth`) are mostly a **`ui`** sub-package:

- **ui** — Compose screen(s), a `@HiltViewModel`, and a sealed-class/data-class `*UiState`. ViewModels expose a single `StateFlow<*UiState>` via `MutableStateFlow` + `asStateFlow()`; UI states are modeled as sealed classes (e.g. `FlashcardsUiState.Loading / ShowFlashcard / SessionCompleted / LoadingFailed`). ViewModels depend on the **shared** repository interfaces.
- Android-only data bits remain: `data/auth/DataStoreTokenStore` (DataStore impl of the shared `TokenStore`; the refresh flow itself is shared), `data/image/` (`AndroidImageUploader`), `home/data/AndroidHomeFeedStrings`, and `auth/` (`DefaultAuthRepository`, `GoogleSignIn`).

The app is **offline-first**: ViewModels read from the shared repositories (`Flow`), which sync with the backend through `FlashcardApiClient` and cache responses into the shared Room DB keyed by backend ids (`@Upsert`). Data flows one direction: `Compose screen → ViewModel → shared Repository → shared DAO (Room) + FlashcardApiClient`. Repository impls map Room `*Entity`/`*WithCards` rows to domain models (`toDomain()`); they never leak entities upward.

### Cross-cutting structure (important, non-obvious)

- The data layer lives in `:shared` — domain models/interfaces in `shared/domain`, the Room DB/entities/DAOs/migrations + repository impls + mappers in `practice/data` / `home/data` / `data/mapping` of commonMain (the package names are kept across the module boundary, so the persistence package is `com.rrbrambley.flashcards.practice.data`).
- Android DI: `di/DatabaseModule.kt` builds the shared `FlashcardsDatabase` via `flashcardsDatabaseBuilder(context, …)` + `createFlashcardsDatabase` and provides the DAOs. `di/RepositoryModule.kt` **`@Provides`** the shared repository impls (they can't be `@Inject`-bound — the shared module has no Hilt/`javax.inject`). `di/DataModule.kt` `@Binds` the Android-only impls (`ImageUploader`, `TokenStore`, `AuthRepository`, `StringProvider`, `HomeFeedStrings`). `di/NetworkModule.kt` provides the Ktor `HttpClient` (OkHttp engine) and the shared `FlashcardApiClient`, configured with `BuildConfig.BACKEND_BASE_URL` and a `tokenProvider` backed by `data/auth/TokenStore` (DataStore).
- The Room DB uses **real migrations** (`shared/.../practice/data/Migrations.kt`, the KMP `Migration.migrate(SQLiteConnection)` API, registered by `createFlashcardsDatabase`); `exportSchema = true` writes versioned schemas to `shared/schemas/`. `fallbackToDestructiveMigration` is applied **only in debug builds** — release builds must migrate. To change the schema: bump `version` in `FlashcardsDatabase`, build (KSP exports `shared/schemas/.../N.json`), add a `Migration(N-1, N)` to `ALL_MIGRATIONS`, and extend `FlashcardsDatabaseMigrationTest` (an instrumented test in androidApp/androidTest, run on the CI emulator). Adding a `@Query` is not a schema change and needs no migration.
- `core/StringProvider.kt` (+ `AndroidStringProvider`, bound in `DataModule`) resolves string resources outside Compose (ViewModels), where `stringResource()` isn't available; tests use a `FakeStringProvider`. The home offline-fallback copy is served through the shared `HomeFeedStrings` interface, implemented by `AndroidHomeFeedStrings` (backed by `StringProvider`). User-facing copy lives in `res/values/strings.xml`.

### Auth

The `auth` package (`AuthRepository`, `AuthViewModel`, `GoogleSignIn`, `ui/AuthScreens.kt`) handles email/password + Google sign-in (Credential Manager + `googleid`). Auth is a **short-lived access JWT + an opaque, revocable refresh token**. The **`TokenStore` interface and the transparent refresh-on-401 flow both live in `:shared`** (`shared/.../api/TokenStore.kt` + `TokenRefreshAuth.kt`): `installTokenRefreshAuth(tokenStore, baseUrl)` is installed via the client factory's `configure` hook (in `NetworkModule`) and installs the Ktor client `Auth` (bearer) plugin so an expired access token is transparently refreshed on `401` (single-flight) and the request retried; logout revokes the refresh token server-side. Android supplies the storage: `data/auth/DataStoreTokenStore` (DataStore impl of the shared `TokenStore`); iOS will add a Keychain impl and reuse the same refresh flow. Launch is gated on having a token. `BuildConfig.GOOGLE_WEB_CLIENT_ID` (from Gradle properties) being blank disables the Google button.

### Navigation (no Navigation-Compose graph)

Despite depending on `navigation-compose`, the app navigates via **`Activity` + `Intent`**, not a `NavHost`:

- `MainActivity` is the launcher. It hosts a single `Scaffold` with a bottom `NavigationBar` whose three destinations (`Home`, `New`, `Library`) are an in-Composable `enum` + `rememberSaveable` state — switching tabs swaps the body Composable, it does not navigate.
- `FlashcardsActivity` (practice) and `EditDeckActivity` (edit) are separate `@AndroidEntryPoint` activities launched via `Intent` from `MainActivity`. Data is passed as intent extras using constants on each activity's companion object: `FlashcardsActivity.SESSION_ID_EXTRA`, `EditDeckActivity.DECK_ID_EXTRA`.

### DI & app setup

Hilt throughout (`@HiltAndroidApp` on `FlashcardsApplication`, `@AndroidEntryPoint` on every Activity, `@HiltViewModel` ViewModels obtained via `hiltViewModel()`). `FlashcardsApplication` also wires Coil 3's `SingletonImageLoader.Factory` (via `FlashcardsImageLoaderFactory`) for remote/SVG card images.

## Architecture (backend)

Ktor (Netty) under `backend/src/main/kotlin/com/rrbrambley/flashcards/backend/`. Feature packages each pair a `*Routes.kt` (HTTP layer, validates input and maps to/from shared DTOs) with a `*Repository.kt`/`*Service.kt` (Exposed persistence / logic): `auth`, `decks`, `sessions`, `home`, `images`, `health`. `Application.kt` is the entry point; `plugins/` configures `Routing`, `Security` (**JWT** bearer auth, `BEARER_AUTH`, with a `WWW-Authenticate` challenge so clients know to refresh), `Serialization`, `Cors`, `StatusPages`, `Monitoring` (MDC request/user ids), `RequestLimits`, and `RateLimiting` (per-IP throttle on `/auth/*`). Auth issues short-lived access JWTs (`auth/TokenService`) plus opaque refresh tokens persisted in `RefreshTokens` (rotated on `/auth/refresh`, with reuse detection that revokes the session); `/auth/logout` deletes the row. `db/` holds `DatabaseFactory` (HikariCP + Exposed, schema creation via `createMissingTablesAndColumns` + first-boot seed of the demo user, the demo *refresh* token `demo-token`, and the global Flags of the World deck) and `Tables.kt`. `mapping/Mappers.kt` converts Exposed rows ↔ shared DTOs. `error/Exceptions.kt` + `StatusPages` map domain exceptions to HTTP codes (e.g. 400, 413 `PayloadTooLarge`, 415 `UnsupportedMediaType`, 429 from the rate limiter, 503 when an optional feature is unconfigured).

- **Pagination:** `GET /decks` (id desc) and `GET /sessions` (updatedAt+id desc) read `limit`/`cursor` query params (`routes/Pagination.kt`, default 20 / max 100, opaque base64 cursor) and respond with the shared `Page<T>`.
- **Config:** `resources/application.conf` reads env vars with `${?VAR}` (DB, `JWT_*`, `RATE_LIMIT_AUTH_*`, `GOOGLE_WEB_CLIENT_ID`, `CORS_ALLOWED_ORIGINS`, `S3_*`/`CDN_BASE_URL`, optional `S3_ENDPOINT`). `JWT_SECRET` has an insecure dev default that must be overridden in production. Unset optional groups degrade gracefully: no `GOOGLE_WEB_CLIENT_ID` → `/auth/google` 503; no S3 bucket/CDN → `/images` 503. The `:backend:run` task forwards these keys from `gradle.properties`/`~/.gradle/gradle.properties` so a plain run works locally.
- **Storage:** `storage/StorageService.kt` (interface) + `S3StorageService.kt` (AWS SDK for Kotlin S3 client). `images/ImageRoutes.kt` does multipart upload (5 MB cap; JPEG/PNG/WebP/GIF), `PutObject`s to `images/<uuid>.<ext>`, and returns the `CDN_BASE_URL`-prefixed URL. **AWS credentials come only from the default AWS chain** (`~/.aws` or env), never config.

## Architecture (webApp)

React 19 + TypeScript + Vite SPA in `webApp/` (react-router). `src/api/` has hand-written TS mirrors of the shared DTOs (`types.ts`, including `Page<T>`) and a fetch-based `client.ts` (access + refresh tokens in `localStorage`, with single-flight transparent refresh on `401`). `src/auth/` handles email/password + Google sign-in (Google Identity Services); `src/decks/` is the library (title search + A–Z/recently-practiced sort + cursor "Load more") + create/edit (`DeckForm` mirrors the Android fields, including front-of-card image upload via `POST /images`). Config comes from `import.meta.env` (`VITE_API_BASE_URL`, `VITE_GOOGLE_WEB_CLIENT_ID`) loaded from `webApp/.env` (gitignored; template in `.env.example`). `src/practice/` is the practice flow (a card-flip + reducer, parity with Android), routed at `/decks/:id/practice`. The dev server is pinned to port 5173 (`vite.config.ts`, `strictPort`) to match the registered Google OAuth origin. Tests use Vitest (`npm run test`).

## Architecture (iosApp)

SwiftUI app in `iosApp/`, **in active development** (the Android-parity initiative; see the Linear "iOS App (SwiftUI)" project). It consumes the shared `Shared` Kotlin/Native framework and the offline-first repositories via `createIosFlashcardSdk(baseUrl, tokenStore)` (from `:shared` iosMain).

- **The Xcode project is generated by [XcodeGen](https://github.com/yonsm/XcodeGen)** from `iosApp/project.yml` (the source of truth). `Flashcards.xcodeproj` is **gitignored** — run `xcodegen generate` from `iosApp/` after cloning or editing the spec; **never hand-edit the `.pbxproj`**. Adding files/targets/build settings or the shared-framework build phase = edit `project.yml` + regenerate.
- **Shared framework integration (direct):** a `preBuildScripts` "Compile Kotlin Framework" phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode` (reads Xcode's `CONFIGURATION`/`SDK_NAME`/`ARCHS`), staging the static `Shared.framework` into `shared/build/xcode-frameworks/<config>/<sdk>`. The target sets `FRAMEWORK_SEARCH_PATHS` there and links via `OTHER_LDFLAGS = -framework Shared`, so Swift can `import Shared`. The Kotlin framework `baseName`/Swift module is **`Shared`** (declared `isStatic = true` in `shared/build.gradle.kts`). The build phase needs a JDK 17+ (`JAVA_HOME` or `/usr/libexec/java_home`).
- Bundle id `com.rrbrambley.flashcards`, deployment target iOS 17, iPhone (`TARGETED_DEVICE_FAMILY = 1`). The `Info.plist` is generated by XcodeGen from `project.yml`'s `info:` block (also gitignored). Sources under `iosApp/Flashcards/`; `Assets.xcassets` holds `AppIcon` (placeholder) + `AccentColor`.
- **Composition root:** `AppContainer` (`@MainActor ObservableObject`) calls `createIosFlashcardSdk(...)` once to build the `FlashcardSdk` (client + repositories), injected via `.environmentObject` and resolved with `@EnvironmentObject`. The backend URL is a **per-config build setting** `BACKEND_BASE_URL` (Debug/Release in `project.yml`) surfaced through Info.plist's `BackendBaseURL` and read by `AppConfig` (sim → `localhost`, device → LAN/host). The `TokenStore` is `KeychainTokenStore` (shared iosMain, via `platform.Security`/`CFDictionary`) so tokens persist across launches; `InMemoryTokenStore` remains for previews/tests. **Keychain needs an app bundle** (`errSecNotAvailable` in a bare KN test runner), so it's verified by running the app — token logic is unit-tested with fakes.
- **Kotlin↔Swift bridging (hand-rolled, no SKIE):** `suspend` → `async` is automatic (`try await`). For Flows, `FlowAdapter<T>` (shared iosMain) + the Swift `asyncStream(_:)` free function yield an `AsyncStream<T?>`; the raw `Flow<T>` is erased across the Obj-C bridge, so wrap each Flow on the Kotlin side at a concrete, non-null element type (see `Bridging.kt`, e.g. `loggedInAdapter`). Kotlin default args don't bridge (pass them all); Kotlin `object` → Swift `.shared`; top-level funcs in `Foo.kt` → Swift `FooKt`.
- **Navigation:** `RootView` gates on auth (observes `loggedInAdapter`): `ProgressView` while loading, the auth flow (`AuthView` + `AuthViewModel`, over the shared `AuthService`) when signed out, `MainTabView` when signed in (mirrors Android's `MainActivity`). `MainTabView` is a `TabView` with **Home / New / Library**, each its own `NavigationStack` for pushing detail/create/edit/practice. Feature screens live in per-feature folders (`Home/`, `Library/`, `Create/`, `Auth/`, …), currently placeholders filled in by the feature tickets.
- **Headless build/verify** (Claude can't drive the Xcode GUI): `cd iosApp && xcodegen generate`, then `xcodebuild -project Flashcards.xcodeproj -scheme Flashcards -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' build`, then `xcrun simctl boot/install/launch` + `simctl io … screenshot` to confirm it runs. The Xcode SDK needs a matching simulator runtime — if missing, `xcodebuild -downloadPlatform iOS`.

## Testing conventions

JVM unit tests in `androidApp/src/test` focus on ViewModels and repositories with **hand-written fakes** that implement the domain interfaces (e.g. `FakeHomeRepository` nested in the test, `FakeStringProvider`) — no mocking framework. Repository tests that need the real `FlashcardApiClient` back it with a Ktor `MockEngine`. Coroutine/Flow tests use `kotlinx-coroutines-test`: `StandardTestDispatcher` + `Dispatchers.setMain/resetMain` in `@Before`/`@After`, `runTest`, and `scheduler.advanceUntilIdle()` to flush. Backend tests (`backend/src/test`, e.g. `ApplicationFlowTest`) drive the full app over HTTP against a **real Postgres via Testcontainers**, with a fake `StorageService` injected for image tests (no AWS needed). **Instrumented** tests live in `androidApp/src/androidTest` — notably `FlashcardsDatabaseMigrationTest` (Room `MigrationTestHelper` against the exported schemas); they run on an emulator (locally via `:androidApp:connectedDebugAndroidTest`, and in CI's emulator job), not in the JVM unit-test run. Web tests are Vitest (`webApp/src/**/*.test.ts(x)`). Shared **iOS** tests live in `shared/src/commonTest` (run on the Native target via `:shared:iosSimulatorArm64Test`, e.g. `TokenRefreshAuthTest`, `AuthServiceTest`) — `commonTest` compiles into both the JVM and iOS test binaries. CI (`.github/workflows/ci.yml`) runs five jobs: ktlint, JVM tests + coverage, web, Android instrumented (emulator), and **iOS** (a `macos-latest` job: `:shared:iosSimulatorArm64Test` + `xcodegen generate` + `xcodebuild` the app for a simulator). The jobs are **path-gated** by a fast `changes` job (`dorny/paths-filter`) so each runs only when relevant code changed, to keep Actions cost down (the iOS job is a 10×-billed macOS runner). `shared/**` is the common dependency (triggers ktlint, JVM, Android, iOS — not web, which hand-mirrors the DTOs); `webApp/**` → web only; `iosApp/**` → iOS only; root Gradle / the workflow file → everything; docs-only → nothing heavy. `main` isn't branch-protected, so a skipped job is simply absent from the checks.

## Notes

- `androidApp`: `minSdk = 26`, `compileSdk/targetSdk = 36`, Java/JVM 11. `shared` matches (`compileSdk = 36`, `minSdk = 26`); `backend` is JVM toolchain 11. Kotlin 2.2.10, Ktor 3.2.x (pinned — newer Ktor/serialization versions break the Kotlin/Native 2.2.10 iOS klibs).
