# LehrerLog

Kotlin Multiplatform (KMP) + Compose Multiplatform application for teachers.

## Modules
- `composeApp/` - Shared UI and client logic (Android, iOS, Desktop JVM, Web Wasm)
- `server/` - Ktor backend with Exposed ORM, PostgreSQL, and Flyway migrations
- `shared/` - DTOs and common code shared between client and server
- `iosApp/` - iOS entry point (Swift/Xcode)

## Architecture

- **Pattern:** MVVM with Compose UI in `commonMain`
- **DI:** Koin (`module { }`, `koinViewModel()` in Composables)
- **Navigation:** Jetpack Navigation (`NavHost`, `rememberNavController`, `composable`)
- **Database:**
    - Client: online-first (SQLDelight/offline sync is being removed; do not add new SQLDelight code)
    - Server: Exposed + PostgreSQL + Flyway (`server/src/main/resources/db/migration/`)
- **Network:** Ktor Client (OkHttp/Android, Darwin/iOS, CIO/JVM, JS/Wasm)
- **Logging:** Kermit
- **Async:** Coroutines + Flow
- **Auth:** JWT with refresh tokens, role-based (`ADMIN`, `SCHOOL_ADMIN`, `TEACHER`)

## Client Logging

- Use `logger()` extension (class-based tags) instead of injecting `Logger`.
- Configure logging in `AppModule.kt` via `LoggerConfig` and `LogFileWriter`.
- Android must call `initAndroidLogFileWriter()` and `initAndroidLogSharing()` in `MainActivity`.
- Log file locations:
    - Android: `cacheDir/logs`
    - iOS: Application Support `logs/`
    - Desktop: `~/.lehrerlog/logs`
    - Web: localStorage (OPFS if available)
- Never log secrets or PII; log anonymized identifiers only.

## Commands

Use `./gradlew` (macOS/Linux) or `.\gradlew.bat` (Windows).

| Task | Command |
|------|---------|
| Run Desktop | `:composeApp:run` |
| Run Server | `:server:run` |
| Run Web (Wasm) | `:composeApp:wasmJsBrowserDevelopmentRun` |
| Run Web (JS) | `:composeApp:jsBrowserDevelopmentRun` |
| Build Android | `:composeApp:assembleDebug` |
| Test Server | `:server:test` |
| Test Client | `:composeApp:testDevDebugUnitTest` |
| Test All | `test` or `allTests` |
| Test Sync | `:server:test --tests "*Sync*Test"` |
| **Coverage Server** | `:server:test :server:jacocoTestReport` |
| **Coverage Client** | `:composeApp:testDevDebugUnitTest :composeApp:koverHtmlReport` |
| **Check Coverage** | `:server:checkCoverage :composeApp:checkCoverage` |
| Flyway Repair | `:server:flywayRepair` |

## Long-Running Tasks

- If the user explicitly says "do not stop" for a task, keep working for at least 3 hours before stopping, unless they
  cancel or you encounter an unsolvable problem which requires user input.

## File Structure

- **Business logic:** Always in `commonMain` unless platform-specific APIs are needed
- **Platform code:**
    - `androidMain` - Android Context, Activity
    - `iosMain` - UIKit/iOS APIs
    - `jvmMain` - Desktop JVM
    - `wasmJsMain` - Web/DOM access
    - `nonWasmMain` - Code shared by all platforms except Wasm
- Use `expect`/`actual` only when interfaces cannot solve the problem

## Coding Style

- Package: `de.aarondietz.lehrerlog`
- 4-space indentation, no tabs
- `PascalCase` for classes, `camelCase` for functions/variables, `SCREAMING_SNAKE_CASE` for constants
- Flyway migrations: `V###__Description.sql`
- Prefer constructor injection; use `koinViewModel()` in UI
- Dispatchers: `Main` for UI, `IO` for DB/Network
- Bitwise operations: `if ((flags and (1 shl 30)) != 0)`

## Code Patterns

### Service Layer - Sealed Result Types
```kotlin
sealed class UpdateResult {
    data class Success(val data: StudentDto) : UpdateResult()
    object NotFound : UpdateResult()
    object VersionConflict : UpdateResult()
}
```
Service methods return sealed types; routes convert to HTTP codes (404, 409, etc.).

### Repository Layer - Result Wrapper
```kotlin
suspend fun createStudent(...): Result<StudentDto>
suspend fun deleteStudent(...): Result<Unit>
```
All repository methods return `Result<T>`. ViewModels use `.onSuccess()` / `.onFailure()`.

### ViewModel - StateFlow Pattern
```kotlin
val students: StateFlow<List<StudentDto>> = schoolId
    .filterNotNull()
    .flatMapLatest { id -> repository.getStudentsFlow(id) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

### Server Routes - School Isolation
All authenticated routes must validate `principal.schoolId`:
- 403 Forbidden: User not associated with school
- 409 Conflict: Version mismatch (optimistic locking)

## Offline Sync (deprecated)

- Offline-first client sync is being removed; do not add new sync/SQLDelight work.
- Keep server `version` fields and `SyncLog` for possible future reintroduction.

## Testing

- Server tests: `server/src/test/kotlin/de/aarondietz/lehrerlog/`
- Client tests: `composeApp/src/commonTest/`
- Naming: `*ServiceEndToEndTest.kt`, `*SyncTest.kt`
- Use unique test prefixes: `testing${(10000..99999).random()}`
- Clean up test data in `@AfterTest` using pattern matching
- Every new UI element must have explicit UI coverage (Compose UI test or Roborazzi snapshot).
- Every server service requires tests for its public behavior.
- Before finishing work, required tests must be implemented and executed successfully.

### Coverage Reports

- **Server (JaCoCo)**: `server/build/reports/jacoco/html/index.html` (60% min, 50% per class)
- **Client (Kover)**: `composeApp/build/reports/kover/html/index.html` (60% min, 50% per class)
- Run `:server:checkCoverage` and `:composeApp:checkCoverage` before committing
- See [COVERAGE_QUICK_START.md](COVERAGE_QUICK_START.md) for quick reference

### ROBORAZZI & COMPOSE UI

1. **DETERMINISM:**
   - NEVER use `delay()` or `Thread.sleep()`.
   - Set `composeTestRule.mainClock.autoAdvance = false`.
   - Use `composeTestRule.mainClock.advanceTimeBy(millis)` for state progression.
   - Force `motionDurationScale = 0` to kill animations.
2. **DATA INTEGRITY:**
   - NO inline mock data or magic strings (e.g., "John Doe").
   - Import strictly from `SharedTestFixtures.kt`. You may add new static values and functions (create if missing).
3. **DESIGN CONSISTENCY:**
   - NO raw values (e.g., `12.dp`, `Color.Red`) in productive code. Allowed for tests.
   - MUST use `MaterialTheme` tokens (spacing, typography, colorScheme). Add reusable tokens if needed.
4. **FAILURE HANDLING:**
   - If snapshot verification fails, report the diff percentage.
   - DO NOT modify UI padding/layout to force a pass.
5. **NAMING:**
   - `captureRoboImage("src/commonTest/snapshots/${TestClass}_${Scenario}.png")`
6. **IO MOCKING:**
   - For file upload limits, mock the response of the server.
   - DO NOT generate actual 100MB dummy files in tests.
   - Use MockFileSystem to simulate file sizes.

## Environments

| Environment | Server URL |
|-------------|------------|
| DEV | `http://localhost:8080` |
| QA | `https://api.qa.lehrerlog.de` |
| PROD | `https://api.lehrerlog.de` |

Override with: `-PserverUrl=https://...`

## CI/CD

- Version source: `iosApp/Configuration/Config.xcconfig` (`APP_VERSION`)
- Release tag format: `MAJOR.MINOR.PATCH-bNNNN` (e.g., `0.0.1-b0001`)
- Android signing secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS_APK`, `ANDROID_KEY_ALIAS_AAB`
- Entire project targets JDK 21 (build/runtime)
- Flavors: Android `prod/qa/dev` with `applicationIdSuffix` (`.qa`, `.dev`); iOS uses `BUNDLE_SUFFIX` in `iosApp/Configuration/Config.xcconfig`
- Flyway: never edit applied migrations; use new migrations. Repair only with `:server:flywayRepair` (local) or `.github/workflows/flyway-repair.yml` (QA/PROD)
- Deploy scripts: `deploy/` folder (Docker Compose, nginx, backup/restore)

## Git Workflow

- Always `git add` new files immediately after creating them
- Stage files before finishing a task
- Only commit/push when explicitly requested

