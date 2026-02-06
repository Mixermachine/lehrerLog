# Compose UI Interaction Coverage Plan

## Goal
Add step-by-step Compose UI interaction tests to cover core user features that are currently only snapshot-tested.

## Baseline Findings
- Android Compose UI tests are currently snapshot-only (`Roborazzi`) in `composeApp/src/androidUnitTest/...`.
- There are no interaction assertions (`performClick`, `performTextInput`, navigation/flow assertions) in Android UI tests.
- ViewModel/unit tests in `commonTest` exist, but they do not validate user interaction wiring at the composable layer.

## Stage 1: Auth + Parent Invite Interaction Flows
- Add interaction tests for:
  - `LoginScreen` (enter credentials + login click callback path)
  - `RegisterScreen` (field entry + register click path)
  - `ParentInviteRedeemContent` (field input + redeem + back action)
- Validation command:
  - `./gradlew :composeApp:testDevDebugUnitTest --tests "*AuthInteractionTest" --tests "*ParentInviteRedeemInteractionTest"`

## Stage 2: Settings + Home Interaction Flows
- Add interaction tests for:
  - `SettingsContent` actions (`refresh quota`, `refresh logs`, `share logs`, `clear logs`, `logout`)
  - `HomeLateStatsContent` resolve punishment action
- Validation command:
  - `./gradlew :composeApp:testDevDebugUnitTest --tests "*SettingsInteractionTest" --tests "*HomeInteractionTest"`

## Stage 3: Students Interaction Flows
- Add interaction tests for:
  - `ClassCard` expansion and student actions (`invite parent`, `view links`, `delete student`)
  - Class-level actions (`add student`, `delete class`)
- Validation command:
  - `./gradlew :composeApp:testDevDebugUnitTest --tests "*StudentsInteractionTest"`

## Stage 4: Parent Role Interaction Flows
- Add interaction tests for:
  - `ParentStudentsScreenContent` student selection
  - `ParentAssignmentsScreenContent` list rendering after student selection
  - `ParentSubmissionsScreenContent` list rendering for graded/note entries
- Validation command:
  - `./gradlew :composeApp:testDevDebugUnitTest --tests "*ParentFlowInteractionTest"`

## Stage 5: Tasks Interaction Flows
- Add interaction tests for:
  - `TasksScreen` class selection and task-card open flow
  - Add-task dialog visibility from FAB and dialog dismissal path
- Validation command:
  - `./gradlew :composeApp:testDevDebugUnitTest --tests "*TasksInteractionTest"`

## Stage 6: Full Verification and Gap Report
- Run full client unit/UI suite:
  - `./gradlew :composeApp:testDevDebugUnitTest`
- Run client coverage report:
  - `./gradlew :composeApp:koverHtmlReport :composeApp:checkCoverage`
- Document remaining uncovered UI behaviors (if any) and recommended follow-up tests.

## Notes
- Use deterministic test behavior (`mainClock.autoAdvance = false`, no sleeps/delays).
- Use `SharedTestFixtures` values for test data (no magic mock strings).
- Prefer callback verification for interaction tests and keep network-dependent tests minimal.
