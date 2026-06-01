# Flashcards

A multiplatform flashcards app for self-testing across subjects. The long-term goal
is a shared product across **Android, iOS, web, and a backend**, sharing business logic
via Kotlin Multiplatform (KMP) while keeping a native UI per platform.

## Status

| Component | State |
|-----------|-------|
| KMP monorepo (`shared` + `androidApp`) | ✅ Done |
| Ktor + Postgres backend | ✅ Done |
| Android app, synced to the backend (offline-first) | ✅ Done |
| iOS app (SwiftUI) | ⏳ Not started |
| Web app (JS/TS) | ⏳ Not started |
| Real login/register UI (currently a seeded dev token) | ⏳ Not started |

## Repository layout

A single Gradle build with three modules:

```
Flashcards/
├── shared/        KMP library — @Serializable API DTOs + a Ktor HTTP client
│                  (targets android, iOS, jvm). Consumed by androidApp and the backend.
├── androidApp/    Android app (Jetpack Compose, MVVM, Hilt, Room). Offline-first.
├── backend/       Ktor server (Netty) + Exposed + Postgres. The API the apps sync against.
├── docker-compose.yml   Local Postgres for the backend
└── gradle/libs.versions.toml   Version catalog (single source for deps)
```

Reserved for later phases: `iosApp/`, `webApp/`.

## Tech stack

- **Language:** Kotlin 2.2.10 (JVM toolchain 11), Kotlin Multiplatform
- **Android:** Jetpack Compose, Hilt (DI), Room (local cache), Ktor client (OkHttp), DataStore
- **Backend:** Ktor 3.2.x, Exposed + HikariCP, PostgreSQL, BCrypt, kotlinx.serialization
- **Shared:** kotlinx.serialization DTOs + a Ktor `FlashcardApiClient` reused across platforms

## API

All endpoints require a bearer token except register/login. Bodies are the `@Serializable`
DTOs in `shared/src/commonMain/.../api/`.

| Method + Path | Purpose |
|---------------|---------|
| `POST /auth/register`, `POST /auth/login` | Issue a bearer token |
| `GET /decks`, `GET /decks/{id}` | List / fetch decks (a user's decks + the global catalog) |
| `POST /decks`, `PUT /decks/{id}` | Create / update a deck (owner-scoped; the seeded deck is read-only) |
| `POST /sessions` | Start or resume a practice session for a deck |
| `GET /sessions?active=true`, `GET /sessions/{id}` | List / fetch practice sessions |
| `PATCH /sessions/{id}`, `POST /sessions/{id}/complete` | Update progress / complete a session |
| `GET /home` | Server-computed home feed |

On first boot the backend creates its schema and seeds a demo user, a fixed dev token
(`demo-token`), and a global "Country Flags" deck.

---

## Running the backend locally

> **Container runtime:** these instructions assume Docker via [Colima](https://github.com/abiosoft/colima)
> (`brew install colima docker docker-compose`). Docker Desktop works too — skip the `colima` steps.

### Default path (port 5432 free)

```bash
colima start                          # start the Docker VM (Colima only)
docker-compose up -d postgres         # start Postgres on :5432
./gradlew :backend:run                # serves at http://0.0.0.0:8080
```

### If you already run Postgres on 5432

Use a different host port and point the backend at it:

```bash
POSTGRES_PORT=5433 docker-compose up -d postgres
DB_JDBC_URL=jdbc:postgresql://localhost:5433/flashcards ./gradlew :backend:run
```

The server is ready when it logs `Responding at http://0.0.0.0:8080`. Leave it running.

If `docker-compose` can't reach the daemon under Colima, export the socket first:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
```

### Configuration

`backend/src/main/resources/application.conf` reads these env vars (with localhost
defaults), so you can override any of them:

| Env var | Default |
|---------|---------|
| `PORT` | `8080` |
| `DB_JDBC_URL` | `jdbc:postgresql://localhost:5432/flashcards` |
| `DB_USER` / `DB_PASSWORD` | `flashcards` / `flashcards` |

### Smoke-test the API

```bash
curl -s http://localhost:8080/decks -H "Authorization: Bearer demo-token"
# -> the seeded Country Flags deck
```

### Teardown

```bash
docker-compose down       # stop Postgres, keep data
docker-compose down -v    # stop + wipe data (fresh seed next run)
colima stop               # stop the VM (Colima only)
```

---

## Running the Android app against the backend

The app's base URL is `http://10.0.2.2:8080` (`BuildConfig.BACKEND_BASE_URL`). `10.0.2.2`
is the **Android emulator's** alias for your host machine, so an emulator reaches a
locally-running backend with no extra setup. It authenticates automatically with the
seeded `demo-token` (login UI is a later phase).

1. Start the backend (above).
2. Boot an emulator and install the app:
   ```bash
   ./gradlew :androidApp:installDebug
   ```
   …or just Run the `androidApp` configuration in Android Studio.

**Physical device:** `10.0.2.2` is emulator-only. Point `BACKEND_BASE_URL` at your host's
LAN IP (e.g. `http://192.168.x.x:8080`), put the device on the same network, and add that
host to `androidApp/src/main/res/xml/network_security_config.xml` (it only allows cleartext
to the dev hosts).

---

## Building & testing

```bash
./gradlew :androidApp:assembleDebug        # build the Android APK
./gradlew :androidApp:testDebugUnitTest    # Android unit tests
./gradlew :shared:build                    # build shared for android/iOS/JVM
./gradlew :backend:build                   # build the backend
./gradlew :backend:test                    # backend integration tests (Testcontainers)
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
