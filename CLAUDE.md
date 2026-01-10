# Project: Lehrerlog
Type: Kotlin Multiplatform (KMP) + Compose Multiplatform
Frontend Targets: Android, iOS, Desktop (JVM), Web (Wasm/JS)
Server: Ktor

## Architecture
- **Pattern:** MVVM (Model-View-ViewModel)
- **DI:** Koin
    - Use `koin-compose-viewmodel` for injecting ViewModels in Composables (`koinViewModel()`).
    - Use `module { }` for dependency definitions.
- **Navigation:** Jetpack Navigation (Compose Multiplatform)
    - Library: `org.jetbrains.androidx.navigation:navigation-compose`
    - Components: `NavHost`, `rememberNavController`, `composable`.
    - Use Type-Safe Navigation (Kotlin serialization) if available, or route strings.
- **Network:** Ktor Client (configure engines per platform).
- **Database:**
    - **Client (Apps):** SQLDelight (`.sq` files for queries).
    - **Server:** Exposed (ORM/DAO) + PostgreSQL + Flyway (Migrations).
- **Async:** Coroutines + Flow

## File Structure Rules
- **Business Logic:** ALWAYS goes in `commonMain` unless platform-specific APIs are needed. One exception: If something is needed for only web and nowhere else, you can mock implementations und nonWasmMain.
- **UI:** Compose UI goes in `commonMain` (shared UI).
- **Platform Implementations:**
    - Android: `androidMain` (Context references, Activity)
    - iOS: `iosMain` (UIKit interoperability)
    - Desktop: `jvmMain` (JDK on Desktop)
    - Web: `wasmJsMain` (DOM access)
    - Server: `server` module (Ktor server setup, Exposed tables)

## Commands
- Run Desktop: `./gradlew :composeApp:run`
- Run Server: `./gradlew :server:run`
- Build Web: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
- Test: `./gradlew test` (or `./gradlew allTests`)

## Coding Style
- Use `expect`/`actual` only when interfaces cannot solve the problem.
- **UI:** Use Material 3 conventions (implied by Compose).
- **Koin:** Prefer constructor injection for classes; use `koinViewModel()` in UI.
- **Concurrency:** Dispatchers.Main for UI, Dispatchers.IO for DB/Network.
- For bitwise operations, always use: `if ((flags and (1 shl 30)) != 0)`