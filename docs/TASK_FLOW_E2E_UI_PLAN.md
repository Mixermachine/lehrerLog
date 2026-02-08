# Task Flow End-to-End UI Test Plan

Status: IMPLEMENTED (phase execution completed)
Owner: Codex
Updated: 2026-02-08

## Goal
Cover the critical teacher workflow, fix the silent task-file upload gap, and enforce the checks in CI.

Required workflow coverage:
1. Account creation
2. Create class
3. Add pupils to class
4. Delete pupils from class
5. Create task without file
6. Create task with file
7. Verify file is truly uploaded
8. Mark pupil submission as delivered (in-person)

## Phase Status

### Phase 1: Deterministic environment
Status: COMPLETE

Delivered:
- Deterministic fixture-backed UI/view-model tests in `composeApp/src/commonTest`.
- Server end-to-end contract tests in `server/src/test/kotlin/de/aarondietz/lehrerlog`.
- Run-level isolation strategy documented in this plan and test fixtures.

### Phase 2: Product behavior fixes
Status: COMPLETE

Delivered:
- `TasksViewModel.createTask(...)` now performs assignment-file upload in the same flow and surfaces failures via UI error state.
- Added `GET /api/tasks/{id}/targets` contract and client consumption to keep detail screen student list aligned with task targets.
- Non-targeted mark-in-person path now maps to explicit user-facing resource (`error_task_student_not_targeted`).

### Phase 3: Compose Desktop UI workflow scaffold
Status: COMPLETE

Delivered:
- Desktop Compose UI scaffold test added:
  - `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopComposeUiScaffoldTest.kt`
- Desktop teacher workflow UI test added:
  - `composeApp/src/jvmTest/kotlin/de/aarondietz/lehrerlog/ui/DesktopTeacherWorkflowUiTest.kt`
- Visible desktop test task added:
  - `:composeApp:desktopUiScaffoldTestVisible`
  - `:composeApp:desktopUiTestVisible`
  - Headless-friendly equivalent runs via `:composeApp:jvmTest` (CI-safe).

### Phase 4: Web/Wasm smoke coverage
Status: COMPLETE

Delivered:
- `:composeApp:wasmJsTest` is now part of CI preflight/build workflows.
- This catches browser-runtime regressions in the Wasm target during CI instead of only after deployment.

### Phase 5: Server integration contract coverage
Status: COMPLETE

Delivered:
- Added task-target retrieval route and tests.
- Expanded file route end-to-end tests:
  - task file metadata success path
  - invalid/missing task-file metadata paths
- Expanded submission end-to-end tests for non-targeted in-person submissions (`400`).

### Phase 6: CI gating and maintenance
Status: COMPLETE

Delivered:
- CI now executes:
  - `:composeApp:testDevDebugUnitTest`
  - `:composeApp:verifyRoborazziDevDebug`
  - `:composeApp:jvmTest`
  - `:composeApp:wasmJsTest`
- Workflow updates:
  - `.github/workflows/ci-preflight.yml`
  - `.github/workflows/composeapp-build.yml`
  - `.github/workflows/webapp-build-deploy.yml`

## Implemented Test Map

- Desktop end-to-end UI flow:
  - `DesktopTeacherWorkflowUiTest` covers account creation, class create, pupil add/delete, task create (with/without file), task-file upload verification, and mark-in-person submission.
- Android Compose interaction coverage:
  - `AuthInteractionTest`, `StudentsInteractionTest`, `TasksInteractionTest`.
- View-model integration coverage:
  - `TasksViewModelTest` (including create-with-file upload paths and non-targeted in-person error mapping).
- Server contract/end-to-end coverage:
  - `FileRouteEndToEndTest`, `TaskRouteEndToEndTest`.

## Validation Run (latest)

Executed successfully:
- `:server:test :server:checkCoverage`
- `:composeApp:testDevDebugUnitTest :composeApp:verifyRoborazziDevDebug :composeApp:jvmTest :composeApp:wasmJsTest :composeApp:checkCoverage`
- `:composeApp:desktopUiScaffoldTestVisible` and `:composeApp:desktopUiTestVisible` are available for local visible runs.

All required suites and coverage checks pass in the current workspace state.
