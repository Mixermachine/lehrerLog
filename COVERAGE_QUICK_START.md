# Coverage Quick Start 🎯

Quick reference for generating and checking test coverage.

## 📊 Generate Coverage Reports

### Server (JaCoCo)
```bash
./gradlew :server:test :server:jacocoTestReport
# View: server/build/reports/jacoco/html/index.html
```

### Client (Kover)
```bash
./gradlew :composeApp:testDevDebugUnitTest :composeApp:koverHtmlReport
# View: composeApp/build/reports/kover/html/index.html
```

### Both Modules
```bash
./gradlew test allTests jacocoTestReport koverHtmlReport
```

## ✅ Verify Coverage Thresholds

### Server
```bash
./gradlew :server:checkCoverage
```
- Minimum: 60% overall, 50% per class

### Client
```bash
./gradlew :composeApp:checkCoverage
```
- Minimum: 60% overall, 50% per class

## 🚀 Before Committing

```bash
# Run this to ensure your code meets coverage standards
./gradlew :server:checkCoverage :composeApp:checkCoverage
```

## 📁 Report Locations

| Module | HTML Report | XML Report (CI) |
|--------|-------------|-----------------|
| Server | `server/build/reports/jacoco/html/` | `server/build/reports/jacoco/jacoco.xml` |
| Client | `composeApp/build/reports/kover/html/` | `composeApp/build/reports/kover/report.xml` |

## 🎨 IDE Integration

### IntelliJ IDEA / Android Studio
1. Right-click test → "Run with Coverage"
2. View coverage inline in editor (green/red bars)
3. Generate coverage report: Run → "Generate Coverage Report"

## 📚 Full Documentation

See [docs/test_coverage.md](docs/test_coverage.md) for complete guide.

## 🐛 Troubleshooting

**Coverage report is empty?**
- Make sure tests pass: `./gradlew :server:test`
- Check you're in the right directory

**Threshold verification fails?**
- View HTML report to see what's missing
- Add tests for uncovered code
- Or adjust thresholds in `build.gradle.kts`

**Can't find report?**
- Server: Look in `server/build/reports/jacoco/html/index.html`
- Client: Look in `composeApp/build/reports/kover/html/index.html`
