# Task Flow Coverage Matrix

Updated: 2026-02-08

| Workflow requirement | Client UI / VM tests | Server E2E tests | CI gate |
|---|---|---|---|
| Account creation | `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopTeacherWorkflowUiTest.kt` + `composeApp/src/androidUnitTest/kotlin/de/aarondietz/lehrerlog/ui/screens/auth/AuthInteractionTest.kt` | (covered by auth endpoints in existing server suite) | `:composeApp:jvmTest`, `:composeApp:testDevDebugUnitTest` |
| Create class | `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopTeacherWorkflowUiTest.kt` + `composeApp/src/androidUnitTest/kotlin/de/aarondietz/lehrerlog/ui/screens/students/StudentsInteractionTest.kt` | class route coverage in server suite | `:composeApp:jvmTest`, `:composeApp:testDevDebugUnitTest`, `:server:test` |
| Add pupils | `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopTeacherWorkflowUiTest.kt` + `composeApp/src/androidUnitTest/kotlin/de/aarondietz/lehrerlog/ui/screens/students/StudentsInteractionTest.kt` | student route coverage in server suite | `:composeApp:jvmTest`, `:composeApp:testDevDebugUnitTest`, `:server:test` |
| Delete pupils | `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopTeacherWorkflowUiTest.kt` + `composeApp/src/androidUnitTest/kotlin/de/aarondietz/lehrerlog/ui/screens/students/StudentsInteractionTest.kt` | student route coverage in server suite | `:composeApp:jvmTest`, `:composeApp:testDevDebugUnitTest`, `:server:test` |
| Create task without file | `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopTeacherWorkflowUiTest.kt` + `composeApp/src/commonTest/kotlin/de/aarondietz/lehrerlog/ui/screens/tasks/TasksViewModelTest.kt` | `server/src/test/kotlin/de/aarondietz/lehrerlog/TaskRouteEndToEndTest.kt` | `:composeApp:jvmTest`, `:composeApp:testDevDebugUnitTest`, `:server:test` |
| Create task with file | `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopTeacherWorkflowUiTest.kt` + `composeApp/src/commonTest/kotlin/de/aarondietz/lehrerlog/ui/screens/tasks/TasksViewModelTest.kt` | `server/src/test/kotlin/de/aarondietz/lehrerlog/FileRouteEndToEndTest.kt` | `:composeApp:jvmTest`, `:composeApp:testDevDebugUnitTest`, `:server:test` |
| Verify task file uploaded | `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopTeacherWorkflowUiTest.kt` + `composeApp/src/commonTest/kotlin/de/aarondietz/lehrerlog/ui/screens/tasks/TasksViewModelTest.kt` | `server/src/test/kotlin/de/aarondietz/lehrerlog/FileRouteEndToEndTest.kt` (`GET /api/tasks/{id}/file`) | `:composeApp:jvmTest`, `:composeApp:testDevDebugUnitTest`, `:server:test` |
| Mark submission delivered (in-person) | `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopTeacherWorkflowUiTest.kt` + `composeApp/src/commonTest/kotlin/de/aarondietz/lehrerlog/ui/screens/tasks/TasksViewModelTest.kt` | `server/src/test/kotlin/de/aarondietz/lehrerlog/TaskRouteEndToEndTest.kt` | `:composeApp:jvmTest`, `:composeApp:testDevDebugUnitTest`, `:server:test` |
| Non-targeted in-person blocked | `composeApp/src/commonTest/kotlin/de/aarondietz/lehrerlog/ui/screens/tasks/TasksViewModelTest.kt` | `server/src/test/kotlin/de/aarondietz/lehrerlog/TaskRouteEndToEndTest.kt` | `:composeApp:testDevDebugUnitTest`, `:server:test` |
| Desktop visible UI scaffold | `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopComposeUiScaffoldTest.kt` + `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopTeacherWorkflowUiTest.kt` | n/a | `:composeApp:jvmTest`, `:composeApp:desktopUiTestVisible` |
| Browser/Wasm smoke | `composeApp/src/wasmJsTest/kotlin/de/aarondietz/lehrerlog/ComposeWasmSmokeTest.kt` | n/a | `:composeApp:wasmJsTest` |

## Notes

- Roborazzi snapshot verification remains a separate guardrail for visual regressions:
  - `:composeApp:verifyRoborazziDevDebug`
- Coverage gates:
  - Client: `:composeApp:checkCoverage`
  - Server: `:server:checkCoverage`
