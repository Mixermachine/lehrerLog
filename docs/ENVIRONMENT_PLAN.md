# Environment Flavors Plan

Goal: add DEV/LOCAL, QA, and PROD flavors for client + server. CI should build QA and PROD artifacts only. DEV stays local.

## Phase 1 - Define environments and URLs
- Client: introduce three flavors (DEV, QA, PROD) with distinct base URLs.
- Server: keep one image, run with a local PostgreSQL container, and wire qa/prod hostnames and ports via compose env.
- Decide final URLs:
  - DEV: `http://localhost:8080`
  - QA: `https://api.qa.lehrerlog.de`
  - PROD: `https://api.lehrerlog.de`

## Phase 2 - Client build flavors
- Add build config for Compose MPP to produce:
  - Android: `devDebug` (local only), `qaRelease`, `prodRelease`.
  - Desktop/JVM: `qa` + `prod` artifacts (two jars).
  - Web/Wasm: `qa` + `prod` distributions with separate base URLs.
- Make `SERVER_URL` configurable per flavor (Gradle build config or expect/actual).
- Ensure CI builds only QA + PROD artifacts, not DEV.

## Phase 3 - Server deployment flavors
- Keep single server image, but run two compose stacks:
  - PROD: maps host port 18080, nginx `api.lehrerlog.de`
  - QA: maps host port 18081, nginx `api.qa.lehrerlog.de`
- Ensure env files are distinct (`~/docker/lehrerlog/app.env`, `~/docker/lehrerlog-qa/app.env`).
- Confirm healthcheck path `/health` in both.

## Phase 4 - CI artifacts and tagging
- CI on tag: build + publish PROD artifacts.
- CI on main: build + publish QA artifacts.
- Upload artifacts per flavor with clear names (e.g., `composeApp-android-prod.aab`, `composeApp-android-qa.aab`, `composeApp-jvm-prod.jar`, `composeApp-jvm-qa.jar`, etc.).

## Phase 5 - Verification
- Local DEV run against localhost.
- QA deployment reachable via `https://api.qa.lehrerlog.de`.
- Prod deployment reachable via `https://api.lehrerlog.de`.
- CI artifacts downloadable and mapped to the correct flavor.
