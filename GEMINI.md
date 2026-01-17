# Project Overview

This is a Kotlin Multiplatform project for an application called "LehrerLog". It's designed to be a tool for teachers, with features for managing tasks, students, and potentially more. The project is structured into three main modules:

*   `composeApp`: A Compose Multiplatform application for Android, iOS, Web, and Desktop. This contains the user interface and frontend logic.
*   `server`: A Ktor-based backend server that provides a REST API for the client applications. It handles authentication, data storage, and synchronization.
*   `shared`: A module for code that is shared between the `composeApp` and `server` modules. This likely contains data models and other common logic.

## Technologies Used

*   **Frontend:**
    *   [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
    *   [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) for the UI.
    *   [Koin](https://insert-koin.io/) for dependency injection.
    *   [Compose Navigation](https://developer.android.com/jetpack/compose/navigation) for screen navigation.
*   **Backend:**
    *   [Ktor](https://ktor.io/) for the server framework.
    *   [JWT](https://jwt.io/) for authentication.
    *   [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) for JSON processing.
    *   A relational database (the specific type is not immediately clear, but it's accessed through a `DatabaseFactory`).
*   **Build System:**
    *   [Gradle](https://gradle.org/) with the Kotlin DSL.

# Building and Running

The following commands can be used to build and run the various parts of the application. These are executed from the root directory of the project.

## Android Application

To build and run the development version of the Android app:

```shell
.\gradlew.bat :composeApp:assembleDebug
```

## Desktop (JVM) Application

To build and run the development version of the desktop app:

```shell
.\gradlew.bat :composeApp:run
```

## Server

To build and run the development version of the server:

```shell
.\gradlew.bat :server:run
```

## Web Application

To build and run the development version of the web app (using Wasm):

```shell
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

## iOS Application

To build and run the development version of the iOS app, open the `/iosApp` directory in Xcode and run it from there.

# Development Conventions

*   **Dependency Injection:** The project uses Koin for dependency injection. ViewModels are injected into Composable functions using `koinViewModel()`. Other dependencies are injected using `koinInject()`.
*   **Authentication:** Authentication is handled using JWT. The client authenticates with the server, and the server provides a JWT to the client. This token is then used to authenticate subsequent requests.
*   **Data Synchronization:** The app has a `SyncManager` that is responsible for synchronizing data between the client and the server.
*   **UI:** The UI is built using Compose Multiplatform. The app uses a bottom navigation bar for top-level navigation.
*   **API:** The server provides a REST API with routes for authentication, and managing schools, classes, students, and users.
