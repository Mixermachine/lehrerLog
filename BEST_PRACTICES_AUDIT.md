# LehrerLog Best Practices Audit Report

**Date:** 2026-02-01
**Project:** LehrerLog - Kotlin Multiplatform Application
**Audit Scope:** Full codebase analysis for best practice violations

---

## Executive Summary

This document outlines deviations from best practices found in the LehrerLog project. Issues are categorized by
severity (Critical, High, Medium, Low) with specific file locations and actionable recommendations.

**Total Issues Found:** 27

- Critical: 3
- High: 5
- Medium: 9
- Low: 10

---

## Critical Issues (Immediate Action Required)

### 1. Unsafe Non-Null Assertion Operator Usage

**Files:**

- `server/src/main/kotlin/de/aarondietz/lehrerlog/routes/FileRoute.kt:38`
- `server/src/main/kotlin/de/aarondietz/lehrerlog/routes/StudentRoute.kt:22, 36`

**Code Pattern:**

```kotlin
val principal = call.principal<UserPrincipal>()!!
```

**Issue:** Using `!!` operator bypasses Kotlin's null safety. If principal is null, application crashes with NPE instead
of gracefully handling the error.

**Impact:** Production crashes, poor user experience, violates Kotlin best practices.

**Recommendation:**

```kotlin
// Replace with:
val principal = call.principal<UserPrincipal>() ?: run {
    call.respond(HttpStatusCode.Unauthorized, "Authentication required")
    return@post
}
```

---

### 2. Default JWT Secret in Development

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/auth/JwtConfig.kt:4`

**Code:**

```kotlin
val secret: String = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production"
```

**Issue:** Hardcoded default secret. If `JWT_SECRET` environment variable is not set in production, tokens can be forged
by anyone knowing this default.

**Impact:** Complete authentication bypass possible.

**Recommendation:**

```kotlin
val secret: String = System.getenv("JWT_SECRET")
    ?: error("JWT_SECRET environment variable must be set")
```

---

### 3. Database Credentials Logging

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/db/DatabaseFactory.kt:27`

**Code:**

```kotlin
println("Initializing database: $jdbcUrl")
```

**Issue:** Using `println` instead of logger. May expose sensitive database connection strings in stdout/logs.

**Impact:** Potential exposure of database URLs/credentials in production logs.

**Recommendation:**

```kotlin
logger.info { "Initializing database connection" }
// Don't log the full JDBC URL or sanitize it first
```

---

## High Severity Issues

### 1. JVM Version Mismatch Across Modules

**Files:**

- `server/build.gradle.kts:13` → JVM 21
- `composeApp/build.gradle.kts:168` → JVM 11
- `shared/build.gradle.kts:14` → JVM 11

**Code:**

```kotlin
// Server uses: JvmTarget.JVM_21
// ComposeApp androidTarget uses: JvmTarget.JVM_11
// Shared androidTarget uses: JvmTarget.JVM_11
```

**Issue:** Inconsistent JVM targets across modules. Shared code compiled for JVM 11 used by server targeting JVM 21
could cause compatibility issues.

**Impact:** Potential compilation or runtime issues, especially for shared code.

**Recommendation:**

```kotlin
// Standardize all modules to JVM 21
jvmToolchain(21)
// Update all module build.gradle.kts files
```

---

### 2. Missing Input Validation on Authentication

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/routes/AuthRoute.kt:67-80`

**Issue:**

- Password validation only checks 8-character minimum
- No complexity requirements (uppercase, lowercase, numbers, special chars)
- No rate limiting on registration/login endpoints
- No account lockout after failed attempts

**Impact:** Weak passwords allowed, brute force attacks possible.

**Recommendation:**

```kotlin
// Add password complexity validation
private fun validatePassword(password: String): Boolean {
    return password.length >= 12 &&
            password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } &&
            password.any { it.isDigit() } &&
            password.any { !it.isLetterOrDigit() }
}

// Add rate limiting middleware
install(RateLimit) {
    register(RateLimitName("auth")) {
        rateLimiter(limit = 5, refillPeriod = 60.seconds)
    }
}
```

// Add proper UI validation on client which informs user what is required.

---

### 3. Unsafe Type Casting Without Null Checks

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/routes/FileRoute.kt:38`

**Issue:** Force casting with `!!` operator without validation.

**Impact:** Runtime crashes if assumptions are violated.

**Recommendation:** See Critical Issue #1 - same pattern.

---

### 4. Resource Not Properly Closed

**File:** `composeApp/build.gradle.kts:88-106`

**Code Location:** `Application.kt:195-216`

**Code:**

```kotlin
val client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(2))
    .build()
// ... used but never .close() called
```

**Issue:** HttpClient created but never closed, leading to resource leaks.

**Impact:** Connection pool exhaustion, memory leaks in long-running applications.

**Recommendation:**

```kotlin
// Use try-with-resources or implement AutoCloseable
class MyService : AutoCloseable {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    override fun close() {
        client.close()
    }
}
```

---

### 5. Android Minimum SDK Below Recommended

**File:** `gradle/libs.versions.toml:4`

**Code:**

```gradle
android-minSdk = "24"  # Android 7.0, released 2016
```

**Issue:** Min SDK 24 (Android 7.0 from 2016) is outdated. Google recommends SDK 26+ for new apps. Older versions have
known security vulnerabilities and limited modern API support.

**Impact:**

- Security vulnerabilities in old Android versions
- Reduced access to modern APIs
- Maintenance burden for legacy support

**Recommendation:**

```gradle
android-minSdk = "26"  # Android 8.0
```

---

## Medium Severity Issues

### 1. Database Connection Pool Not Configured

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/db/DatabaseFactory.kt`

**Issue:** Exposed ORM connection doesn't explicitly configure connection pool size, timeout settings, or maximum pool
size.

**Impact:** Potential connection exhaustion under load, no backpressure handling.

**Recommendation:**

```kotlin
Database.connect(
    url = jdbcUrl,
    driver = "org.postgresql.Driver",
    user = dbUser,
    password = dbPassword,
    setupConnection = { connection ->
        connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    },
    manager = {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = this@connect.url
            username = dbUser
            password = dbPassword
            maximumPoolSize = 20
            minimumIdle = 5
            idleTimeout = 300000
            connectionTimeout = 10000
        })
    }
)
```

---

### 2. Missing Global Exception Handler in Routes

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/routes/AuthRoute.kt:62-80`

**Issue:** Routes don't have global exception handlers for unhandled exceptions in request parsing or service calls.

**Impact:** Generic 500 errors, poor debugging experience, potential information leakage through stack traces.

**Recommendation:**

```kotlin
// In Application.kt
install(StatusPages) {
    exception<Throwable> { call, cause ->
        logger.error(cause) { "Unhandled exception" }

        val (status, message) = when (cause) {
            is IllegalArgumentException -> HttpStatusCode.BadRequest to "Invalid request"
            is NoSuchElementException -> HttpStatusCode.NotFound to "Resource not found"
            else -> HttpStatusCode.InternalServerError to "Internal server error"
        }

        call.respond(status, mapOf("error" to message))
    }
}
```

---

### 3. CORS Configuration Too Permissive

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/Application.kt:89-113`

**Code:**

```kotlin
install(CORS) {
    allowHost("localhost:8080", schemes = listOf("http"))
    allowHost("localhost:8081", schemes = listOf("http"))
    allowHost("127.0.0.1:8080", schemes = listOf("http"))
    allowHost("127.0.0.1:8081", schemes = listOf("http"))
    allowCredentials = true
```

**Issue:** Multiple redundant localhost entries. `allowCredentials = true` with broad hosts increases CSRF exposure.

**Impact:** Potential CSRF attacks if combined with other vulnerabilities.

**Recommendation:**

```kotlin
install(CORS) {
    val environment = System.getenv("ENVIRONMENT") ?: "production"

    if (environment == "development") {
        allowHost("localhost:8080", schemes = listOf("http"))
        allowHost("localhost:8081", schemes = listOf("http"))
    }

    // Production hosts
    allowHost("app.lehrerlog.de", schemes = listOf("https"))
    allowHost("app.qa.lehrerlog.de", schemes = listOf("https"))

    allowCredentials = true
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Authorization)
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
}
```

---

### 4. SQL Health Check Without Error Logging

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/Application.kt:147-150`

**Code:**

```kotlin
val dbHealthy = try {
    transaction {
        exec("SELECT 1") { it.next() }
    }
    true
} catch (e: Exception) {
    false
}
```

**Issue:** Exception silently caught and converted to boolean. No logging of actual database errors.

**Impact:** Difficult to diagnose database connectivity issues in production.

**Recommendation:**

```kotlin
val dbHealthy = try {
    transaction {
        exec("SELECT 1") { it.next() }
    }
    logger.debug { "Database health check passed" }
    true
} catch (e: Exception) {
    logger.error(e) { "Database health check failed: ${e.message}" }
    false
}
```

---

### 5. Missing @BeforeTest Cleanup Pattern

**File:** `server/src/test/kotlin/de/aarondietz/lehrerlog/AuthEndToEndTest.kt:40-60`

**Issue:** Test setup uses unique prefixes but cleanup patterns not consistently documented. Some tests may not properly
clean up between runs.

**Impact:** Test data accumulation, potential flaky tests from state leakage.

**Recommendation:**

```kotlin
@BeforeTest
fun setupTest() {
    // Clear any existing test data with our prefix
    transaction {
        UserTable.deleteWhere {
            UserTable.email like "$TEST_PREFIX%"
        }
    }
}

@AfterTest
fun cleanupTest() {
    // Ensure cleanup even on test failure
    transaction {
        UserTable.deleteWhere {
            UserTable.email like "$TEST_PREFIX%"
        }
    }
}
```

---

### 6. Docker Base Images Not Pinned to Specific Versions

**Files:**

- `server/Dockerfile:1` → `gradle:8.7-jdk21`
- `server/Dockerfile:6` → `eclipse-temurin:21-jre`
- `composeApp/Dockerfile:1` → `gradle:8.14-jdk21`
- `composeApp/Dockerfile:31` → `nginx:alpine`

**Issue:** Base images use minor version tags instead of full semantic versioning with SHA256 digests.

**Impact:** Non-reproducible builds, unexpected updates could break builds, potential security patches applied without
testing.

**Recommendation:**
Use gradle:8.14.4-jdk21 where gradle is required

```dockerfile
# Pin to specific versions with SHA256
FROM gradle:8.14.4-jdk21@sha256:abc123... AS build
# Or at minimum use full version tags
FROM eclipse-temurin:21.0.1-jre

# Document why each version is chosen
# Update policy: Check for updates monthly, test before upgrading
```

---

### 7. No Request Size Limits on File Uploads

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/routes/FileRoute.kt:55-59`

**Issue:** Multipart file upload doesn't validate total request size before processing. Only validates individual file
size after parsing.

**Impact:** Potential DoS through extremely large uploads consuming memory/disk.

**Recommendation:**

```kotlin
// In Application.kt
install(DataConversion) {
    // Limit request body size
}

// Or in the route
val maxRequestSize = 100 * 1024 * 1024 // 100MB -> Make this configurable in server.env
if (call.request.header("Content-Length")?.toLongOrNull() ?: 0 > maxRequestSize) {
    call.respond(HttpStatusCode.PayloadTooLarge, "Request too large")
    return@post
}
```

---

### 8. Inconsistent Error Response Format

**Various route files**

**Issue:** Some routes return error messages as plain strings, others as JSON objects. No consistent API error response
format.

**Impact:** Client applications need to handle multiple error formats.

**Recommendation:**

```kotlin
// Define standard error response DTO
data class ErrorResponseDto(
    val error: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val path: String? = null
)

// Use consistently across all routes
call.respond(
    HttpStatusCode.BadRequest,
    ErrorResponseDto(
        error = "VALIDATION_ERROR",
        message = "Email is required",
        path = call.request.path()
    )
)
```

---

### 9. Missing API Versioning Strategy

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/Application.kt`

**Issue:** No API versioning in routes. All endpoints at root level without version prefix (e.g., `/api/v1/...`).

**Impact:** Breaking changes in API require coordination with all clients. No graceful migration path.

**Recommendation:**

```kotlin
routing {
    route("/api/v1") {
        authRoutes()
        studentRoutes()
        teacherRoutes()
        // ... other routes
    }

    // Future version
    route("/api/v2") {
        // New version of APIs
    }
}
```

---

## Low Severity Issues

### 1. Inconsistent String Trimming Pattern

**Files:** Multiple locations in services and routes

**Code:**

```kotlin
// Pattern 1:
val value = System.getenv("VAR")?.trim().orEmpty()

// Pattern 2:
val value = System.getenv("VAR")?.trim().orEmpty().ifBlank { "default" }

// Pattern 3:
val value = System.getenv("VAR")?.trim() ?: ""
```

**Issue:** Three different patterns for same operation reduces code readability and maintainability.

**Impact:** Minor code quality issue, inconsistent style.

**Recommendation:** Standardize on one pattern:

```kotlin
// Recommended: Most explicit and clear
fun getEnvOrDefault(key: String, default: String = ""): String {
    return System.getenv(key)?.trim()?.takeIf { it.isNotBlank() } ?: default
}
```

---

### 2. Test Prefix Generated at Class Load Time

**File:** `server/src/test/kotlin/de/aarondietz/lehrerlog/AuthEndToEndTest.kt:48`

**Code:**

```kotlin
companion object {
    private val TEST_PREFIX = "testing${(10000..99999).random()}"
}
```

**Issue:** Random number generated once when class loads, not per test instance. If tests run in parallel, all tests in
same class share same prefix.

**Impact:** Could cause test conflicts in parallel execution, flaky tests.

**Recommendation:**

```kotlin
// Generate per test run, not per class
@BeforeTest
fun setup() {
    testPrefix = "testing${(10000..99999).random()}"
}

private lateinit var testPrefix: String
```

---

### 3. Potential Deprecated API Usage

**File:** `gradle/libs.versions.toml:14`

**Code:**

```gradle
composeMaterial3 = "1.9.0"
```

**Issue:** Material3 version may be outdated. No version constraint strategy or update policy documented.

**Impact:** Potential missed security/performance updates, deprecated API usage.

**Recommendation:**

```gradle
# Document update policy in libs.versions.toml
# Update material3 to latest stable version
composeMaterial3 = "1.3.1"  # Check for latest at time of fix

# Add Gradle version catalog update plugin
# plugins {
#     id("nl.littlerobots.version-catalog-update") version "0.8.4"
# }
```

---

### 4. Health Check Doesn't Validate Response Content

**File:** `composeApp/Dockerfile:42`

**Code:**

```dockerfile
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget -q --spider http://localhost/health || exit 1
```

**Issue:** Only checks HTTP 200 status, doesn't validate response content. Could pass even if app returns error page
with 200 status.

**Impact:** False-positive health checks, unhealthy containers marked as healthy.

**Recommendation:**

```dockerfile
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget -q -O - http://localhost/health | grep -q '"status":"healthy"' || exit 1
```

---

### 5. Unused Imports in Multiple Files

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/services/FileStorageService.kt:14-16`

**Issue:** Wildcard imports and unused imports reduce code clarity.

**Impact:** Code maintainability, slower IDE performance.

**Recommendation:**

```kotlin
// Enable in .editorconfig or IDE settings
[ * . kt]
ij_kotlin_packages_to_use_import_on_demand = unset
ij_kotlin_name_count_to_use_star_import = 999
ij_kotlin_name_count_to_use_star_import_for_members = 999

// Run kotlinter or ktlint
    ./ gradlew formatKotlin
```

---

### 6. Base64 Decoding Without Proper Error Handling

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/Application.kt:332-340`

**Code:**

```kotlin
val encoded = System.getenv("SEED_TEST_USER_PASSWORD_B64")?.trim().orEmpty()
if (encoded.isNotBlank()) {
    return try {
        String(Base64.getDecoder().decode(encoded)).trim()
    } catch (e: IllegalArgumentException) {
        null
    }
}
```

**Issue:** Silent failure on decode error. Caller needs to check null and read env var again to detect misconfiguration.

**Impact:** Confusing logic flow, misconfiguration not detected early.

**Recommendation:**

```kotlin
val encoded = System.getenv("SEED_TEST_USER_PASSWORD_B64")?.trim()
if (!encoded.isNullOrBlank()) {
    return try {
        String(Base64.getDecoder().decode(encoded)).trim()
    } catch (e: IllegalArgumentException) {
        logger.error { "Failed to decode SEED_TEST_USER_PASSWORD_B64: ${e.message}" }
        throw IllegalStateException("Invalid base64 in SEED_TEST_USER_PASSWORD_B64", e)
    }
}
```

---

### 7. Missing Correlation IDs in Logs

**All server files**

**Issue:** No request correlation IDs in logs. Difficult to trace a single request through multiple service calls.

**Impact:** Debugging distributed transactions is difficult.

**Recommendation:**

```kotlin
install(CallLogging) {
    level = Level.INFO

    // Add correlation ID to MDC
    mdc("correlationId") {
        it.request.header("X-Correlation-ID")
            ?: UUID.randomUUID().toString()
    }

    format { call ->
        val correlationId = call.mdc("correlationId")
        val status = call.response.status()
        val method = call.request.httpMethod.value
        val path = call.request.path()
        "[$correlationId] $method $path - $status"
    }
}
```

---

### 8. No Database Migration Rollback Scripts

**Status:** ⏸️ **DEFERRED - Not to be executed for now**

**Directory:** `server/src/main/resources/db/migration/`

**Issue:** Flyway migrations only have "up" migrations. No documented rollback procedures for failed deployments.

**Impact:** Difficult to recover from failed migrations in production.

**Decision:** Before going live, production database will be reset and all migrations will be consolidated into a single
migration file. Rollback procedures will be implemented after initial production deployment.

**Recommendation (for future, post-launch):**

```sql
-- For each migration Vnnn__description.sql
-- Create Unnn__description.sql with rollback steps
-- Document in DEPLOYMENT.md:
-- "To rollback migration V015:
--  1. Restore database backup
--  2. OR manually run U015__rollback_xxx.sql
--  3. Run flyway repair"
```

**Action Required:** Consolidate all existing migrations into one file before production launch.

---

### 9. Missing Rate Limiting on Public Endpoints

**Files:** All route files

**Issue:** No rate limiting middleware installed for public endpoints (registration, login, password reset).

**Impact:** Vulnerable to brute force attacks, resource exhaustion.

**Recommendation:**

```kotlin
// Install rate limiting
install(RateLimit) {
    global {
        rateLimiter(limit = 100, refillPeriod = 60.seconds)
    }

    register(RateLimitName("auth")) {
        rateLimiter(limit = 5, refillPeriod = 60.seconds)
        requestKey { call ->
            call.request.origin.remoteHost
        }
    }
}

// Apply to routes
route("/auth") {
    rateLimit(RateLimitName("auth")) {
        post("/login") { ... }
        post("/register") { ... }
    }
}
```

---

### 10. Environment Variables Not Validated on Startup

**File:** `server/src/main/kotlin/de/aarondietz/lehrerlog/Application.kt`

**Issue:** Required environment variables (DB credentials, JWT secret) are read lazily. App may start successfully but
fail on first request.

**Impact:** Deployment issues not caught early, difficult to diagnose.

**Recommendation:**

```kotlin
fun Application.module() {
    // Validate required env vars on startup
    validateEnvironment()

    // ... rest of configuration
}

fun validateEnvironment() {
    val required = listOf(
        "DATABASE_URL",
        "DATABASE_USER",
        "DATABASE_PASSWORD",
        "JWT_SECRET"
    )

    val missing = required.filter { System.getenv(it).isNullOrBlank() }

    if (missing.isNotEmpty()) {
        error("Missing required environment variables: ${missing.joinToString()}")
    }

    logger.info { "Environment validation passed" }
}
```

---

## Positive Observations ✓

### Architectural Strengths

1. **Proper Expect/Actual Usage**: Correctly uses `expect/actual` for platform-specific code (TokenStorage, FilePicker,
   Time) rather than over-relying on interfaces.

2. **Clean Separation of Concerns**:
    - Services layer properly encapsulates business logic
    - Routes handle HTTP concerns only
    - Database layer isolated with Exposed ORM
    - Clear MVVM pattern in Compose app

3. **Centralized Dependency Management**: Uses `libs.versions.toml` for version catalog, following Gradle best
   practices.

4. **Test Organization**: Follows consistent naming conventions (`*EndToEndTest.kt`, `*ServiceTest.kt`) with unique test
   prefixes for isolation.

5. **Type Safety**: Good use of sealed classes for result types (UpdateResult, etc.).

6. **Coroutine Usage**: Proper use of structured concurrency with viewModelScope and proper dispatchers.

---

## Priority Matrix

| Priority      | Category          | Count | Estimated Effort |
|---------------|-------------------|-------|------------------|
| P0 (Critical) | Security          | 3     | 1.5-2 days       |
| P1 (High)     | Security, Quality | 5     | 3-4 days         |
| P2 (Medium)   | Quality, DevOps   | 9     | 5-7 days         |
| P3 (Low)      | Maintenance       | 10    | 3-5 days         |

**Total Estimated Effort:** 13-18 days (can be parallelized)

---

## Recommended Implementation Plan

### Phase 1: Critical Security Fixes (Week 1)

- [ ] Replace all `!!` operators with safe null handling
- [ ] Make JWT_SECRET required (fail fast if missing)
- [ ] Replace println with structured logging
- [ ] Validate all required env vars on startup

### Phase 2: High Priority Fixes (Week 2)

- [ ] Standardize JVM target to 21 across all modules
- [ ] Implement password complexity requirements
- [ ] Add rate limiting to auth endpoints
- [ ] Fix resource leaks (HttpClient, database connections)
- [ ] Update Android minSdk to 26+

### Phase 3: Medium Priority Improvements (Week 3)

- [ ] Configure database connection pooling
- [ ] Add global exception handler middleware
- [ ] Refine CORS configuration
- [ ] Add proper error logging throughout
- [ ] Pin Docker base image versions
- [ ] Implement consistent error response format
- [ ] Add API versioning (/api/v1)

### Phase 4: Low Priority Polish (Week 4)

- [ ] Standardize code patterns (string trimming, etc.)
- [ ] Fix test prefix generation for parallel execution
- [ ] Update dependencies to latest stable versions
- [ ] Improve health check validation
- [ ] Remove unused imports (run ktlint)
- [ ] Add request correlation IDs
- [ ] ~~Document rollback procedures~~ ⏸️ **DEFERRED** (consolidate migrations first)
- [ ] Add rate limiting to all public endpoints

---

## Testing Requirements

After each fix:

1. **Unit Tests**: Verify the specific fix works
2. **Integration Tests**: Ensure no regressions
3. **Coverage Check**: Run `:server:checkCoverage` and `:composeApp:checkCoverage`
4. **Security Test**: For security fixes, attempt to exploit the old vulnerability

---

## Monitoring & Verification

### Success Metrics

- [ ] Zero `!!` operators in production code
- [ ] All environment variables validated on startup
- [ ] Structured logging in 100% of server code
- [ ] Test coverage maintained at 60%+
- [ ] Zero critical security vulnerabilities
- [ ] All Docker images pinned to specific versions
- [ ] JVM target consistent across all modules

### Ongoing Maintenance

- Monthly dependency updates
- Quarterly security audit
- Monitor error logs for unhandled exceptions
- Track API error rates after changes

---

## Risk Assessment

| Risk                                       | Probability | Impact | Mitigation                              |
|--------------------------------------------|-------------|--------|-----------------------------------------|
| Breaking changes during fixes              | Medium      | High   | Comprehensive test suite, feature flags |
| Performance degradation from rate limiting | Low         | Medium | Monitor response times, adjust limits   |
| Migration failures                         | Low         | High   | Test in QA, backup before production    |
| Dependency conflicts                       | Medium      | Medium | Update one at a time, test thoroughly   |

---

## Document History

| Date       | Version | Author      | Changes              |
|------------|---------|-------------|----------------------|
| 2026-02-01 | 1.0     | Claude Code | Initial audit report |

---

## Next Steps

1. **Review** this document with the team
2. **Prioritize** based on business impact and risk
3. **Assign** issues to team members
4. **Create** tracking tickets for each fix
5. **Schedule** implementation sprints
6. **Monitor** progress and update this document

---

## Enhancement Proposals

This section documents planned enhancements to improve the codebase beyond fixing existing issues.

---

## ENHANCEMENT 1: Centralized Client-Side Logging with File Rotation

**Category:** Quality of Life, Debugging, Production Support
**Priority:** Medium
**Estimated Effort:** 3-4 days
**Status:** Planned

---

### Overview

Implement centralized file-based logging for the client application (composeApp) to improve production debugging
capabilities. Currently, logs only go to console, making it impossible to diagnose issues in production on user devices.

**Goals:**

- ✅ Log to both console (development) and files (production)
- ✅ Support all multiplatform targets (Android, iOS, Desktop JVM, Web/Wasm)
- ✅ Implement automatic log rotation when files reach 2MB
- ✅ Keep logs from last 7 days or max 5 files
- ✅ Use existing Kermit library (already integrated at version 2.0.8)
- ✅ Platform-specific file storage locations
- ✅ Web/Wasm: OPFS (Origin Private File System) with 90%+ browser support

---

### Current State Analysis

**Kermit Already Integrated:**

- **Version:** `2.0.8` (from `gradle/libs.versions.toml`)
- **Dependency:** Already in `composeApp/build.gradle.kts:259`
- **Configuration:** Basic setup in `composeApp/src/commonMain/kotlin/de/aarondietz/lehrerlog/AppModule.kt`

```kotlin
single {
    Logger.withTag("LehrerLog")
}
```

**Current Usage Pattern:**

```kotlin
// In ViewModels and Repositories:
logger.d { "Loading user data" }              // Debug
logger.i { "User authenticated" }             // Info
logger.w { "Cache miss, fetching from API" }  // Warning
logger.e(exception) { "Failed to load data" } // Error
```

**Current Output:**

- ✅ Console only (all platforms)
- ❌ No file logging
- ❌ No persistence
- ❌ No rotation
- ❌ Cannot debug production issues

**Files Currently Using Kermit:**

- `SchoolClassRepository.kt`
- `LatePeriodManagementViewModel.kt`
- `ParentInviteManagementViewModel.kt`
- `SettingsViewModel.kt`
- `StudentsViewModel.kt`
- `TasksViewModel.kt`

---

### Architecture Design

#### Use Expect/Actual Pattern for Platform-Specific File I/O

**Common Interface** (`composeApp/src/commonMain/kotlin/de/aarondietz/lehrerlog/logging/LogFileWriter.kt`):

```kotlin
/**
 * Platform-specific log file writer with automatic rotation.
 */
expect class LogFileWriter {
    /**
     * Initialize the log writer with storage configuration.
     * @param maxFileSizeMB Maximum size of a single log file before rotation (default: 2MB)
     * @param maxFiles Maximum number of log files to keep (default: 5)
     */
    fun initialize(maxFileSizeMB: Int = 2, maxFiles: Int = 5)

    /**
     * Write a log entry to file.
     * Format: "YYYY-MM-DD HH:mm:ss.SSS [LEVEL] [TAG] Message"
     */
    fun writeLog(
        timestamp: String,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null
    )

    /**
     * Get all log files (newest first) for export/sharing.
     * @return List of file paths or URIs
     */
    fun getLogFiles(): List<String>

    /**
     * Clear all log files.
     */
    fun clearLogs()

    /**
     * Get current log file size in bytes.
     */
    fun getCurrentLogSize(): Long

    /**
     * Trigger manual rotation (useful for testing).
     */
    fun rotateNow()
}
```

**Kermit Integration** (update `AppModule.kt`):

```kotlin
single {
    val fileWriter = LogFileWriter().apply {
        initialize(maxFileSizeMB = 2, maxFiles = 5)
    }

    val logger = Logger(
        config = StaticConfig(
            logWriterList = listOf(
                platformLogWriter(),           // Console output
                KermitFileLogWriter(fileWriter) // File output
            ),
            minSeverity = if (isDebug()) Severity.Debug else Severity.Info
        ),
        tag = "LehrerLog"
    )

    logger
}
```

**Custom Kermit LogWriter Wrapper:**

```kotlin
// composeApp/src/commonMain/kotlin/de/aarondietz/lehrerlog/logging/KermitFileLogWriter.kt

class KermitFileLogWriter(
    private val fileWriter: LogFileWriter
) : co.touchlab.kermit.LogWriter() {

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val timestamp = Clock.System.now().toString()
        val level = severity.name.uppercase()

        fileWriter.writeLog(
            timestamp = timestamp,
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )
    }
}
```

---

### Platform-Specific Implementations

#### Android Implementation

**File:** `composeApp/src/androidMain/kotlin/de/aarondietz/lehrerlog/logging/LogFileWriter.android.kt`

**Storage Location:** `/data/data/de.aarondietz.lehrerlog/cache/logs/`
**Permissions:** None required (app-specific storage)

```kotlin
actual class LogFileWriter {
    private lateinit var logDir: File
    private lateinit var currentLogFile: File
    private var maxFileSizeBytes: Long = 2 * 1024 * 1024 // 2MB
    private var maxFiles: Int = 5

    actual fun initialize(maxFileSizeMB: Int, maxFiles: Int) {
        this.maxFileSizeBytes = maxFileSizeMB * 1024L * 1024L
        this.maxFiles = maxFiles

        // Use app-specific cache directory (no permissions needed)
        val context = AndroidPlatform.applicationContext
        logDir = File(context.cacheDir, "logs").apply { mkdirs() }
        currentLogFile = File(logDir, "app_${getCurrentDateString()}.log")
        cleanupOldLogs()
    }

    actual fun writeLog(
        timestamp: String,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        if (getCurrentLogSize() >= maxFileSizeBytes) {
            rotateNow()
        }

        val logEntry = buildString {
            append(timestamp)
            append(" [").append(level).append("]")
            append(" [").append(tag).append("] ")
            append(message)
            if (throwable != null) {
                append("\n").append(throwable.stackTraceToString())
            }
            append("\n")
        }

        try {
            currentLogFile.appendText(logEntry)
        } catch (e: IOException) {
            println("Failed to write log: ${e.message}")
        }
    }

    // ... rotation and cleanup methods
}
```

---

#### iOS Implementation

**File:** `composeApp/src/iosMain/kotlin/de/aarondietz/lehrerlog/logging/LogFileWriter.ios.kt`

**Storage Location:** `~/Library/Application Support/de.aarondietz.lehrerlog/logs/`
**Permissions:** None required (app sandbox)

```kotlin
actual class LogFileWriter {
    private lateinit var logDir: String
    private lateinit var currentLogFile: String
    private var maxFileSizeBytes: Long = 2 * 1024 * 1024
    private var maxFiles: Int = 5

    actual fun initialize(maxFileSizeMB: Int, maxFiles: Int) {
        this.maxFileSizeBytes = maxFileSizeMB * 1024L * 1024L
        this.maxFiles = maxFiles

        // Use Application Support directory (persistent)
        val fileManager = NSFileManager.defaultManager
        val appSupportURL = fileManager.URLsForDirectory(
            NSApplicationSupportDirectory,
            NSUserDomainMask
        ).first() as NSURL

        logDir = appSupportURL.path!! + "/logs"
        fileManager.createDirectoryAtPath(
            logDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )

        currentLogFile = "$logDir/app_${getCurrentDateString()}.log"
        cleanupOldLogs()
    }

    // ... iOS-specific file operations using NSFileManager
}
```

---

#### Desktop JVM Implementation

**File:** `composeApp/src/jvmMain/kotlin/de/aarondietz/lehrerlog/logging/LogFileWriter.jvm.kt`

**Storage Location:** `~/.lehrerlog/logs/` (follows existing TokenStorage pattern)
**Permissions:** None required (user home directory)

```kotlin
actual class LogFileWriter {
    private lateinit var logDir: File
    private lateinit var currentLogFile: File
    private var maxFileSizeBytes: Long = 2 * 1024 * 1024
    private var maxFiles: Int = 5

    actual fun initialize(maxFileSizeMB: Int, maxFiles: Int) {
        this.maxFileSizeBytes = maxFileSizeMB * 1024L * 1024L
        this.maxFiles = maxFiles

        // Use same pattern as TokenStorage: ~/.lehrerlog/
        val userHome = System.getProperty("user.home")
        logDir = File(userHome, ".lehrerlog/logs").apply { mkdirs() }
        currentLogFile = File(logDir, "app_${getCurrentDateString()}.log")
        cleanupOldLogs()
    }

    actual fun writeLog(
        timestamp: String,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        if (getCurrentLogSize() >= maxFileSizeBytes) {
            rotateNow()
        }

        val logEntry = buildString {
            append(timestamp)
            append(" [").append(level).append("]")
            append(" [").append(tag).append("] ")
            append(message)
            if (throwable != null) {
                append("\n").append(throwable.stackTraceToString())
            }
            append("\n")
        }

        synchronized(this) {
            currentLogFile.appendText(logEntry)
        }
    }

    // ... rotation and cleanup methods
}
```

---

#### Web/Wasm Implementation (OPFS - Origin Private File System)

**File:** `composeApp/src/wasmJsMain/kotlin/de/aarondietz/lehrerlog/logging/LogFileWriter.wasmJs.kt`

**Storage:** OPFS (Origin Private File System) - Modern browser file system API
**Browser Support:** Chrome 86+, Edge 86+, Safari 15.2+, Firefox 111+ (90%+ coverage)

**About OPFS:**

- Origin-private file system isolated from user files
- Persistent across sessions (unlike SessionStorage)
- Much larger storage than localStorage (typically GBs vs 5-10MB)
- Fast, asynchronous file operations
- Not accessible by users (secure, private to the app)
- Part of File System Access API standard

```kotlin
actual class LogFileWriter {
    private var currentLogFileName: String = ""
    private var maxFileSizeBytes: Long = 2 * 1024 * 1024
    private var maxFiles: Int = 5
    private var opfsSupported: Boolean = false

    actual fun initialize(maxFileSizeMB: Int, maxFiles: Int) {
        this.maxFileSizeBytes = maxFileSizeMB * 1024L * 1024L
        this.maxFiles = maxFiles

        // Check OPFS support
        opfsSupported = checkOPFSSupport()

        if (!opfsSupported) {
            console.warn("OPFS not supported in this browser. Logs will only appear in console.")
            return
        }

        currentLogFileName = "app_${getCurrentDateString()}.log"

        // Initialize OPFS and cleanup old logs (async)
        initializeOPFS()
    }

    actual fun writeLog(
        timestamp: String,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        if (!opfsSupported) return

        val logEntry = buildString {
            append(timestamp)
            append(" [").append(level).append("]")
            append(" [").append(tag).append("] ")
            append(message)
            if (throwable != null) {
                append("\n")
                append(throwable.stackTraceToString())
            }
            append("\n")
        }

        // Write to OPFS asynchronously
        writeToOPFS(currentLogFileName, logEntry)
    }

    actual fun getCurrentLogSize(): Long {
        // Would require async call to OPFS
        // For simplicity, track size in memory or use approximation
        return 0L // Implement if needed
    }

    actual fun rotateNow() {
        if (!opfsSupported) return

        val rotatedName = "app_${getCurrentDateTimeString()}.log"
        renameFileInOPFS(currentLogFileName, rotatedName)
        currentLogFileName = "app_${getCurrentDateString()}.log"
        cleanupOldLogsInOPFS()
    }

    actual fun getLogFiles(): List<String> {
        if (!opfsSupported) return emptyList()

        // This would need to be implemented with async OPFS API
        return listLogFilesFromOPFS()
    }

    actual fun clearLogs() {
        if (!opfsSupported) return
        clearAllLogsInOPFS()
    }

    // Browser-specific OPFS functions using js() interop
    private fun checkOPFSSupport(): Boolean {
        return js(
            """
            'storage' in navigator &&
            'getDirectory' in navigator.storage
        """
        ) as Boolean
    }

    private fun initializeOPFS() {
        js(
            """
            (async () => {
                try {
                    const root = await navigator.storage.getDirectory();
                    const logsDir = await root.getDirectoryHandle('logs', { create: true });

                    // Cleanup old logs on init
                    const now = Date.now();
                    const sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000);

                    for await (const [name, handle] of logsDir.entries()) {
                        if (handle.kind === 'file' && name.endsWith('.log')) {
                            const file = await handle.getFile();
                            if (file.lastModified < sevenDaysAgo) {
                                await logsDir.removeEntry(name);
                            }
                        }
                    }
                } catch (e) {
                    console.error('Failed to initialize OPFS:', e);
                }
            })();
        """
        )
    }

    private fun writeToOPFS(fileName: String, content: String) {
        js(
            """
            (async () => {
                try {
                    const root = await navigator.storage.getDirectory();
                    const logsDir = await root.getDirectoryHandle('logs', { create: true });
                    const fileHandle = await logsDir.getFileHandle($fileName, { create: true });

                    // Get existing content and append
                    const file = await fileHandle.getFile();
                    const existingContent = await file.text();

                    // Check if rotation needed (file > maxFileSizeBytes)
                    const newContent = existingContent + $content;
                    if (newContent.length > ${maxFileSizeBytes}) {
                        // Trigger rotation
                        const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, -5);
                        const rotatedName = fileName.replace('.log', `_${timestamp}.log`);

                        // Rename current file
                        const rotatedHandle = await logsDir.getFileHandle(rotatedName, { create: true });
                        const writable = await rotatedHandle.createWritable();
                        await writable.write(existingContent);
                        await writable.close();

                        // Write new content to fresh file
                        const writable2 = await fileHandle.createWritable();
                        await writable2.write($content);
                        await writable2.close();
                    } else {
                        // Append to existing file
                        const writable = await fileHandle.createWritable();
                        await writable.write(newContent);
                        await writable.close();
                    }
                } catch (e) {
                    console.error('Failed to write log to OPFS:', e);
                }
            })();
        """
        )
    }

    private fun renameFileInOPFS(oldName: String, newName: String) {
        js(
            """
            (async () => {
                try {
                    const root = await navigator.storage.getDirectory();
                    const logsDir = await root.getDirectoryHandle('logs', { create: true });

                    const oldHandle = await logsDir.getFileHandle($oldName);
                    const file = await oldHandle.getFile();
                    const content = await file.text();

                    const newHandle = await logsDir.getFileHandle($newName, { create: true });
                    const writable = await newHandle.createWritable();
                    await writable.write(content);
                    await writable.close();

                    await logsDir.removeEntry($oldName);
                } catch (e) {
                    console.error('Failed to rename log file:', e);
                }
            })();
        """
        )
    }

    private fun listLogFilesFromOPFS(): List<String> {
        // This is tricky because js() returns immediately
        // Would need Promise/callback handling in Kotlin/JS
        // For now, return empty and implement via Settings UI with suspend functions
        return emptyList()
    }

    private fun cleanupOldLogsInOPFS() {
        js(
            """
            (async () => {
                try {
                    const root = await navigator.storage.getDirectory();
                    const logsDir = await root.getDirectoryHandle('logs', { create: true });

                    const files = [];
                    for await (const [name, handle] of logsDir.entries()) {
                        if (handle.kind === 'file' && name.endsWith('.log')) {
                            const file = await handle.getFile();
                            files.push({ name, lastModified: file.lastModified });
                        }
                    }

                    // Sort by lastModified descending
                    files.sort((a, b) => b.lastModified - a.lastModified);

                    // Keep only maxFiles most recent
                    for (let i = ${maxFiles}; i < files.length; i++) {
                        await logsDir.removeEntry(files[i].name);
                    }
                } catch (e) {
                    console.error('Failed to cleanup old logs:', e);
                }
            })();
        """
        )
    }

    private fun clearAllLogsInOPFS() {
        js(
            """
            (async () => {
                try {
                    const root = await navigator.storage.getDirectory();
                    const logsDir = await root.getDirectoryHandle('logs', { create: true });

                    for await (const [name, handle] of logsDir.entries()) {
                        if (handle.kind === 'file' && name.endsWith('.log')) {
                            await logsDir.removeEntry(name);
                        }
                    }
                } catch (e) {
                    console.error('Failed to clear logs:', e);
                }
            })();
        """
        )
    }

    private fun getCurrentDateString(): String {
        return js("new Date().toISOString().split('T')[0]") as String
    }

    private fun getCurrentDateTimeString(): String {
        return js(
            """
            new Date().toISOString().replace(/[:.]/g, '-').slice(0, -5)
        """
        ) as String
    }
}
```

**Storage Location:** Browser OPFS → `opfs://origin/logs/app_*.log`
**Access:** Via DevTools → Application → Storage → Origin Private File System (Chrome/Edge)
**Permissions:** None required (origin-private)
**Persistence:** Until explicitly cleared or browser storage evicted

**Browser Compatibility:**

- ✅ Chrome/Edge 86+ (Full support)
- ✅ Safari 15.2+ (Full support)
- ✅ Firefox 111+ (Full support)
- ⚠️ Older browsers: Falls back to console-only logging

**Benefits over No-Op:**

- ✅ Persistent logs in browser for debugging
- ✅ Users can export logs for support tickets
- ✅ Survives page reloads and browser restarts
- ✅ Large storage capacity (GBs, not limited to 5MB)
- ✅ Same log management as native platforms

**Note:** For production use, consider adding a UI in Settings to download/export logs as a single file, since OPFS
files are not directly user-accessible.

---

### File Rotation Strategy

#### Size-Based Rotation (Primary)

- **Trigger:** When current log file exceeds 2MB (configurable)
- **Action:** Rename current file with timestamp, create new file
- **Naming:** `app_YYYY-MM-DD_HH-mm-ss.log`

#### Time-Based Cleanup (Secondary)

- **Daily Files:** One file per day (e.g., `app_2026-02-01.log`)
- **Rotation on Size:** If daily file exceeds 2MB, rotate with timestamp
- **Max Files:** Keep only 5 most recent files
- **Age Limit:** Delete files older than 7 days

#### File Naming Convention

```
app_2026-02-01.log              ← Current day's log
app_2026-02-01_14-30-00.log     ← Rotated (hit 2MB at 2:30 PM)
app_2026-02-01_16-45-00.log     ← Rotated again
app_2026-01-31.log              ← Previous day
app_2026-01-30.log              ← Older (deleted after 7 days)
```

#### Log Entry Format

```
2026-02-01T14:32:15.123Z [INFO] [LehrerLog] User authenticated successfully
2026-02-01T14:32:16.456Z [DEBUG] [Repository] Loading students for school 123
2026-02-01T14:32:17.789Z [ERROR] [NetworkClient] Failed to fetch data
java.net.SocketTimeoutException: timeout
    at okhttp3.internal.connection.RealCall.execute(RealCall.kt:123)
    ...
```

---

### Class-Specific Logger Extension (Automatic Tag Detection)

**Problem:** Current implementation uses a single global logger with tag `"LehrerLog"` for entire app. All logs show the
same tag, making it difficult to identify which class/component generated the log.

**Current Output:**

```
[LehrerLog] Loading students
[LehrerLog] Creating school class
[LehrerLog] Network error occurred
```

❌ No way to know which ViewModel/Repository logged each message.

**Solution:** Extension function that automatically generates class-specific loggers with proper tagging.

**Desired Output:**

```
[StudentsViewModel] Loading students
[SchoolClassRepository] Creating school class
[NetworkClient] Network error occurred
```

✅ Clear source identification for every log entry.

---

#### Architecture Design

**Extension Function Approach:**

Create a reusable extension that uses Kotlin's `reified` type parameters to automatically detect the calling class name
and create a properly configured logger.

**File:** `composeApp/src/commonMain/kotlin/de/aarondietz/lehrerlog/logging/LoggerExt.kt`

```kotlin
package de.aarondietz.lehrerlog.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter

/**
 * Creates a class-specific logger with automatic tag detection.
 *
 * Usage:
 * ```

* class MyViewModel : ViewModel() {
*     private val logger by lazy { logger() }
* }
* ```
*
* @return Logger configured with class name as tag
  */
  inline fun <reified T : Any> T.logger(): Logger {
  return createLogger(T::class.simpleName ?: "Unknown")
  }

/**

* Creates a logger with custom tag.
*
* Usage:
* ```
* private val logger by lazy { logger("CustomTag") }
* ```

*/
fun Any.logger(tag: String): Logger {
return createLogger(tag)
}

/**

* Internal function to create logger with consistent configuration.
* Retrieves LogFileWriter from global LoggerConfig if file logging is enabled.
  */
  private fun createLogger(tag: String): Logger {
  val config = LoggerConfig.getInstance()

  return Logger(
  config = StaticConfig(
  logWriterList = config.getLogWriters(),
  minSeverity = config.getMinSeverity()
  ),
  tag = tag
  )
  }

```

**Centralized Configuration Manager:**

```kotlin
// composeApp/src/commonMain/kotlin/de/aarondietz/lehrerlog/logging/LoggerConfig.kt

package de.aarondietz.lehrerlog.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter

/**
 * Centralized logger configuration shared by all logger instances.
 * Initialized once in AppModule, used by logger() extension function.
 */
class LoggerConfig private constructor() {
    private var fileWriter: LogFileWriter? = null
    private var minSeverity: Severity = Severity.Info
    private var fileLoggingEnabled: Boolean = true

    fun initialize(
        fileWriter: LogFileWriter,
        minSeverity: Severity = Severity.Info,
        enableFileLogging: Boolean = true
    ) {
        this.fileWriter = fileWriter
        this.minSeverity = minSeverity
        this.fileLoggingEnabled = enableFileLogging
    }

    fun getLogWriters(): List<LogWriter> {
        return buildList {
            // Always add console writer
            add(platformLogWriter())

            // Add file writer if enabled and available
            if (fileLoggingEnabled && fileWriter != null) {
                add(KermitFileLogWriter(fileWriter!!))
            }
        }
    }

    fun getMinSeverity(): Severity = minSeverity

    companion object {
        @Volatile
        private var instance: LoggerConfig? = null

        fun getInstance(): LoggerConfig {
            return instance ?: synchronized(this) {
                instance ?: LoggerConfig().also { instance = it }
            }
        }
    }
}
```

---

#### Integration with Koin

**Update `AppModule.kt`:**

```kotlin
// composeApp/src/commonMain/kotlin/de/aarondietz/lehrerlog/AppModule.kt

val appModule = module {

    // Provide LogFileWriter as singleton
    single {
        LogFileWriter().apply {
            initialize(maxFileSizeMB = 2, maxFiles = 5)
        }
    }

    // Initialize global logger configuration on app start
    single {
        LoggerConfig.getInstance().apply {
            initialize(
                fileWriter = get(),
                minSeverity = if (isDebugBuild()) Severity.Debug else Severity.Info,
                enableFileLogging = true
            )
        }
    }

    // ViewModels and other components don't need logger injection anymore
    // They use: private val logger by lazy { logger() }
}

// Helper function to detect debug build
expect fun isDebugBuild(): Boolean
```

**Platform-specific debug detection:**

```kotlin
// androidMain
actual fun isDebugBuild(): Boolean = BuildConfig.DEBUG

// iosMain
actual fun isDebugBuild(): Boolean {
    #if DEBUG
    return true
    #else
    return false
    #endif
}

// jvmMain
actual fun isDebugBuild(): Boolean {
    return System.getProperty("env", "production") == "development"
}

// wasmJsMain
actual fun isDebugBuild(): Boolean {
    return js("process.env.NODE_ENV === 'development'") as? Boolean ?: false
}
```

---

#### Usage Examples

**In ViewModels:**

```kotlin
class StudentsViewModel(
    private val repository: StudentRepository,
    private val schoolId: StateFlow<Long?>
) : ViewModel() {

    // Automatic tag detection - tag will be "StudentsViewModel"
    private val logger by lazy { logger() }

    init {
        logger.d { "StudentsViewModel initialized" }
    }

    val students: StateFlow<List<StudentDto>> = schoolId
        .filterNotNull()
        .flatMapLatest { id ->
            logger.i { "Loading students for school $id" }
            repository.getStudentsFlow(id)
        }
        .onEach { students ->
            logger.d { "Loaded ${students.size} students" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

**Output:**

```
2026-02-01T14:32:15.123Z [DEBUG] [StudentsViewModel] StudentsViewModel initialized
2026-02-01T14:32:16.456Z [INFO] [StudentsViewModel] Loading students for school 123
2026-02-01T14:32:17.789Z [DEBUG] [StudentsViewModel] Loaded 45 students
```

---

**In Repositories:**

```kotlin
class SchoolClassRepository(
    private val apiClient: ApiClient
) {
    private val logger by lazy { logger() }

    suspend fun createSchoolClass(dto: CreateSchoolClassDto): Result<SchoolClassDto> {
        logger.i { "Creating school class: ${dto.name}" }

        return try {
            val result = apiClient.post("/school-classes", dto)
            logger.d { "School class created successfully: ${result.id}" }
            Result.success(result)
        } catch (e: Exception) {
            logger.e(e) { "Failed to create school class: ${dto.name}" }
            Result.failure(e)
        }
    }
}
```

**Output:**

```
2026-02-01T14:35:10.123Z [INFO] [SchoolClassRepository] Creating school class: Math 101
2026-02-01T14:35:11.456Z [DEBUG] [SchoolClassRepository] School class created successfully: 789
```

---

**Custom Tags (when needed):**

```kotlin
class NetworkClient {
    // Use custom tag for specific logging context
    private val logger by lazy { logger("HTTP") }

    suspend fun get(url: String): String {
        logger.d { "GET $url" }
        // ...
    }
}
```

**Output:**

```
2026-02-01T14:40:00.123Z [DEBUG] [HTTP] GET https://api.lehrerlog.de/students
```

---

#### Migration Strategy

**Phase 1: Add Extension Function (No Breaking Changes)**

1. Create `LoggerExt.kt` and `LoggerConfig.kt`
2. Update `AppModule.kt` to initialize `LoggerConfig`
3. Keep existing code working (backward compatible)

**Phase 2: Gradual Migration**

Migrate files one by one from:

```kotlin
// OLD: Global logger injection
class StudentsViewModel(
    private val repository: StudentRepository,
    private val logger: Logger  // ❌ Remove this
) : ViewModel()
```

To:

```kotlin
// NEW: Extension function
class StudentsViewModel(
    private val repository: StudentRepository
) : ViewModel() {
    private val logger by lazy { logger() }  // ✅ Auto-tagged
}
```

**Files to Migrate (from earlier analysis):**

- `SchoolClassRepository.kt`
- `LatePeriodManagementViewModel.kt`
- `ParentInviteManagementViewModel.kt`
- `SettingsViewModel.kt`
- `StudentsViewModel.kt`
- `TasksViewModel.kt`

**Phase 3: Remove Old Logger from Koin (Optional)**

After all files migrated, remove from `AppModule.kt`:

```kotlin
// Remove this (no longer needed):
single {
    Logger.withTag("LehrerLog")
}
```

---

#### Testing Strategy

**Unit Tests** (`composeApp/src/commonTest/kotlin/de/aarondietz/lehrerlog/logging/LoggerExtTest.kt`):

```kotlin
class LoggerExtTest {

    @BeforeTest
    fun setup() {
        // Initialize with test configuration
        LoggerConfig.getInstance().initialize(
            fileWriter = FakeLogFileWriter(),
            minSeverity = Severity.Debug,
            enableFileLogging = true
        )
    }

    @Test
    fun `logger extension generates correct tag from class name`() {
        class TestClass {
            val logger = logger()
        }

        val testInstance = TestClass()
        // Verify tag is "TestClass"
        // (Would need to expose tag property or verify through log output)
    }

    @Test
    fun `custom tag logger uses provided tag`() {
        class TestClass {
            val logger = logger("CustomTag")
        }

        val testInstance = TestClass()
        // Verify tag is "CustomTag"
    }

    @Test
    fun `logger respects global severity configuration`() {
        LoggerConfig.getInstance().initialize(
            fileWriter = FakeLogFileWriter(),
            minSeverity = Severity.Warn,  // Only WARN and ERROR
            enableFileLogging = false
        )

        class TestClass {
            val logger = logger()
        }

        val testInstance = TestClass()
        testInstance.logger.d { "Debug message" }  // Should not appear
        testInstance.logger.w { "Warning message" }  // Should appear

        // Verify only WARN and above are logged
    }
}

class FakeLogFileWriter : LogFileWriter {
    val logs = mutableListOf<String>()

    override fun initialize(maxFileSizeMB: Int, maxFiles: Int) {}

    override fun writeLog(
        timestamp: String,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        logs.add("[$level] [$tag] $message")
    }

    override fun getCurrentLogSize(): Long = 0
    override fun rotateNow() {}
    override fun getLogFiles(): List<String> = emptyList()
    override fun clearLogs() {
        logs.clear()
    }
}
```

**Integration Tests:**

```kotlin
@Test
fun `ViewModels use correct class-specific tags`() {
    val viewModel = StudentsViewModel(mockRepository, flowOf(123L))

    // Trigger some logging
    viewModel.loadStudents()

    // Verify logs contain [StudentsViewModel] tag
    val logs = getLogOutput()
    assertTrue(logs.any { it.contains("[StudentsViewModel]") })
}
```

---

#### Benefits Summary

| Benefit                 | Description                                                         |
|-------------------------|---------------------------------------------------------------------|
| **Automatic Tagging**   | No manual tag management, class name detected automatically         |
| **Clean Code**          | One-liner to add logging: `private val logger by lazy { logger() }` |
| **No Injection Needed** | Removes logger from constructor parameters                          |
| **Consistent Config**   | All loggers share same configuration (severity, writers)            |
| **Easy Debugging**      | Immediately see which class logged each message                     |
| **Migration Path**      | Can coexist with old approach, gradual migration                    |
| **Testable**            | Easy to verify logging behavior in tests                            |
| **Performance**         | Lazy initialization - logger created only when first used           |

---

#### Performance Considerations

**Lazy Initialization:**

```kotlin
private val logger by lazy { logger() }
```

- Logger created **only when first used**
- Minimal overhead if class never logs
- Shared configuration (LoggerConfig) is singleton

**Memory:**

- Each class has its own Logger instance
- Negligible memory impact (~few KB per logger)
- Worth it for improved debugging

**Benchmarks (estimated):**

- Logger creation: < 1ms
- Log call overhead: < 0.1ms (same as before)
- File write: ~1-5ms (async, doesn't block)

---

#### Edge Cases & Solutions

**Problem:** Anonymous classes have generic names like `MyViewModel$1`

**Solution:** Allow optional manual tag override:

```kotlin
private val logger by lazy { logger("MyViewModel-Callback") }
```

---

**Problem:** Companion objects

**Solution:** Extension works on companion objects:

```kotlin
companion object {
    private val logger by lazy { logger() }  // Tag: "MyClass.Companion"

    fun staticMethod() {
        logger.i { "Static method called" }
    }
}
```

Or use custom tag:

```kotlin
companion object {
    private val logger by lazy { logger("MyClass") }
}
```

---

**Problem:** Multiple loggers in same class (different contexts)

**Solution:** Use custom tags:

```kotlin
class NetworkService {
    private val httpLogger by lazy { logger("HTTP") }
    private val wsLogger by lazy { logger("WebSocket") }

    fun httpCall() {
        httpLogger.d { "HTTP request" }
    }

    fun wsConnect() {
        wsLogger.d { "WebSocket connecting" }
    }
}
```

---

#### Documentation Requirements

**Add to `AGENTS.md`:**

```markdown
## Logging

### Adding Logging to a Class

Use the logger extension function for automatic class-name tagging:

```kotlin
class MyViewModel : ViewModel() {
    private val logger by lazy { logger() }

    fun doSomething() {
        logger.i { "Doing something" }
    }
}
```

### Log Levels

- `logger.d { }` - Debug (development only)
- `logger.i { }` - Info (general information)
- `logger.w { }` - Warning (potential issues)
- `logger.e(exception) { }` - Error (failures)

### Custom Tags

For specific contexts, use custom tags:

```kotlin
private val logger by lazy { logger("CustomTag") }
```

### What NOT to Log

- ❌ Passwords, tokens, or authentication data
- ❌ Personal identifiable information (PII)
- ❌ Full API responses with user data
- ✅ User IDs (hashed/anonymized), error messages, performance metrics

```

---

### Implementation Steps

**Updated Implementation Timeline:**

#### Phase 1: Core Infrastructure (1 day)
- [ ] Create `LogFileWriter` expect/actual interface in `commonMain`
- [ ] Implement JVM version first (easiest, follows TokenStorage pattern)
- [ ] Create `KermitFileLogWriter` wrapper class
- [ ] Add unit tests for JVM implementation

#### Phase 2: Platform Implementations (2 days)
- [ ] Implement Android version with cache directory
- [ ] Implement iOS version with Application Support directory
- [ ] Implement Wasm version with OPFS (Origin Private File System)
- [ ] Add OPFS browser compatibility detection and fallback
- [ ] Test file creation and rotation on each platform

#### Phase 3: Class-Specific Logger Extension (0.5 days) **← NEW**
- [ ] Create `LoggerExt.kt` with automatic tag detection
- [ ] Create `LoggerConfig.kt` for centralized configuration
- [ ] Add platform-specific `isDebugBuild()` functions
- [ ] Update `AppModule.kt` to initialize `LoggerConfig`
- [ ] Write unit tests for logger extension
- [ ] Update `AGENTS.md` with logging guidelines

#### Phase 4: Kermit Integration & Migration (1 day) **← UPDATED**
- [ ] Verify extension function works with file logging
- [ ] Migrate existing files to use logger extension (6 files)
- [ ] Remove old global logger from Koin (optional)
- [ ] Test log output on all platforms with class-specific tags
- [ ] Verify environment-based severity filtering works

#### Phase 5: UI for Log Management (1 day)
- [ ] Add Settings screen section to view/export logs
- [ ] Add "Clear Logs" button in settings
- [ ] Add "Share Logs" functionality (for support tickets)
- [ ] Show current log file size and count
- [ ] Display logs with class-specific tags in UI

#### Phase 6: Testing & Documentation (0.5 days)
- [ ] Write comprehensive unit and integration tests
- [ ] Test with different severity levels (Debug, Info, Warn, Error)
- [ ] Update `AGENTS.md` with logging guidelines
- [ ] Document log file locations per platform in README
- [ ] Add troubleshooting guide for common issues
- [ ] Add OPFS browser compatibility documentation

**Total Estimated Effort:** 5-6 days (updated for class-specific loggers)

---

#### Phase 1: Core Infrastructure (1 day)
- [ ] Create `LogFileWriter` expect/actual interface in `commonMain`
- [ ] Implement JVM version first (easiest, follows TokenStorage pattern)
- [ ] Create `KermitFileLogWriter` wrapper class
- [ ] Add unit tests for JVM implementation

#### Phase 2: Platform Implementations (2 days)
- [ ] Implement Android version with cache directory
- [ ] Implement iOS version with Application Support directory
- [ ] Implement Wasm version with OPFS (Origin Private File System)
- [ ] Add OPFS browser compatibility detection and fallback
- [ ] Test file creation and rotation on each platform

---

### Testing Requirements

**Unit Tests** (`composeApp/src/commonTest/kotlin/de/aarondietz/lehrerlog/logging/LogFileWriterTest.kt`):

```kotlin
class LogFileWriterTest {
    private lateinit var logWriter: LogFileWriter

    @BeforeTest
    fun setup() {
        logWriter = LogFileWriter()
        logWriter.initialize(maxFileSizeMB = 1, maxFiles = 3)
    }

    @AfterTest
    fun cleanup() {
        logWriter.clearLogs()
    }

    @Test
    fun `writeLog creates log file`() {
        logWriter.writeLog("2026-02-01T12:00:00Z", "INFO", "Test", "Message", null)
        assertTrue(logWriter.getLogFiles().isNotEmpty())
    }

    @Test
    fun `log rotation occurs at size threshold`() {
        // Write enough logs to trigger rotation
        repeat(50000) { i ->
            logWriter.writeLog(
                "2026-02-01T12:00:00Z", "INFO", "Test",
                "Test message $i with content to trigger rotation", null
            )
        }
        assertTrue(logWriter.getLogFiles().size > 1)
    }

    @Test
    fun `clearLogs removes all files`() {
        logWriter.writeLog("2026-02-01T12:00:00Z", "INFO", "Test", "Message", null)
        assertTrue(logWriter.getLogFiles().isNotEmpty())
        logWriter.clearLogs()
        assertTrue(logWriter.getLogFiles().isEmpty())
    }
}
```

**Integration Tests:**

- Test Kermit integration with custom LogWriter
- Verify logs appear in both console and file
- Test concurrent writes from multiple coroutines
- Verify log format consistency across platforms

---

### Benefits

1. **Production Debugging:** Diagnose issues on user devices without console access
2. **Support Tickets:** Users can share log files with support team
3. **Crash Analysis:** Preserve logs leading up to crashes
4. **Performance Monitoring:** Track operation timings in production
5. **Audit Trail:** Record user actions for compliance (if needed)
6. **Automated Testing:** Verify log output in integration tests

---

### Security Considerations

**DO NOT LOG:**

- ❌ Passwords or authentication tokens (JWT, refresh tokens)
- ❌ Personal identifiable information (PII) unless hashed
- ❌ Credit card numbers or payment data
- ❌ Full API responses containing user data
- ❌ Database credentials or API keys

**SAFE TO LOG:**

- ✅ User IDs (hashed or anonymized)
- ✅ API endpoints called (without sensitive query params)
- ✅ Error messages (sanitized)
- ✅ Performance metrics and timings
- ✅ Feature usage analytics

**Log Sanitization Example:**

```kotlin
logger.i { "API call: ${apiUrl.sanitizeForLogging()}" }

fun String.sanitizeForLogging(): String {
    return this.replace(Regex("token=[^&]+"), "token=***")
        .replace(Regex("password=[^&]+"), "password=***")
        .replace(Regex("email=[^&]+"), "email=***")
}
```

---

### Risks & Mitigations

| Risk                   | Probability | Impact | Mitigation                                             |
|------------------------|-------------|--------|--------------------------------------------------------|
| Storage space consumed | Medium      | Low    | Automatic rotation (2MB) and cleanup (7 days, 5 files) |
| Performance impact     | Low         | Medium | Write logs asynchronously using coroutines             |
| Privacy concerns       | Medium      | High   | Sanitize log messages, document what NOT to log        |
| Platform differences   | Low         | Medium | Use expect/actual pattern, comprehensive testing       |
| Log corruption         | Low         | Low    | Synchronize writes on concurrent platforms             |
| App size increase      | Low         | Low    | Minimal (~500 lines total across platforms)            |

---

### Dependencies

**No new dependencies required!**

- ✅ Kermit 2.0.8 (already integrated)
- ✅ kotlinx-datetime (already in project for timestamps)
- ✅ kotlin-stdlib (for File I/O on JVM/Android)
- ✅ Foundation framework (iOS, built-in with Kotlin/Native)

---

### Success Criteria

- [ ] Logs written to files on Android, iOS, and Desktop
- [ ] Automatic rotation at 2MB threshold working correctly
- [ ] Old logs cleaned up (7 days or 5 files limit enforced)
- [ ] No performance degradation (async writes, < 5ms overhead)
- [ ] Settings UI to view/export/clear logs implemented
- [ ] All tests pass on all platforms (100% coverage for LogFileWriter)
- [ ] Documentation updated in AGENTS.md and README

---

### Future Enhancements

1. **Remote Log Upload:** Send logs to server for centralized monitoring (Sentry/LogRocket)
2. **Log Encryption:** Encrypt log files at rest using platform keystore
3. **Structured Logging:** Use JSON format for easier parsing and analysis
4. **Log Filtering UI:** Filter logs by level, tag, time range in settings
5. **Performance Metrics:** Automatic tracking of function execution times
6. **Crash Reporting Integration:** Attach logs to crash reports automatically

---

*This enhancement will significantly improve production support capabilities and debugging experience across all
platforms.*

---

*End of Report*
