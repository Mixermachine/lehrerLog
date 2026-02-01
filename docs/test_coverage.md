# Test Coverage Guide

This document explains how to generate and use test coverage reports for the LehrerLog project.

## Overview

The project uses different coverage tools for different modules:
- **Server (JVM)**: JaCoCo - industry-standard Java/Kotlin coverage tool
- **Client (Multiplatform)**: Kover - JetBrains' Kotlin-native coverage tool

## Quick Start

### Generate All Coverage Reports

```bash
# Server coverage
./gradlew :server:test :server:jacocoTestReport

# Client coverage
./gradlew :composeApp:koverHtmlReport

# Or run both
./gradlew :server:jacocoTestReport :composeApp:koverHtmlReport
```

### View Reports

After generation, open these files in your browser:

- **Server**: `server/build/reports/jacoco/html/index.html`
- **Client**: `composeApp/build/reports/kover/html/index.html`

## Server Coverage (JaCoCo)

### Available Tasks

| Task | Description |
|------|-------------|
| `:server:test` | Run all tests |
| `:server:jacocoTestReport` | Generate HTML and XML coverage reports |
| `:server:jacocoTestCoverageVerification` | Verify coverage meets thresholds |
| `:server:checkCoverage` | Run tests + verification (recommended) |

### Usage Examples

```bash
# Run tests and generate report
./gradlew :server:test :server:jacocoTestReport

# Run tests and verify coverage thresholds
./gradlew :server:checkCoverage

# Generate XML report for CI (Codecov, SonarQube, etc.)
./gradlew :server:jacocoTestReport
# Report location: server/build/reports/jacoco/jacoco.xml
```

### Coverage Thresholds

- **Overall minimum**: 60% line coverage
- **Per-class minimum**: 50% line coverage

### Excluded from Coverage

- Generated code (`ServerConfig`)
- Data classes and DTOs (`de.aarondietz.lehrerlog.data.**`)
- Database table definitions (`de.aarondietz.lehrerlog.db.tables.**`)
- Main application entry point (`ApplicationKt`)

### CI Integration

JaCoCo XML reports work with popular coverage services:

**GitHub Actions + Codecov:**
```yaml
- name: Generate coverage report
  run: ./gradlew :server:jacocoTestReport

- name: Upload coverage to Codecov
  uses: codecov/codecov-action@v3
  with:
    files: ./server/build/reports/jacoco/jacoco.xml
    flags: server
```

**SonarQube:**
```bash
./gradlew sonar \
  -Dsonar.coverage.jacoco.xmlReportPaths=server/build/reports/jacoco/jacoco.xml
```

## Client Coverage (Kover)

### Available Tasks

| Task | Description |
|------|-------------|
| `:composeApp:allTests` | Run all tests (all platforms) |
| `:composeApp:koverHtmlReport` | Generate HTML coverage report |
| `:composeApp:koverXmlReport` | Generate XML coverage report |
| `:composeApp:koverVerify` | Verify coverage meets thresholds |
| `:composeApp:checkCoverage` | Run all coverage tasks |

### Usage Examples

```bash
# Run Android unit tests and generate coverage
./gradlew :composeApp:testDebugUnitTest :composeApp:koverHtmlReport

# Run all tests (all platforms) and generate coverage
./gradlew :composeApp:allTests :composeApp:koverHtmlReport

# Verify coverage thresholds
./gradlew :composeApp:koverVerify

# Complete coverage workflow
./gradlew :composeApp:checkCoverage
```

### Platform-Specific Tests

Kover aggregates coverage from all test variants:

```bash
# Android unit tests (Robolectric)
./gradlew :composeApp:testDebugUnitTest

# JVM desktop tests
./gradlew :composeApp:jvmTest

# Wasm tests
./gradlew :composeApp:wasmJsTest

# All tests together
./gradlew :composeApp:allTests
```

### Coverage Thresholds

- **Overall minimum**: 60% line coverage
- **Per-class minimum**: 50% line coverage

### Excluded from Coverage

- Generated code (`ServerConfig`, build-generated classes)
- Platform entry points (`MainKt`, `App*`)
- Data classes and DTOs (`de.aarondietz.lehrerlog.data.**`)
- Dependency injection modules (`*Module*Kt`)
- Compose preview functions (`@Preview` annotated)

### CI Integration

**GitHub Actions + Codecov:**
```yaml
- name: Run tests and generate coverage
  run: ./gradlew :composeApp:allTests :composeApp:koverXmlReport

- name: Upload coverage to Codecov
  uses: codecov/codecov-action@v3
  with:
    files: ./composeApp/build/reports/kover/report.xml
    flags: client
```

## Combined Coverage Report

To generate coverage for both modules:

```bash
# Full project test + coverage
./gradlew test allTests jacocoTestReport koverHtmlReport
```

## Best Practices

### 1. Run Coverage Locally Before Pushing

```bash
# Check both modules meet thresholds
./gradlew :server:checkCoverage :composeApp:checkCoverage
```

### 2. Focus on Critical Paths

Prioritize test coverage for:
- Service layer (business logic)
- API routes (server endpoints)
- ViewModels (client state management)
- Data validation and transformation

### 3. Review Coverage Reports

Don't just chase percentages. Review HTML reports to find:
- Uncovered branches (if/else, when expressions)
- Untested error handling paths
- Missing edge case tests

### 4. Exclude Appropriately

Coverage tools exclude:
- Boilerplate code (getters, setters)
- Framework code (Koin modules)
- Generated code

This keeps metrics focused on your logic.

### 5. Use Coverage in PR Reviews

Add coverage checks to your CI pipeline:

```yaml
- name: Verify coverage thresholds
  run: |
    ./gradlew :server:jacocoTestCoverageVerification
    ./gradlew :composeApp:koverVerify
```

## Troubleshooting

### "No tests found"

Ensure tests are in the correct source sets:
- Server: `server/src/test/kotlin/`
- Client Android: `composeApp/src/androidUnitTest/kotlin/`
- Client Common: `composeApp/src/commonTest/kotlin/`

### Coverage report is empty

1. Tests must execute before report generation
2. Check test output for failures
3. Ensure source sets are configured correctly

### Thresholds too strict/lenient

Edit thresholds in `build.gradle.kts`:

**Server (JaCoCo):**
```kotlin
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.60".toBigDecimal() // Change this
            }
        }
    }
}
```

**Client (Kover):**
```kotlin
kover {
    reports {
        total {
            verify {
                rule {
                    minBound(60) // Change this
                }
            }
        }
    }
}
```

### Exclude specific packages

**Server (JaCoCo):**
```kotlin
classDirectories.setFrom(
    files(classDirectories.files.map {
        fileTree(it) {
            exclude("**/your/package/**")
        }
    })
)
```

**Client (Kover):**
```kotlin
kover {
    reports {
        filters {
            excludes {
                packages("your.package")
            }
        }
    }
}
```

## Integration with IDEs

### IntelliJ IDEA

1. Run tests with coverage:
   - Right-click test class/method → "Run with Coverage"
   - Or use keyboard shortcut: `Ctrl+Shift+F10` (Windows/Linux) or `⌃⇧R` (Mac)

2. View coverage in editor:
   - Green bars = covered lines
   - Red bars = uncovered lines
   - Yellow bars = partially covered branches

3. Generate coverage report:
   - Run → "Generate Coverage Report"
   - Choose HTML or XML format

### Android Studio

Same as IntelliJ IDEA. Additional features:
- Coverage data shows in Android Test tab
- Can view coverage per module

## Advanced: Multi-Module Coverage

To combine server and client coverage (requires custom task):

```kotlin
// In root build.gradle.kts
tasks.register("combinedCoverageReport") {
    group = "verification"
    description = "Generate combined coverage report"
    dependsOn(
        ":server:jacocoTestReport",
        ":composeApp:koverHtmlReport"
    )
}
```

## References

- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [Kover Documentation](https://kotlin.github.io/kotlinx-kover/)
- [Roborazzi Testing](https://github.com/takahirom/roborazzi)
- Project test strategy: `docs/planing_tech_plan.md#phase-4-testing-strategy`
