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
- **Sync Architecture:** Offline-First with automatic synchronization
    - **Client generates UUIDs**: All entities created offline use client-generated UUIDs
    - **Server preserves UUIDs**: Server respects client UUIDs for offline-created entities
    - **Optimistic Locking**: Version fields prevent concurrent update conflicts
    - **Sync Log**: Server maintains append-only log of all changes (CREATE, UPDATE, DELETE)
    - **Incremental Sync**: Clients fetch only changes since last sync (by sync log ID)
    - **Conflict Resolution**: Last-write-wins with version numbers; conflicts rejected with error
    - **School Isolation**: All sync operations enforce multi-tenant data separation

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
- Test Server: `./gradlew :server:test`
- Test All: `./gradlew test` (or `./gradlew allTests`)
- Test Sync: `./gradlew :server:test --tests "*Sync*Test"`

## Coding Style
- Use `expect`/`actual` only when interfaces cannot solve the problem.
- **UI:** Use Material 3 conventions (implied by Compose).
- **Koin:** Prefer constructor injection for classes; use `koinViewModel()` in UI.
- **Concurrency:** Dispatchers.Main for UI, Dispatchers.IO for DB/Network.
- For bitwise operations, always use: `if ((flags and (1 shl 30)) != 0)`

## Offline-First Sync Implementation

### Server-Side Patterns

**Service Layer with UUID Preservation:**
```kotlin
// ALWAYS provide overloads for sync-based creation (preserves client UUIDs)
fun createStudent(
    studentId: UUID,      // Client-generated UUID
    schoolId: UUID,
    firstName: String,
    lastName: String,
    userId: UUID          // For audit trail
): StudentDto {
    return transaction {
        Students.insert {
            it[Students.id] = studentId  // Preserve client UUID
            it[Students.schoolId] = schoolId
            it[Students.firstName] = firstName
            it[Students.lastName] = lastName
            it[Students.createdBy] = userId
        }

        // ALWAYS log to SyncLog
        SyncLog.insert {
            it[SyncLog.schoolId] = schoolId
            it[SyncLog.entityType] = EntityType.STUDENT.name
            it[SyncLog.entityId] = studentId
            it[SyncLog.operation] = SyncOperation.CREATE
            it[SyncLog.userId] = userId
        }

        getStudent(studentId, schoolId)!!
    }
}

// Keep auto-generated UUID version for direct API usage
fun createStudent(
    schoolId: UUID,
    firstName: String,
    lastName: String,
    userId: UUID
): StudentDto { /* auto-generate UUID */ }
```

**Version-Based Conflict Detection:**
```kotlin
fun updateStudent(/* ... */, version: Long, /* ... */): UpdateResult {
    return transaction {
        val existing = Students.selectAll()
            .where { (Students.id eq studentId) and (Students.schoolId eq schoolId) }
            .singleOrNull() ?: return@transaction UpdateResult.NotFound

        // Check version for optimistic locking
        if (existing[Students.version] != version) {
            return@transaction UpdateResult.VersionConflict
        }

        // Update with incremented version
        Students.update({ /* ... */ }) {
            it[Students.version] = version + 1
            // ... other fields
        }

        // Log UPDATE to SyncLog
        SyncLog.insert { /* ... */ }
    }
}
```

### Client-Side Patterns

**SQLDelight Schemas Must Include:**
- `id TEXT PRIMARY KEY` (UUIDs as strings)
- `version INTEGER NOT NULL DEFAULT 1`
- `isSynced INTEGER NOT NULL DEFAULT 0`
- `localUpdatedAt INTEGER NOT NULL`
- `createdAt INTEGER NOT NULL`
- `updatedAt INTEGER NOT NULL`

**Sync Tables:**
- `SyncMetadata.sq`: Tracks `lastSyncLogId` per entity type
- `PendingSync.sq`: Queues unsynced local changes with retry logic

**Platform-Specific Connectivity:**
- Use `expect class ConnectivityMonitor` with `actual` implementations
- Android: `ConnectivityManager` with `NetworkCallback`
- iOS: `NWPathMonitor`
- Desktop: Periodic socket checks
- Web: `navigator.onLine` with event listeners

### Testing Requirements

**ALL sync functionality MUST have end-to-end tests:**
1. **Server CRUD Tests**: Verify sync logs are created for all operations
2. **Sync Service Tests**: Test push/pull endpoints, pagination, conflicts
3. **Offline-to-Online Tests**: Simulate complete workflow (offline create → push → pull)
4. **Conflict Resolution Tests**: Test version conflicts with multiple clients
5. **School Isolation Tests**: Ensure multi-tenant data separation

**Test Naming Convention:**
- Server tests: `*ServiceEndToEndTest.kt`, `*SyncTest.kt`
- Use unique test prefixes: `testing${(10000..99999).random()}`
- Clean up test data in `@AfterTest` using pattern matching

**Example Test Structure:**
```kotlin
@Test
fun `test offline creation and sync workflow`() {
    // 1. Create offline (client-side UUID)
    val offlineId = UUID.randomUUID().toString()
    val entity = createEntityDto(id = offlineId, ...)

    // 2. Push to server
    val pushRequest = PushChangesRequest(
        changes = listOf(
            PushChangeRequest(
                entityType = "STUDENT",
                entityId = offlineId,
                operation = "CREATE",
                version = 1L,
                data = json.encodeToString(entity)
            )
        )
    )
    val response = syncService.pushChanges(schoolId, userId, pushRequest)

    // 3. Verify server has entity with same UUID
    assertEquals(1, response.successCount)
    val serverEntity = studentService.getStudent(UUID.fromString(offlineId), schoolId)
    assertNotNull(serverEntity)
    assertEquals(offlineId, serverEntity.id)

    // 4. Verify other clients can pull
    val pullResponse = syncService.getChangesSince(schoolId, sinceLogId = 0L)
    assertEquals(1, pullResponse.changes.size)
    assertEquals("CREATE", pullResponse.changes[0].operation)
}

## Git Workflow
- **IMPORTANT:** Always `git add` new files immediately after creating them. Untracked files are dangerous as they can be lost or forgotten.
- Stage new files before finishing a task to ensure nothing is left behind.
- You should NEVER make the final commit. This will be done by the user.

