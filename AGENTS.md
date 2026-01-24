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
    - Client: SQLDelight (`.sq` files in `composeApp/src/commonMain/sqldelight/`)
    - Server: Exposed + PostgreSQL + Flyway (`server/src/main/resources/db/migration/`)
- **Network:** Ktor Client (OkHttp/Android, Darwin/iOS, CIO/JVM, JS/Wasm)
- **Logging:** Kermit
- **Async:** Coroutines + Flow
- **Auth:** JWT with refresh tokens, role-based (`ADMIN`, `SCHOOL_ADMIN`, `TEACHER`)

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
| Test All | `test` or `allTests` |
| Test Sync | `:server:test --tests "*Sync*Test"` |
| Flyway Repair | `:server:flywayRepair` |

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

## Offline-First Sync

The app uses offline-first architecture with automatic synchronization.

### Key Concepts

- **Client-generated UUIDs:** Entities created offline use client UUIDs preserved by server
- **SyncLog:** Server maintains append-only log of all changes (CREATE, UPDATE, DELETE)
- **Optimistic Locking:** Version fields prevent concurrent update conflicts
- **School Isolation:** All operations enforce multi-tenant data separation

### Server Pattern

```kotlin
// Service methods with UUID preservation for sync
fun createStudent(studentId: UUID, schoolId: UUID, ..., userId: UUID): StudentDto {
    return transaction {
        Students.insert { it[id] = studentId; ... }
        SyncLog.insert { /* log CREATE */ }
        getStudent(studentId, schoolId)!!
    }
}

// Version-based conflict detection
fun updateStudent(..., version: Long, ...): UpdateResult {
    return transaction {
        if (existing[Students.version] != version) {
            return@transaction UpdateResult.VersionConflict
        }
        Students.update { it[Students.version] = version + 1; ... }
        SyncLog.insert { /* log UPDATE */ }
    }
}
```

### Client SQLDelight Schema

All synced entities must include:
```sql
CREATE TABLE IF NOT EXISTS Entity (
    id TEXT PRIMARY KEY NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    isSynced INTEGER NOT NULL DEFAULT 0,
    localUpdatedAt INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
```

Supporting tables: `SyncMetadata.sq` (tracks lastSyncLogId), `PendingSync.sq` (queues unsynced changes)

### Platform Connectivity

`expect class ConnectivityMonitor` with `actual` implementations:
- Android: `ConnectivityManager` + `NetworkCallback`
- iOS: `NWPathMonitor`
- Desktop: Periodic socket checks
- Web: `navigator.onLine` + event listeners

## Testing

- Server tests: `server/src/test/kotlin/de/aarondietz/lehrerlog/`
- Client tests: `composeApp/src/commonTest/`
- Naming: `*ServiceEndToEndTest.kt`, `*SyncTest.kt`
- Use unique test prefixes: `testing${(10000..99999).random()}`
- Clean up test data in `@AfterTest` using pattern matching

### Required Sync Test Coverage

1. Server CRUD tests (verify sync logs created)
2. Push/pull endpoint tests with pagination
3. Offline-to-online workflow tests
4. Version conflict resolution tests
5. School isolation tests

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
- Flavors: Android `prod/qa/dev` with `applicationIdSuffix` (`.qa`, `.dev`); iOS uses `BUNDLE_SUFFIX` in `iosApp/Configuration/Config.xcconfig`
- Flyway: never edit applied migrations; use new migrations. Repair only with `:server:flywayRepair` (local) or `.github/workflows/flyway-repair.yml` (QA/PROD)
- Deploy scripts: `deploy/` folder (Docker Compose, nginx, backup/restore)

## Git Workflow

- Always `git add` new files immediately after creating them
- Stage files before finishing a task
- Only commit/push when explicitly requested
