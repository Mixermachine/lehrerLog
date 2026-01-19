#!/usr/bin/env bash
set -euo pipefail

# LehrerLog PostgreSQL Backup Script
# Runs daily via cron to backup the database

# Configuration (can be overridden via environment)
ENV_NAME="${ENV_NAME:-prod}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/docker/lehrerlog}"
BACKUP_DIR="${BACKUP_DIR:-/var/lib/lehrerlog/${ENV_NAME}/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
POSTGRES_DB="${POSTGRES_DB:-lehrerlog}"
POSTGRES_USER="${POSTGRES_USER:-lehrerlog}"

# Container name (matches docker-compose.yml)
DB_CONTAINER="lehrerlog-${ENV_NAME}-db"

# Timestamp for backup file
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/${POSTGRES_DB}_${TIMESTAMP}.sql.gz"

echo "=== LehrerLog Database Backup ==="
echo "Timestamp: $(date -Iseconds)"
echo "Environment: $ENV_NAME"
echo "Database: $POSTGRES_DB"
echo "Container: $DB_CONTAINER"
echo "Backup directory: $BACKUP_DIR"
echo "Retention: $BACKUP_RETENTION_DAYS days"
echo ""

# Create backup directory if it doesn't exist
mkdir -p "$BACKUP_DIR"

# Verify container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
    echo "Error: Database container '$DB_CONTAINER' is not running."
    echo "Running containers:"
    docker ps --format '{{.Names}}'
    exit 1
fi

echo "Creating backup..."

# Perform the backup
if docker exec "$DB_CONTAINER" pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | gzip > "$BACKUP_FILE"; then
    BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
    echo "Backup created: $BACKUP_FILE ($BACKUP_SIZE)"
else
    echo "Error: Backup failed!"
    rm -f "$BACKUP_FILE"
    exit 1
fi

# Verify backup is not empty
if [[ ! -s "$BACKUP_FILE" ]]; then
    echo "Error: Backup file is empty!"
    rm -f "$BACKUP_FILE"
    exit 1
fi

# Clean up old backups
echo ""
echo "Cleaning up backups older than $BACKUP_RETENTION_DAYS days..."
DELETED_COUNT=$(find "$BACKUP_DIR" -name "${POSTGRES_DB}_*.sql.gz" -type f -mtime +$BACKUP_RETENTION_DAYS -delete -print | wc -l)
echo "Deleted $DELETED_COUNT old backup(s)"

# List current backups
echo ""
echo "Current backups:"
ls -lht "$BACKUP_DIR"/${POSTGRES_DB}_*.sql.gz 2>/dev/null | head -5 || echo "No backups found"

# Calculate total backup size
TOTAL_SIZE=$(du -sh "$BACKUP_DIR" 2>/dev/null | cut -f1 || echo "0")
BACKUP_COUNT=$(find "$BACKUP_DIR" -name "${POSTGRES_DB}_*.sql.gz" -type f | wc -l)
echo ""
echo "Total: $BACKUP_COUNT backup(s), $TOTAL_SIZE"

echo ""
echo "=== Backup Complete ==="
