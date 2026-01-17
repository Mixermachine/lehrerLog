# Repository Guidelines

## Project Structure & Module Organization
This is a Kotlin Multiplatform repository with three Gradle modules:
- `composeApp/` for the shared Compose UI and platform targets (Android, Desktop JVM, iOS, Web).
- `shared/` for shared business/domain code used across all targets.
- `server/` for the Ktor backend, including routes, services, database access, and Flyway migrations in `server/src/main/resources/db/migration`.
Platform entry points live in `iosApp/` (Swift/Xcode) and the Compose module for Android/JVM/Web.

## Architecture Overview
- UI follows MVVM with Compose in `commonMain`; keep business logic in shared code unless a platform API is required.
- DI uses Koin (`module { }`, `koinViewModel()` in Composables).
- Navigation uses Jetpack Navigation for Compose (`NavHost`, `rememberNavController`, `composable`).
- Data: Ktor Client on apps, SQLDelight for local storage; server uses Exposed + PostgreSQL with Flyway migrations.
- Sync is offline-first: client-generated UUIDs are preserved, SyncLog is append-only, and conflicts use version-based optimistic locking.

## Build, Test, and Development Commands
Use the Gradle wrapper (`./gradlew` on macOS/Linux, `.\gradlew.bat` on Windows).
- `:composeApp:assembleDebug` builds the Android debug APK.
- `:composeApp:run` starts the Desktop JVM app.
- `:composeApp:wasmJsBrowserDevelopmentRun` runs the Web Wasm dev server.
- `:composeApp:jsBrowserDevelopmentRun` runs the JS web dev server (legacy browsers).
- `:server:run` starts the Ktor server locally.
- `:server:test` and `:composeApp:test` run module tests.
- `test` (root) runs all module tests; `allTests` is available for KMP.
- `:server:test --tests "*Sync*Test"` targets sync-related E2E tests.

## Coding Style & Naming Conventions
- Kotlin: 4-space indentation, no tabs, follow official Kotlin style.
- Names: `PascalCase` for classes, `camelCase` for functions/variables, `SCREAMING_SNAKE_CASE` for constants.
- Packages follow `de.aarondietz.lehrerlog`.
- SQL migrations use Flyway naming: `V###__Description.sql`.

## Testing Guidelines
- Tests live in `*/src/commonTest` for KMP and `server/src/test` for backend tests.
- Frameworks: `kotlin.test` (shared) and JUnit via `kotlin.test.junit` for server tests; Ktor test host is used for backend integration tests.
- Name tests `*Test.kt` (see `server/src/test/...EndToEndTest.kt`).
- Sync features require end-to-end coverage: CRUD logging, push/pull flows, conflict handling, and school isolation.

## Commit & Pull Request Guidelines
- Recent history mixes `fix:`-prefixed commits, sentence-case "Adds ..." messages, and a few WIP-style entries.
- Prefer short, present-tense summaries; use `fix:` or `feat:` prefixes if you follow conventional commits, but keep it consistent within a series.
- PRs should include a clear description, steps to verify, and screenshots for UI changes. Mention any schema/migration updates and link related issues.
- Always `git add` newly created files; avoid leaving untracked files behind.

## Configuration & Data
- Server schema changes require a new Flyway migration in `server/src/main/resources/db/migration`.
- Keep secrets out of VCS; prefer local config overrides (e.g., `local.properties`) for developer-specific values.
