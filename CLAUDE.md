# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Flashcards is a Kotlin Multiplatform (KMP) monorepo for a self-testing flashcards product. Today it contains the Android app (`androidApp/`, Compose) and a shared KMP module (`shared/`). Reserved for future phases: `iosApp/` (SwiftUI), `webApp/` (JS/TS), and `backend/` (Kotlin). The Android app ships with a seeded "country flags" deck when no decks exist locally.

The repo is one Gradle build with two modules: `:androidApp` (the AGP application) and `:shared` (KMP library targeting `androidTarget`, the three iOS targets, and `jvm`). `:androidApp` depends on `:shared`.

## Commands

All commands run via the Gradle wrapper from the repo root. Tasks are module-qualified.

```bash
./gradlew :androidApp:assembleDebug          # Build the debug APK
./gradlew :androidApp:installDebug           # Build + install on a connected device/emulator
./gradlew :androidApp:testDebugUnitTest      # Android unit tests (androidApp/src/test)
./gradlew :androidApp:connectedAndroidTest   # Instrumented tests (needs a device/emulator)
./gradlew :androidApp:lint                   # Android Lint
./gradlew test                               # All JVM unit tests across modules

# shared KMP module
./gradlew :shared:assembleDebug              # Build the shared Android library
./gradlew :shared:jvmTest                    # Run commonTest on the JVM host
./gradlew :shared:compileKotlinMetadata      # Type-check commonMain (expect/actual)
```

Run a single unit test class or method:

```bash
./gradlew :androidApp:testDebugUnitTest --tests "com.rrbrambley.flashcards.practice.ui.FlashcardsViewModelTest"
./gradlew :androidApp:testDebugUnitTest --tests "*.HomeViewModelTest.uiState_startsAsLoading"
```

Dependencies are managed through the version catalog at `gradle/libs.versions.toml` — add/upgrade libraries and plugins there, not inline in `androidApp/build.gradle.kts` or `shared/build.gradle.kts`. The KMP setup adds the `kotlin-multiplatform` and `android-library` plugin aliases. `local.properties` (SDK path) is gitignored and machine-specific.

## The `shared` module

`shared/` is a KMP library with source sets `commonMain` / `androidMain` / `iosMain` / `jvmMain` (plus `commonTest`). It currently holds only a `Platform`/`Greeting` expect/actual **placeholder** — no real domain logic has been migrated yet. It uses its own namespace `com.rrbrambley.flashcards.shared` and carries its own `compileSdk = 36` / `minSdk = 26`. iOS framework *linking* tasks require Xcode on macOS and are a later-phase concern; the iOS klib compiles headlessly. Domain logic (e.g. `Flashcard`, `FlashcardDeck`, repository interfaces) is a candidate to move into `commonMain` in a later phase, but for now it lives in `androidApp`.

## Architecture (androidApp)

Package-by-feature MVVM under `androidApp/src/main/java/com/rrbrambley/flashcards/`. Each feature package (`home`, `library`, `create`, `edit`, `practice`) is split into `ui` / `domain` / `data` sub-packages following the same layering:

- **ui** — Compose screen(s), a `@HiltViewModel`, and a sealed-class/data-class `*UiState`. ViewModels expose a single `StateFlow<*UiState>` via `MutableStateFlow` + `asStateFlow()`; UI states are modeled as sealed classes (e.g. `FlashcardsUiState.Loading / ShowFlashcard / SessionCompleted / LoadingFailed`).
- **domain** — repository interfaces and plain Kotlin models, no Android/framework types.
- **data** — repository implementations, Room DAOs/entities, and data sources.

Data flows one direction: `Compose screen → ViewModel → Repository (interface in domain) → DataSource → Room DAO`, with everything exposed as Kotlin `Flow`. Repository impls map Room `*Entity`/`*WithCards` rows to domain models (`toDomain()`); they never leak entities upward.

### Cross-cutting structure (important, non-obvious)

- The **top-level `domain` package** (`Flashcard`, `FlashcardDeck`, `FlashcardRepository`) holds models/interfaces shared across features. But the shared **Room database, DAOs, entities, and `FlashcardRepositoryImpl` all live under `practice/data/`** — the `practice` feature is the de facto home of shared persistence infrastructure, not a self-contained feature. `FlashcardsDatabase` (`practice/data/FlashcardsDatabase.kt`) registers all entities (`FlashcardDeckEntity`, `FlashcardEntity`, `PracticeSessionEntity`).
- Repositories are bound to interfaces via `@Binds` in `di/DataModule.kt`. When adding a repository, add its `@Binds` there. `di/DatabaseModule.kt` provides the DB + DAOs; `di/NetworkModule.kt` provides Retrofit (currently a placeholder `baseUrl`; the app is local/Room-driven — no live backend).
- The DB uses `fallbackToDestructiveMigration(true)` — schema changes wipe data rather than migrate. Bump the `version` in `FlashcardsDatabase` when changing entities.

### Navigation (no Navigation-Compose graph)

Despite depending on `navigation-compose`, the app navigates via **`Activity` + `Intent`**, not a `NavHost`:

- `MainActivity` is the launcher. It hosts a single `Scaffold` with a bottom `NavigationBar` whose three destinations (`Home`, `New`, `Library`) are an in-Composable `enum` + `rememberSaveable` state — switching tabs swaps the body Composable, it does not navigate.
- `FlashcardsActivity` (practice) and `EditDeckActivity` (edit) are separate `@AndroidEntryPoint` activities launched via `Intent` from `MainActivity`. Data is passed as intent extras using constants on each activity's companion object: `FlashcardsActivity.SESSION_ID_EXTRA`, `EditDeckActivity.DECK_ID_EXTRA`.

### DI & app setup

Hilt throughout (`@HiltAndroidApp` on `FlashcardsApplication`, `@AndroidEntryPoint` on every Activity, `@HiltViewModel` ViewModels obtained via `hiltViewModel()`). `FlashcardsApplication` also wires Coil 3's `SingletonImageLoader.Factory` (via `FlashcardsImageLoaderFactory`) for remote/SVG card images.

## Testing conventions

JVM unit tests (`androidApp/src/test`) focus on ViewModels and repositories with **hand-written fakes** that implement the domain interfaces (e.g. `FakeHomeRepository` nested in the test) — no mocking framework. Coroutine/Flow tests use `kotlinx-coroutines-test`: `StandardTestDispatcher` + `Dispatchers.setMain/resetMain` in `@Before`/`@After`, `runTest`, and `scheduler.advanceUntilIdle()` to flush.

## Notes

- Planning docs for features live in `.firebender/plans/*.plan.md` — useful context for in-progress/recent work; the project-level TODO is in `README.md`.
- `androidApp`: `minSdk = 26`, `compileSdk/targetSdk = 36`, Java/JVM 11. `shared` matches (`compileSdk = 36`, `minSdk = 26`, JVM 11 toolchain).
