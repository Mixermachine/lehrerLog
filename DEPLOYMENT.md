# Deployment & Rollback Procedures

This document describes the approved rollback procedures for LehrerLog deployments, with a focus on database safety.

## Application Rollback (Docker)

1. Identify the last known good release tag (e.g., `0.3.2-b0123`).
2. Update the image tag in `deploy/docker-compose.yml` (or the QA/PROD env file used by `deploy/remote-deploy.sh`).
3. Deploy the previous version:
   ```shell
   docker compose -f deploy/docker-compose.yml pull
   docker compose -f deploy/docker-compose.yml up -d
   ```
4. Run the verification script:
   ```shell
   ./deploy/verify.sh
   ```

## Database Rollback (Flyway)

Flyway migrations are **forward-only**. Rollback is performed by restoring a backup and repairing metadata.

### Before Every Production Deployment

- Create a database backup using:
  ```shell
  ./deploy/backup.sh
  ```
- Store the backup artifact securely with the release tag in its name.

### Rollback Procedure

1. Stop the application (Docker rollback above or stop the current deployment).
2. Restore the backup:
   ```shell
   ./deploy/restore.sh
   ```
3. Repair Flyway metadata:
   ```shell
   ./gradlew :server:flywayRepair
   ```
    - For QA/PROD, use the GitHub workflow `.github/workflows/flyway-repair.yml`.
4. Redeploy the last known good application version.
5. Verify service health:
   ```shell
   ./deploy/verify.sh
   ```

### When a Migration Fails Mid-Deploy

- **Do not edit** applied migration files.
- Restore the database backup, run Flyway repair, and redeploy.
- Create a **new** migration for any fixes.

## Notes

- Migrations are stored in `server/src/main/resources/db/migration/` and must remain immutable once applied.
- Use `deploy/backup.sh` and `deploy/restore.sh` for consistent backups across environments.
