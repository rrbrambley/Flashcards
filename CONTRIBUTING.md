# Contributing

Thanks for your interest in Flashcards! This is a Kotlin Multiplatform monorepo with an
Android app, a Ktor backend, a shared KMP library, and a React/TypeScript web app. See the
[README](README.md) for the full layout, setup, and optional Google/AWS configuration.

## Getting set up

1. Read the [README](README.md) "Quick start" — install the prerequisites (JDK 11+,
   Android Studio, Node 18+, and Docker via Colima or Docker Desktop).
2. Start Postgres and the backend, then run the Android app and/or the web app.
3. Google Sign-In and image uploads are **optional** — the app runs fully without them.
   If you want them, configure your **own** OAuth client and AWS resources (README).

## Project conventions

- **Single source for dependencies:** add/upgrade libraries in `gradle/libs.versions.toml`,
  not inline in a module's `build.gradle.kts`.
- **Android architecture:** package-by-feature MVVM (`ui` / `domain` / `data`), one
  `StateFlow<*UiState>` per ViewModel, repositories behind interfaces bound with Hilt.
- **Backend:** Ktor + Exposed; routes in `*/Routes.kt`, persistence in `*/Repository.kt`,
  domain DTOs come from the shared module.
- **Tests:** JVM unit tests use hand-written fakes (no mocking framework). Backend
  integration tests use Testcontainers (Docker required).

## Before opening a pull request

Run the checks for the area you changed:

```bash
./gradlew :androidApp:testDebugUnitTest   # Android unit tests
./gradlew :shared:build                   # shared library (android/iOS/JVM)
./gradlew :backend:test                   # backend integration tests (needs Docker)
cd webApp && npm run build && npm run lint # web app
```

- Keep changes focused and described in the PR body.
- **Never commit secrets** or account-specific identifiers. Personal config goes in
  `~/.gradle/gradle.properties` or `webApp/.env` (both untracked). See [SECURITY.md](SECURITY.md).
- Match the style of the surrounding code.

## License

By contributing, you agree that your contributions are licensed under the
[MIT License](LICENSE).
