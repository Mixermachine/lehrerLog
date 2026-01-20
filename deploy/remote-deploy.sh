#!/usr/bin/env bash
set -euo pipefail

# Ensure system sbin paths are available for non-interactive shells.
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

# Configuration with defaults
DOMAIN="${DOMAIN:-lehrerlog.9d4.de}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/docker/lehrerlog}"
IMAGE_NAME="${IMAGE_NAME:-ghcr.io/${REPO_OWNER:-lehrerlog}/lehrerlog-server}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-}"
GHCR_USERNAME="${GHCR_USERNAME:-}"
GHCR_TOKEN="${GHCR_TOKEN:-}"
SKIP_DNS_CHECK="${SKIP_DNS_CHECK:-false}"
SUDO_PASSWORD="${SUDO_PASSWORD:-}"

# Resolve command paths for sudoers matching.
BIN_APT_GET="$(command -v apt-get || true)"
BIN_CERTBOT="$(command -v certbot || true)"
BIN_CHMOD="$(command -v chmod || true)"
BIN_CHOWN="$(command -v chown || true)"
BIN_CP="$(command -v cp || true)"
BIN_LN="$(command -v ln || true)"
BIN_LOGROTATE="$(command -v logrotate || true)"
BIN_MKDIR="$(command -v mkdir || true)"
BIN_NGINX="$(command -v nginx || true)"
BIN_SED="$(command -v sed || true)"
BIN_SYSTEMCTL="$(command -v systemctl || true)"
BIN_TEE="$(command -v tee || true)"

require_cmd() {
  local name="$1"
  local path="$2"
  if [[ -z "$path" ]]; then
    echo "Error: required command not found: $name"
    exit 1
  fi
}

require_cmd "mkdir" "$BIN_MKDIR"
require_cmd "chown" "$BIN_CHOWN"
require_cmd "cp" "$BIN_CP"
require_cmd "ln" "$BIN_LN"
require_cmd "sed" "$BIN_SED"
require_cmd "tee" "$BIN_TEE"
require_cmd "chmod" "$BIN_CHMOD"
require_cmd "nginx" "$BIN_NGINX"
require_cmd "systemctl" "$BIN_SYSTEMCTL"
require_cmd "logrotate" "$BIN_LOGROTATE"

# PostgreSQL settings
POSTGRES_DB="${POSTGRES_DB:-lehrerlog}"
POSTGRES_USER="${POSTGRES_USER:-lehrerlog}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-}"

# Storage directories - default to environment-specific paths
ENV_NAME="${ENV_NAME:-prod}"
HOST_PORT="${HOST_PORT:-}"
DATA_DIR="${DATA_DIR:-/var/lib/lehrerlog/${ENV_NAME}/data}"
DB_DATA_DIR="${DB_DATA_DIR:-/var/lib/lehrerlog/${ENV_NAME}/pgdata}"
BACKUP_DIR="${BACKUP_DIR:-/var/lib/lehrerlog/${ENV_NAME}/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
ENABLE_BACKUP_CRON="${ENABLE_BACKUP_CRON:-true}"

# Lock file for preventing concurrent deployments
LOCK_FILE="/tmp/lehrerlog-deploy-${ENV_NAME}.lock"

# Validation
if [[ -z "$LETSENCRYPT_EMAIL" ]]; then
  echo "Error: LETSENCRYPT_EMAIL is required."
  exit 1
fi

# Configure sudo for non-interactive use (either passwordless or SUDO_PASSWORD).
if [[ -n "$SUDO_PASSWORD" ]]; then
  ASKPASS_FILE="$(mktemp)"
  chmod 700 "$ASKPASS_FILE"
  printf '#!/bin/sh\necho "%s"\n' "$SUDO_PASSWORD" > "$ASKPASS_FILE"
  export SUDO_ASKPASS="$ASKPASS_FILE"
  export SUDO_ASKPASS_REQUIRE=force
  export SUDO_PROMPT=""
  sudo() {
    if ! command sudo -A "$@"; then
      echo "Error: sudo failed for: $*"
      exit 1
    fi
  }
  trap 'rm -f "$ASKPASS_FILE"' EXIT
else
  sudo() {
    if ! command sudo -n "$@"; then
      echo "Error: sudo failed for: $*"
      exit 1
    fi
  }
fi

# Ensure sudo is non-interactive using a whitelisted command (GitHub Actions has no TTY).
SUDO_CHECK_DIR="/tmp/lehrerlog-sudo-check-${ENV_NAME}"
if ! sudo "$BIN_MKDIR" -p "$SUDO_CHECK_DIR" 2>/dev/null; then
  echo "Error: passwordless sudo is required for deployment."
  echo "Configure sudoers for the deploy user (e.g., aaron) or rerun manually with a TTY."
  exit 1
fi

# Acquire deployment lock
acquire_lock() {
  if [[ -f "$LOCK_FILE" ]]; then
    LOCK_PID=$(cat "$LOCK_FILE" 2>/dev/null || echo "")
    if [[ -n "$LOCK_PID" ]] && kill -0 "$LOCK_PID" 2>/dev/null; then
      echo "Error: Another deployment is in progress (PID: $LOCK_PID)"
      echo "If this is stale, remove $LOCK_FILE manually"
      exit 1
    fi
    echo "Warning: Removing stale lock file"
    rm -f "$LOCK_FILE"
  fi
  echo $$ > "$LOCK_FILE"
  trap 'rm -f "$LOCK_FILE"' EXIT
}

acquire_lock

echo "=== LehrerLog Deployment ==="
echo "Environment: $ENV_NAME"
echo "Domain: $DOMAIN"
echo "Deploy directory: $DEPLOY_DIR"
echo "Data directory: $DATA_DIR"
echo "Database directory: $DB_DATA_DIR"
echo "Backup directory: $BACKUP_DIR"
if [[ -z "$HOST_PORT" ]]; then
  if [[ "$ENV_NAME" == "staging" ]]; then
    HOST_PORT=18081
  else
    HOST_PORT=18080
  fi
fi

echo "Port: $HOST_PORT"
echo ""

# Create directories (only if they don't exist)
mkdir -p "$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR/.deploy"

# Handle POSTGRES_PASSWORD: reuse existing, use provided, or generate new
EXISTING_PASSWORD=""
if [[ -f "$DEPLOY_DIR/.env" ]]; then
  EXISTING_PASSWORD=$(grep -E '^POSTGRES_PASSWORD=' "$DEPLOY_DIR/.env" | head -1 | cut -d= -f2- || true)
fi

if [[ -z "$POSTGRES_PASSWORD" ]]; then
  # No password provided - use existing or generate new
  if [[ -n "$EXISTING_PASSWORD" ]]; then
    POSTGRES_PASSWORD="$EXISTING_PASSWORD"
    echo "Using existing POSTGRES_PASSWORD from $DEPLOY_DIR/.env"
  else
    # Avoid SIGPIPE causing exit under pipefail when head closes the pipe.
    set +o pipefail
    POSTGRES_PASSWORD=$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)
    set -o pipefail
    echo "Generated new POSTGRES_PASSWORD for ${ENV_NAME}"
  fi
elif [[ -n "$EXISTING_PASSWORD" ]] && [[ "$POSTGRES_PASSWORD" != "$EXISTING_PASSWORD" ]]; then
  # Password provided but differs from existing - DANGER!
  echo ""
  echo "WARNING: Provided POSTGRES_PASSWORD differs from existing password in $DEPLOY_DIR/.env"
  echo "The database was initialized with the existing password."
  echo "Changing it will cause connection failures!"
  echo ""
  echo "Options:"
  echo "  1. Remove the POSTGRES_PASSWORD secret to use existing password"
  echo "  2. Manually update the database password first"
  echo "  3. Set FORCE_PASSWORD_CHANGE=true to proceed anyway (DANGEROUS)"
  echo ""
  if [[ "${FORCE_PASSWORD_CHANGE:-false}" != "true" ]]; then
    echo "Falling back to existing password for safety."
    POSTGRES_PASSWORD="$EXISTING_PASSWORD"
  else
    echo "FORCE_PASSWORD_CHANGE=true - proceeding with new password!"
  fi
fi

# Create data directories with proper ownership (only if new)
for DIR in "$DATA_DIR" "$BACKUP_DIR"; do
  if [[ ! -d "$DIR" ]]; then
    sudo "$BIN_MKDIR" -p "$DIR"
    sudo "$BIN_CHOWN" "$(id -u):$(id -g)" "$DIR"
  fi
done

if [[ ! -d "$DB_DATA_DIR" ]]; then
  sudo "$BIN_MKDIR" -p "$DB_DATA_DIR"
  sudo "$BIN_CHOWN" 999:999 "$DB_DATA_DIR"  # PostgreSQL container runs as uid 999
fi

# Copy deployment files if available
if [[ -f "$DEPLOY_DIR/.deploy/docker-compose.yml" ]]; then
  cp "$DEPLOY_DIR/.deploy/docker-compose.yml" "$DEPLOY_DIR/docker-compose.yml"
fi

if [[ -f "$DEPLOY_DIR/.deploy/app.env.example" ]] && [[ ! -f "$DEPLOY_DIR/app.env" ]]; then
  cp "$DEPLOY_DIR/.deploy/app.env.example" "$DEPLOY_DIR/app.env"
fi

# Create/update .env file with current settings
cat > "$DEPLOY_DIR/.env" <<EOF
# Auto-generated by remote-deploy.sh on $(date -Iseconds)
ENV_NAME=$ENV_NAME
IMAGE_NAME=$IMAGE_NAME
IMAGE_TAG=$IMAGE_TAG
HOST_PORT=$HOST_PORT
POSTGRES_DB=$POSTGRES_DB
POSTGRES_USER=$POSTGRES_USER
POSTGRES_PASSWORD=$POSTGRES_PASSWORD
DATA_DIR=$DATA_DIR
DB_DATA_DIR=$DB_DATA_DIR
EOF

echo "Created .env file at $DEPLOY_DIR/.env"

# Setup nginx configuration (avoid overwriting certbot-managed SSL blocks).
NGINX_SITE="/etc/nginx/sites-available/$DOMAIN"
if [[ ! -f "$NGINX_SITE" ]]; then
  if [[ -f "$DEPLOY_DIR/.deploy/nginx/$DOMAIN.conf" ]]; then
    sudo "$BIN_CP" "$DEPLOY_DIR/.deploy/nginx/$DOMAIN.conf" "$NGINX_SITE"
  else
    sudo "$BIN_CP" "$DEPLOY_DIR/.deploy/nginx/lehrerlog.9d4.de.conf" "$NGINX_SITE"
    sudo "$BIN_SED" -i "s/server_name lehrerlog.9d4.de;/server_name $DOMAIN;/" "$NGINX_SITE"
  fi
fi
# Ensure current domain and upstream port are updated in-place.
sudo "$BIN_SED" -i "s/server_name .*/server_name $DOMAIN;/" "$NGINX_SITE"
sudo "$BIN_SED" -i "s|proxy_pass http://127.0.0.1:[0-9]*;|proxy_pass http://127.0.0.1:$HOST_PORT;|g" "$NGINX_SITE"
sudo "$BIN_SED" -i "s|proxy_pass http://localhost:[0-9]*;|proxy_pass http://127.0.0.1:$HOST_PORT;|g" "$NGINX_SITE"

# Create symlink if it doesn't exist (use -e to check for symlink existence)
if [[ ! -e "/etc/nginx/sites-enabled/$DOMAIN" ]]; then
  sudo "$BIN_LN" -s "/etc/nginx/sites-available/$DOMAIN" "/etc/nginx/sites-enabled/$DOMAIN"
fi

# Install certbot if needed
if [[ -z "$BIN_CERTBOT" ]]; then
  require_cmd "apt-get" "$BIN_APT_GET"
  sudo "$BIN_APT_GET" update
  sudo "$BIN_APT_GET" install -y certbot python3-certbot-nginx
  BIN_CERTBOT="$(command -v certbot || true)"
  require_cmd "certbot" "$BIN_CERTBOT"
fi

# Test and reload nginx
sudo "$BIN_NGINX" -t
sudo "$BIN_SYSTEMCTL" reload nginx

# DNS check
if [[ "$SKIP_DNS_CHECK" != "true" ]]; then
  if ! getent hosts "$DOMAIN" >/dev/null 2>&1; then
    echo "Error: DNS lookup failed for $DOMAIN. Set SKIP_DNS_CHECK=true to bypass."
    exit 1
  fi
fi

# Setup SSL certificate (ensure nginx is actually configured for HTTPS).
CERT_EXISTS=false
if sudo "$BIN_CERTBOT" certificates -d "$DOMAIN" 2>/dev/null | grep -q "Certificate Name"; then
  CERT_EXISTS=true
fi
SSL_CONFIGURED=false
if [[ -r "$NGINX_SITE" ]] && grep -q "ssl_certificate" "$NGINX_SITE"; then
  SSL_CONFIGURED=true
fi

if [[ "$SSL_CONFIGURED" == "false" ]]; then
  echo "Configuring HTTPS for $DOMAIN..."
  if [[ "$CERT_EXISTS" == "true" ]]; then
    sudo "$BIN_CERTBOT" --nginx -d "$DOMAIN" --non-interactive --agree-tos -m "$LETSENCRYPT_EMAIL" --reinstall
  else
    echo "Obtaining SSL certificate for $DOMAIN..."
    sudo "$BIN_CERTBOT" --nginx -d "$DOMAIN" --non-interactive --agree-tos -m "$LETSENCRYPT_EMAIL"
  fi
  sudo "$BIN_SYSTEMCTL" reload nginx
fi

# Docker login to GHCR
if [[ -n "$GHCR_USERNAME" ]] && [[ -n "$GHCR_TOKEN" ]]; then
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USERNAME" --password-stdin
fi

# Deploy with docker compose
cd "$DEPLOY_DIR"
echo "Pulling latest images..."
docker compose pull

echo "Starting services..."
docker compose up -d --remove-orphans

# Clean up old Docker images to prevent disk bloat
echo ""
echo "Cleaning up old Docker images..."
# Remove dangling images
docker image prune -f || true
# Remove old lehrerlog-server images (keep only current)
CURRENT_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
docker images "${IMAGE_NAME}" --format "{{.Repository}}:{{.Tag}}" | \
  grep -v "^${CURRENT_IMAGE}$" | \
  grep -v "<none>" | \
  xargs -r docker rmi || true

# Wait for health check
echo ""
echo "Waiting for services to be healthy..."
sleep 5
docker compose ps

# Setup backup scripts
if [[ -f "$DEPLOY_DIR/.deploy/backup.sh" ]]; then
  cp "$DEPLOY_DIR/.deploy/backup.sh" "$DEPLOY_DIR/backup.sh"
  chmod +x "$DEPLOY_DIR/backup.sh"
fi

if [[ -f "$DEPLOY_DIR/.deploy/restore.sh" ]]; then
  cp "$DEPLOY_DIR/.deploy/restore.sh" "$DEPLOY_DIR/restore.sh"
  chmod +x "$DEPLOY_DIR/restore.sh"
fi

# Setup backup cron job and log rotation
if [[ "$ENABLE_BACKUP_CRON" == "true" ]]; then
  echo ""
  echo "Setting up backup cron job..."

  CRON_FILE="/etc/cron.d/lehrerlog-backup-${ENV_NAME}"
  CRON_SCRIPT="$DEPLOY_DIR/backup.sh"
  LOG_FILE="/var/log/lehrerlog-backup-${ENV_NAME}.log"

  # Create cron job that runs daily at 3 AM (stagger staging at 3:30)
  if [[ "$ENV_NAME" == "staging" ]]; then
    CRON_HOUR="3"
    CRON_MINUTE="30"
  else
    CRON_HOUR="3"
    CRON_MINUTE="0"
  fi

  sudo "$BIN_TEE" "$CRON_FILE" > /dev/null <<CRON
# LehrerLog $ENV_NAME database backup - runs daily
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin

$CRON_MINUTE $CRON_HOUR * * * $(whoami) ENV_NAME=$ENV_NAME DEPLOY_DIR=$DEPLOY_DIR BACKUP_DIR=$BACKUP_DIR BACKUP_RETENTION_DAYS=$BACKUP_RETENTION_DAYS POSTGRES_DB=$POSTGRES_DB POSTGRES_USER=$POSTGRES_USER $CRON_SCRIPT >> $LOG_FILE 2>&1
CRON

  sudo "$BIN_CHMOD" 644 "$CRON_FILE"

  # Setup log rotation (idempotent - overwrites existing config)
  LOGROTATE_FILE="/etc/logrotate.d/lehrerlog-backup-${ENV_NAME}"
  sudo "$BIN_TEE" "$LOGROTATE_FILE" > /dev/null <<LOGROTATE
$LOG_FILE {
    weekly
    rotate 4
    compress
    delaycompress
    missingok
    notifempty
    create 644 $(whoami) $(whoami)
}
LOGROTATE

  sudo "$BIN_CHMOD" 644 "$LOGROTATE_FILE"

  echo "Backup cron job installed: $CRON_FILE"
  echo "Log rotation installed: $LOGROTATE_FILE"
  echo "Backups will run daily at ${CRON_HOUR}:${CRON_MINUTE} and retain for $BACKUP_RETENTION_DAYS days"
  echo "Backup logs: $LOG_FILE (rotated weekly, kept 4 weeks)"
fi

echo ""
echo "=== Deployment Complete ==="
echo "Service URL: https://$DOMAIN"
echo "Health check: https://$DOMAIN/health"
echo ""
echo "Backup commands:"
echo "  Manual backup:  $DEPLOY_DIR/backup.sh"
echo "  Restore:        $DEPLOY_DIR/restore.sh <backup_file>"
echo "  Restore latest: $DEPLOY_DIR/restore.sh latest"
