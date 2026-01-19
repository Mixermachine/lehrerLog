#!/usr/bin/env bash
set -euo pipefail

# LehrerLog PostgreSQL Restore Script
# Restores database from a backup file

# Configuration (can be overridden via environment)
ENV_NAME="${ENV_NAME:-prod}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/docker/lehrerlog}"
BACKUP_DIR="${BACKUP_DIR:-/var/lib/lehrerlog/${ENV_NAME}/backups}"
POSTGRES_DB="${POSTGRES_DB:-lehrerlog}"
POSTGRES_USER="${POSTGRES_USER:-lehrerlog}"
HOST_PORT="${HOST_PORT:-8080}"

# Container names (match docker-compose.yml)
DB_CONTAINER="lehrerlog-${ENV_NAME}-db"
SERVER_CONTAINER="lehrerlog-${ENV_NAME}-server"

usage() {
    echo "Usage: $0 <backup_file>"
    echo ""
    echo "Restores a LehrerLog database from a backup file."
    echo ""
    echo "Arguments:"
    echo "  backup_file    Path to the backup file (.sql.gz)"
    echo ""
    echo "Examples:"
    echo "  $0 /var/lib/lehrerlog/prod/backups/lehrerlog_prod_20240115_030000.sql.gz"
    echo "  $0 latest    # Restore from most recent backup"
    echo ""
    echo "Available backups:"
    ls -lht "$BACKUP_DIR"/${POSTGRES_DB}_*.sql.gz 2>/dev/null | head -10 || echo "  No backups found in $BACKUP_DIR"
    exit 1
}

# Check arguments
if [[ $# -lt 1 ]]; then
    usage
fi

BACKUP_FILE="$1"

# Handle "latest" shortcut
if [[ "$BACKUP_FILE" == "latest" ]]; then
    BACKUP_FILE=$(ls -t "$BACKUP_DIR"/${POSTGRES_DB}_*.sql.gz 2>/dev/null | head -1 || echo "")
    if [[ -z "$BACKUP_FILE" ]]; then
        echo "Error: No backups found in $BACKUP_DIR"
        exit 1
    fi
    echo "Using latest backup: $BACKUP_FILE"
fi

# Verify backup file exists
if [[ ! -f "$BACKUP_FILE" ]]; then
    echo "Error: Backup file not found: $BACKUP_FILE"
    exit 1
fi

echo "=== LehrerLog Database Restore ==="
echo "Environment: $ENV_NAME"
echo "Database: $POSTGRES_DB"
echo "Backup file: $BACKUP_FILE"
echo "DB Container: $DB_CONTAINER"
echo "Server Container: $SERVER_CONTAINER"
echo ""

# Verify container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
    echo "Error: Database container '$DB_CONTAINER' is not running."
    exit 1
fi

# Confirm restore
echo ""
echo "WARNING: This will OVERWRITE the current database!"
echo "All existing data in '$POSTGRES_DB' will be LOST!"
echo ""
read -p "Type 'yes' to confirm restore: " CONFIRM

if [[ "$CONFIRM" != "yes" ]]; then
    echo "Restore cancelled."
    exit 0
fi

echo ""
echo "Stopping application to prevent writes..."
docker stop "$SERVER_CONTAINER" 2>/dev/null || true

echo ""
echo "Restoring database..."

# Drop and recreate database, then restore
docker exec -i "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d postgres <<EOF
-- Terminate existing connections
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$POSTGRES_DB' AND pid <> pg_backend_pid();
-- Drop and recreate
DROP DATABASE IF EXISTS $POSTGRES_DB;
CREATE DATABASE $POSTGRES_DB OWNER $POSTGRES_USER;
EOF

# Restore from backup
if gunzip -c "$BACKUP_FILE" | docker exec -i "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"; then
    echo "Database restored successfully!"
else
    echo "Error: Restore failed!"
    echo "Starting application anyway..."
    docker start "$SERVER_CONTAINER"
    exit 1
fi

echo ""
echo "Starting application..."
docker start "$SERVER_CONTAINER"

echo ""
echo "Waiting for health check..."
sleep 10

# Check health
if curl -fsS "http://localhost:${HOST_PORT}/health" > /dev/null 2>&1; then
    echo "Application is healthy!"
else
    echo "Warning: Health check failed. Please verify manually:"
    echo "  curl http://localhost:${HOST_PORT}/health"
    echo "  docker logs $SERVER_CONTAINER"
fi

echo ""
echo "=== Restore Complete ==="
