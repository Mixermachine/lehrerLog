# Environment Rename Plan (staging -> qa)

Goal
- Rename the current staging environment to QA and update server base URLs to the new domains.
- PROD API: https://api.lehrerlog.de
- QA API: https://api.qa.lehrerlog.de

Phase 1: Inventory and safety checks
- Identify all references to "staging" in code, docs, workflows, deploy scripts, nginx templates, and env examples.
- Record existing secrets/vars that need renaming (STAGING_* -> QA_*).
- Confirm existing server directories and container names that will be renamed (e.g., ~/docker/lehrerlog-staging -> ~/docker/lehrerlog-qa).

Phase 2: Client URL updates
- Update client build URLs in CI and docs:
  - QA -> https://api.qa.lehrerlog.de
  - PROD -> https://api.lehrerlog.de
- Keep DEV as localhost.
- Ensure all client artifacts use QA/PROD URLs via -PserverUrl.

Phase 3: Workflow and secret refactor
- Rename CI job labels from staging -> qa.
- Update workflow inputs and artifact names to QA.
- Rename secrets/vars used by deployment + verification:
  - STAGING_DB_PASSWORD -> QA_DB_PASSWORD
  - STAGING_TEST_USER_EMAIL/PASSWORD -> QA_TEST_USER_EMAIL/PASSWORD
  - Any remaining STAGING_* references.

Phase 4: Server deploy config + nginx
- Rename env examples and directories:
  - deploy/.env.staging.example -> deploy/.env.qa.example
  - Update ENV_NAME=qa, DB names, ports, data paths.
- Update nginx config templates:
  - staging.lehrerlog.9d4.de -> api.qa.lehrerlog.de
  - prod host -> api.lehrerlog.de
- Update deploy scripts to use QA names and domains.
- Update seed defaults to QA (names, school code, school name).

Phase 5: Documentation updates
- Update AGENTS.md and README.md to reflect QA/PROD URLs.
- Update docs/ENVIRONMENT_PLAN.md to reflect QA naming and new domains.

Phase 6: Apply + verify
- Apply code changes, commit, and push.
- Trigger CI builds for QA/PROD client artifacts.
- Deploy QA and PROD server using updated domains.
- Verify health, school search, and auth flows on both APIs.

Open questions before execution
- Do you want to keep existing data from the current staging DB, or start fresh for QA?
- Should the QA server use the same ports as current staging (18081) or new ones?
