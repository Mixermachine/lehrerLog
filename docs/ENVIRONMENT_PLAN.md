# Environment Flavors Plan

Goal: add DEV/LOCAL, QA, and PROD flavors for client + server. CI should build QA and PROD artifacts only. DEV stays local.

## Phase 0 - Versioning and release tags
- Base semver starts at `0.0.1`.
- PROD tags must be `MAJOR.MINOR.PATCH-bNNNN` (example: `0.0.1-b0001`).
- Build number for tags is computed as: last tag build + 1, **before** tag creation.
- Release name must match the tag (same string).
- Non-tag builds use `0.0.1-dev.YYYYMMDD.RUN` as version name.
- Non-tag build number uses `RUN` only (monotonic in CI).
- Android versionCode and iOS CFBundleVersion must use the same computed integer:
  - `major*100_000_000 + minor*1_000_000 + patch*10_000 + build`
- iOS short version uses `MAJOR.MINOR.PATCH` only (no suffix).

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
- Ensure Android QA uses a distinct applicationId (suffix `.qa`) to avoid cross-install with prod.
- Ensure iOS QA uses a distinct bundle identifier (suffix `.qa`) to avoid cross-install with prod.
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
- Add a release workflow (manual trigger) that:
  - Reads the last prod tag, increments `bNNNN`, and creates the next tag.
  - Uses the tag string for the release name and for all prod artifacts.

## Phase 5 - Verification
- Local DEV run against localhost.
- QA deployment reachable via `https://api.qa.lehrerlog.de`.
- Prod deployment reachable via `https://api.lehrerlog.de`.
- CI artifacts downloadable and mapped to the correct flavor.
