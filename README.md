This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM), Server.

## Key Features (Current)
- Task creation and submissions (including in-person).
- Late policy tracking + punishment resolution.
- Parent invites and read-only parent views.
- Storage quota enforcement + file download audit logging.


* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
      folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

* [/shared](./shared/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./shared/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Environment Flavors

The client uses a build-time server URL. DEV defaults to localhost; QA and prod are built in CI.

- DEV (local): uses `http://localhost:8080`
  - Override with `-PserverUrl=http://localhost:8080`
  - Example:
    - macOS/Linux:
      ```shell
      ./gradlew :composeApp:assembleDebug -PserverUrl=http://localhost:8080
      ```
    - Windows:
      ```shell
      .\gradlew.bat :composeApp:assembleDebug -PserverUrl=http://localhost:8080
      ```
- QA: CI builds with `https://api.qa.lehrerlog.de`
- PROD: CI builds with `https://api.lehrerlog.de`

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### Build and Run Server

To build and run the development version of the server, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:

- on macOS/Linux
  ```shell
  ./gradlew :server:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :server:run
  ```

### Build and Run Web Application

To build and run the development version of the web app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:

- for the Wasm target (faster, modern browsers):
    - on macOS/Linux
      ```shell
      ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
      ```
    - on Windows
      ```shell
      .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
      ```
- for the JS target (slower, supports older browsers):
    - on macOS/Linux
      ```shell
      ./gradlew :composeApp:jsBrowserDevelopmentRun
      ```
    - on Windows
      ```shell
      .\gradlew.bat :composeApp:jsBrowserDevelopmentRun
      ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE's toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

## Testing & Coverage

### Run Tests

**Server tests:**
```shell
./gradlew :server:test
```

**Client tests (Android unit tests with Robolectric):**
```shell
./gradlew :composeApp:testDevDebugUnitTest
```

**All tests:**
```shell
./gradlew test allTests
```

### Coverage Reports

Generate test coverage reports with JaCoCo (server) and Kover (client):

**Server coverage:**
```shell
./gradlew :server:test :server:jacocoTestReport
# View: server/build/reports/jacoco/html/index.html
```

**Client coverage:**
```shell
./gradlew :composeApp:testDevDebugUnitTest :composeApp:koverHtmlReport
# View: composeApp/build/reports/kover/html/index.html
```

**Verify coverage thresholds (60% overall, 50% per class):**
```shell
./gradlew :server:checkCoverage :composeApp:checkCoverage
```

📚 See [COVERAGE_QUICK_START.md](COVERAGE_QUICK_START.md) for quick reference or [docs/test_coverage.md](docs/test_coverage.md) for complete guide.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack
channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
