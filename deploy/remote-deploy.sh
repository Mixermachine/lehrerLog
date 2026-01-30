#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Error at line $LINENO" >&2' ERR

# Ensure system sbin paths are available for non-interactive shells.
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

# Configuration with defaults
DOMAIN="${DOMAIN:-api.lehrerlog.de}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/docker/lehrerlog}"
IMAGE_NAME="${IMAGE_NAME:-}"
IMAGE_TAG="${IMAGE_TAG:-}"
WEBAPP_IMAGE_NAME="${WEBAPP_IMAGE_NAME:-}"
WEBAPP_IMAGE_TAG="${WEBAPP_IMAGE_TAG:-}"
WEBAPP_HOST_PORT="${WEBAPP_HOST_PORT:-}"
WEBAPP_DOMAIN="${WEBAPP_DOMAIN:-}"
DEPLOY_WEBAPP_ONLY="${DEPLOY_WEBAPP_ONLY:-false}"
DEPLOY_SERVER_ONLY="${DEPLOY_SERVER_ONLY:-false}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-}"
GHCR_USERNAME="${GHCR_USERNAME:-}"
GHCR_TOKEN="${GHCR_TOKEN:-}"
SKIP_DNS_CHECK="${SKIP_DNS_CHECK:-false}"
SUDO_PASSWORD="${SUDO_PASSWORD:-}"

if [[ -n "$GHCR_USERNAME" ]]; then
  GHCR_USERNAME="${GHCR_USERNAME,,}"
fi

# Resolve command paths for sudoers matching.
BIN_APT_GET="$(command -v apt-get || true)"
BIN_CERTBOT="$(command -v certbot || true)"
BIN_CHMOD="$(command -v chmod || true)"
BIN_CHOWN="$(command -v chown || true)"
BIN_CP="$(command -v cp || true)"
BIN_LN="$(command -v ln || true)"
BIN_BASE64="$(command -v base64 || true)"
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

# Optional seed test user config (useful for QA verification).
SEED_TEST_USER_EMAIL="${SEED_TEST_USER_EMAIL:-}"
SEED_TEST_USER_PASSWORD="${SEED_TEST_USER_PASSWORD:-}"
SEED_TEST_USER_PASSWORD_B64="${SEED_TEST_USER_PASSWORD_B64:-}"
SEED_TEST_USER_FIRST_NAME="${SEED_TEST_USER_FIRST_NAME:-}"
SEED_TEST_USER_LAST_NAME="${SEED_TEST_USER_LAST_NAME:-}"
SEED_TEST_SCHOOL_CODE="${SEED_TEST_SCHOOL_CODE:-}"
SEED_TEST_SCHOOL_NAME="${SEED_TEST_SCHOOL_NAME:-}"

# Optional post-deploy auth verification (defaults to seed user).
VERIFY_USER_EMAIL="${VERIFY_USER_EMAIL:-$SEED_TEST_USER_EMAIL}"
VERIFY_USER_PASSWORD="${VERIFY_USER_PASSWORD:-$SEED_TEST_USER_PASSWORD}"

# PostgreSQL settings
POSTGRES_DB="${POSTGRES_DB:-lehrerlog}"
POSTGRES_USER="${POSTGRES_USER:-lehrerlog}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-}"
POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:16-alpine}"
POSTGRES_UID="${POSTGRES_UID:-}"
POSTGRES_GID="${POSTGRES_GID:-}"
if [[ -z "$POSTGRES_UID" || -z "$POSTGRES_GID" ]]; then
  if [[ "$POSTGRES_IMAGE" == *"alpine"* ]]; then
    POSTGRES_UID=70
    POSTGRES_GID=70
  else
    POSTGRES_UID=999
    POSTGRES_GID=999
  fi
fi

# Storage directories - default to environment-specific paths
ENV_NAME="${ENV_NAME:-prod}"
HOST_PORT="${HOST_PORT:-}"
DATA_DIR="${DATA_DIR:-/var/lib/lehrerlog/${ENV_NAME}/data}"
DB_DATA_DIR="${DB_DATA_DIR:-/var/lib/lehrerlog/${ENV_NAME}/pgdata}"
BACKUP_DIR="${BACKUP_DIR:-/var/lib/lehrerlog/${ENV_NAME}/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
ENABLE_BACKUP_CRON="${ENABLE_BACKUP_CRON:-true}"
GARAGE_DIR="${GARAGE_DIR:-/var/lib/lehrerlog/${ENV_NAME}/garage}"
GARAGE_CONFIG_PATH="${GARAGE_CONFIG_PATH:-$DEPLOY_DIR/garage/garage.toml}"
GARAGE_IMAGE="${GARAGE_IMAGE:-dxflrs/garage:v2.2.0}"
GARAGE_API_PORT="${GARAGE_API_PORT:-}"
GARAGE_ADMIN_PORT="${GARAGE_ADMIN_PORT:-}"
GARAGE_ADMIN_TOKEN="${GARAGE_ADMIN_TOKEN:-}"
GARAGE_METRICS_TOKEN="${GARAGE_METRICS_TOKEN:-}"
GARAGE_RPC_SECRET="${GARAGE_RPC_SECRET:-}"

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
  if [[ "$ENV_NAME" == "qa" ]]; then
    HOST_PORT=18081
  else
    HOST_PORT=18080
  fi
fi

if [[ -z "$WEBAPP_HOST_PORT" ]]; then
  if [[ "$ENV_NAME" == "qa" ]]; then
    WEBAPP_HOST_PORT=18083
  else
    WEBAPP_HOST_PORT=18082
  fi
fi

if [[ -z "$WEBAPP_DOMAIN" ]]; then
  if [[ "$ENV_NAME" == "qa" ]]; then
    WEBAPP_DOMAIN=app.qa.lehrerlog.de
  else
    WEBAPP_DOMAIN=app.lehrerlog.de
  fi
fi

if [[ -z "$GARAGE_API_PORT" ]]; then
  if [[ "$ENV_NAME" == "qa" ]]; then
    GARAGE_API_PORT=3910
  else
    GARAGE_API_PORT=3900
  fi
fi

if [[ -z "$GARAGE_ADMIN_PORT" ]]; then
  if [[ "$ENV_NAME" == "qa" ]]; then
    GARAGE_ADMIN_PORT=3913
  else
    GARAGE_ADMIN_PORT=3903
  fi
fi

cleanup_legacy_staging() {
  if [[ "$ENV_NAME" != "qa" ]]; then
    return
  fi
  local legacy_dir="$HOME/docker/lehrerlog-staging"
  if [[ -f "$legacy_dir/docker-compose.yml" ]]; then
    echo "Stopping legacy staging stack at $legacy_dir to free port $HOST_PORT..."
    docker rm -f lehrerlog-staging-server lehrerlog-staging-db || true
    docker network rm lehrerlog-staging_lehrerlog-net || true
  fi
}

cleanup_legacy_staging

if [[ "$DEPLOY_WEBAPP_ONLY" != "true" ]]; then
  if docker ps --format '{{.Names}} {{.Ports}}' | grep -q ":${HOST_PORT}->"; then
    echo "Host port ${HOST_PORT} is already in use. Checking for existing LehrerLog containers..."
    containers="$(docker ps --filter "publish=${HOST_PORT}" --format '{{.Names}}' || true)"
    non_lehrerlog=""
    if [[ -n "$containers" ]]; then
      while read -r container; do
        [[ -z "$container" ]] && continue
        if [[ "$container" == lehrerlog-* ]]; then
          echo "Stopping container ${container} to free port ${HOST_PORT}..."
          docker rm -f "$container" || true
        else
          non_lehrerlog="${non_lehrerlog} ${container}"
        fi
      done <<< "$containers"
    fi
    if [[ -n "$non_lehrerlog" ]]; then
      echo "Error: host port ${HOST_PORT} is used by non-LehrerLog containers:${non_lehrerlog}"
      exit 1
    fi
  fi
fi

echo "Port: $HOST_PORT"
echo ""

# Create directories (only if they don't exist)
echo "Step: ensure deploy directories exist"
if ! mkdir -p "$DEPLOY_DIR" "$DEPLOY_DIR/.deploy"; then
  echo "Error: unable to create deploy directories at $DEPLOY_DIR"
  exit 1
fi

# Handle POSTGRES_PASSWORD: reuse existing, use provided, or generate new
SERVER_ENV_FILE="$DEPLOY_DIR/server.env"
WEBAPP_ENV_FILE="$DEPLOY_DIR/webapp.env"
echo "Step: check legacy env migration"
if [[ ! -f "$SERVER_ENV_FILE" && -f "$DEPLOY_DIR/.env" ]]; then
  mv "$DEPLOY_DIR/.env" "$SERVER_ENV_FILE"
  echo "Migrated legacy .env to server.env"
fi
echo "Step: read existing env values"
EXISTING_PASSWORD=""
EXISTING_IMAGE_NAME=""
EXISTING_IMAGE_TAG=""
if [[ -f "$SERVER_ENV_FILE" ]]; then
  EXISTING_PASSWORD=$(grep -E '^POSTGRES_PASSWORD=' "$SERVER_ENV_FILE" | head -1 | cut -d= -f2- || true)
  EXISTING_IMAGE_NAME=$(grep -E '^IMAGE_NAME=' "$SERVER_ENV_FILE" | head -1 | cut -d= -f2- || true)
  EXISTING_IMAGE_TAG=$(grep -E '^IMAGE_TAG=' "$SERVER_ENV_FILE" | head -1 | cut -d= -f2- || true)
fi
EXISTING_WEBAPP_IMAGE_NAME=""
EXISTING_WEBAPP_IMAGE_TAG=""
EXISTING_WEBAPP_HOST_PORT=""
if [[ -f "$WEBAPP_ENV_FILE" ]]; then
  EXISTING_WEBAPP_IMAGE_NAME=$(grep -E '^WEBAPP_IMAGE_NAME=' "$WEBAPP_ENV_FILE" | head -1 | cut -d= -f2- || true)
  EXISTING_WEBAPP_IMAGE_TAG=$(grep -E '^WEBAPP_IMAGE_TAG=' "$WEBAPP_ENV_FILE" | head -1 | cut -d= -f2- || true)
  EXISTING_WEBAPP_HOST_PORT=$(grep -E '^WEBAPP_HOST_PORT=' "$WEBAPP_ENV_FILE" | head -1 | cut -d= -f2- || true)
elif [[ -f "$SERVER_ENV_FILE" ]]; then
  # Legacy location (prior to split into webapp.env)
  EXISTING_WEBAPP_IMAGE_NAME=$(grep -E '^WEBAPP_IMAGE_NAME=' "$SERVER_ENV_FILE" | head -1 | cut -d= -f2- || true)
  EXISTING_WEBAPP_IMAGE_TAG=$(grep -E '^WEBAPP_IMAGE_TAG=' "$SERVER_ENV_FILE" | head -1 | cut -d= -f2- || true)
  EXISTING_WEBAPP_HOST_PORT=$(grep -E '^WEBAPP_HOST_PORT=' "$SERVER_ENV_FILE" | head -1 | cut -d= -f2- || true)
fi

echo "Step: resolve POSTGRES_PASSWORD"
if [[ -z "$POSTGRES_PASSWORD" ]]; then
  # No password provided - use existing or generate new
  echo "Step: POSTGRES_PASSWORD empty; existing password length: ${#EXISTING_PASSWORD}"
  DB_HAS_DATA=false
  if [[ -d "$DB_DATA_DIR" ]]; then
    echo "Step: checking DB_DATA_DIR contents at $DB_DATA_DIR"
    if [[ ! -r "$DB_DATA_DIR" ]]; then
      echo "Info: adjusting ownership to inspect $DB_DATA_DIR"
      sudo "$BIN_CHOWN" "$(id -u):$(id -g)" "$DB_DATA_DIR"
    fi
    if find "$DB_DATA_DIR" -mindepth 1 -print -quit 2>/dev/null | grep -q .; then
      DB_HAS_DATA=true
    fi
  fi

  if [[ -n "$EXISTING_PASSWORD" && "$DB_HAS_DATA" == "true" ]]; then
    POSTGRES_PASSWORD="$EXISTING_PASSWORD"
    echo "Using existing POSTGRES_PASSWORD from $SERVER_ENV_FILE"
  else
    if [[ -n "$EXISTING_PASSWORD" && "$DB_HAS_DATA" != "true" ]]; then
      echo "Info: existing server.env found but DB_DATA_DIR is empty; generating new password."
    fi
    if [[ "$DB_HAS_DATA" == "true" ]]; then
      echo "Error: $DB_DATA_DIR contains data but no POSTGRES_PASSWORD is set."
      echo "Please set POSTGRES_PASSWORD (or restore server.env) before deploying."
      exit 1
    fi
    # Avoid SIGPIPE causing exit under pipefail when head closes the pipe.
    set +o pipefail
    POSTGRES_PASSWORD=$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)
    set -o pipefail
    echo "Step: generated POSTGRES_PASSWORD"
    echo "Generated new POSTGRES_PASSWORD for ${ENV_NAME}"
  fi
elif [[ -n "$EXISTING_PASSWORD" ]] && [[ "$POSTGRES_PASSWORD" != "$EXISTING_PASSWORD" ]]; then
  # Password provided but differs from existing - DANGER!
  echo ""
  echo "WARNING: Provided POSTGRES_PASSWORD differs from existing password in $SERVER_ENV_FILE"
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

if [[ -z "$IMAGE_NAME" ]]; then
  if [[ -n "$EXISTING_IMAGE_NAME" ]]; then
    IMAGE_NAME="$EXISTING_IMAGE_NAME"
  elif [[ -n "$GHCR_USERNAME" ]]; then
    IMAGE_NAME="ghcr.io/${GHCR_USERNAME}/lehrerlog-server"
  else
    IMAGE_NAME="ghcr.io/lehrerlog/lehrerlog-server"
  fi
fi

if [[ -z "$IMAGE_TAG" ]]; then
  if [[ -n "$EXISTING_IMAGE_TAG" ]]; then
    IMAGE_TAG="$EXISTING_IMAGE_TAG"
  else
    IMAGE_TAG="latest"
  fi
fi

if [[ -z "$WEBAPP_IMAGE_NAME" ]]; then
  if [[ -n "$EXISTING_WEBAPP_IMAGE_NAME" ]]; then
    WEBAPP_IMAGE_NAME="$EXISTING_WEBAPP_IMAGE_NAME"
  elif [[ -n "$GHCR_USERNAME" ]]; then
    WEBAPP_IMAGE_NAME="ghcr.io/${GHCR_USERNAME}/lehrerlog-webapp"
  else
    WEBAPP_IMAGE_NAME="ghcr.io/lehrerlog/lehrerlog-webapp"
  fi
fi

if [[ -z "$WEBAPP_IMAGE_TAG" ]]; then
  if [[ -n "$EXISTING_WEBAPP_IMAGE_TAG" ]]; then
    WEBAPP_IMAGE_TAG="$EXISTING_WEBAPP_IMAGE_TAG"
  else
    WEBAPP_IMAGE_TAG="latest"
  fi
fi

if [[ -z "$WEBAPP_HOST_PORT" && -n "$EXISTING_WEBAPP_HOST_PORT" ]]; then
  WEBAPP_HOST_PORT="$EXISTING_WEBAPP_HOST_PORT"
fi

SCHOOL_CATALOG_ENDPOINT="${SCHOOL_CATALOG_ENDPOINT:-https://overpass-api.de/api/interpreter}"
OBJECT_STORAGE_HEALTH_URL="${OBJECT_STORAGE_HEALTH_URL:-}"
SEED_TEST_USER_EMAIL="${SEED_TEST_USER_EMAIL:-}"
SEED_TEST_USER_PASSWORD_B64="${SEED_TEST_USER_PASSWORD_B64:-}"
SEED_TEST_USER_FIRST_NAME="${SEED_TEST_USER_FIRST_NAME:-QA}"
SEED_TEST_USER_LAST_NAME="${SEED_TEST_USER_LAST_NAME:-Verifier}"
SEED_TEST_SCHOOL_CODE="${SEED_TEST_SCHOOL_CODE:-QA-TEST}"
SEED_TEST_SCHOOL_NAME="${SEED_TEST_SCHOOL_NAME:-QA Test School}"

# Create data directories with proper ownership (only if new)
for DIR in "$DATA_DIR" "$BACKUP_DIR"; do
  if [[ ! -d "$DIR" ]]; then
    sudo "$BIN_MKDIR" -p "$DIR"
    sudo "$BIN_CHOWN" "$(id -u):$(id -g)" "$DIR"
  fi
done

if [[ -e "$GARAGE_DIR" ]] && [[ ! -d "$GARAGE_DIR" ]]; then
  sudo rm -f "$GARAGE_DIR"
fi
if [[ ! -d "$GARAGE_DIR" ]]; then
  sudo "$BIN_MKDIR" -p "$GARAGE_DIR"
  sudo "$BIN_CHOWN" "$(id -u):$(id -g)" "$GARAGE_DIR"
fi
if [[ ! -d "$GARAGE_DIR/data" ]]; then
  sudo "$BIN_MKDIR" -p "$GARAGE_DIR/data"
  sudo "$BIN_CHOWN" "$(id -u):$(id -g)" "$GARAGE_DIR/data"
fi
if [[ ! -d "$GARAGE_DIR/meta" ]]; then
  sudo "$BIN_MKDIR" -p "$GARAGE_DIR/meta"
  sudo "$BIN_CHOWN" "$(id -u):$(id -g)" "$GARAGE_DIR/meta"
fi

if [[ ! -d "$DB_DATA_DIR" ]]; then
  sudo "$BIN_MKDIR" -p "$DB_DATA_DIR"
fi
sudo "$BIN_CHOWN" -R "${POSTGRES_UID}:${POSTGRES_GID}" "$DB_DATA_DIR"
# Ensure ownership/permissions inside the mounted data dir (alpine postgres uses uid 70).
echo "Step: normalize postgres data directory permissions"
docker run --rm -v "$DB_DATA_DIR":/var/lib/postgresql/data busybox:1.36 sh -c \
  "chown -R ${POSTGRES_UID}:${POSTGRES_GID} /var/lib/postgresql/data && chmod -R 700 /var/lib/postgresql/data" >/dev/null 2>&1 || true

# Copy deployment files if available
if [[ -f "$DEPLOY_DIR/.deploy/docker-compose.yml" ]]; then
  cp "$DEPLOY_DIR/.deploy/docker-compose.yml" "$DEPLOY_DIR/docker-compose.yml"
fi

if [[ ! -d "$DEPLOY_DIR/garage" ]]; then
  mkdir -p "$DEPLOY_DIR/garage"
fi

GARAGE_CONFIG_DIR="$(dirname "$GARAGE_CONFIG_PATH")"
if [[ ! -d "$GARAGE_CONFIG_DIR" ]]; then
  sudo "$BIN_MKDIR" -p "$GARAGE_CONFIG_DIR"
  sudo "$BIN_CHOWN" "$(id -u):$(id -g)" "$GARAGE_CONFIG_DIR"
fi
if [[ -f "$DEPLOY_DIR/.deploy/garage/garage.toml" ]]; then
  cp "$DEPLOY_DIR/.deploy/garage/garage.toml" "$GARAGE_CONFIG_PATH"
fi


if [[ -z "$GARAGE_ADMIN_TOKEN" ]]; then
  set +o pipefail
  GARAGE_ADMIN_TOKEN=$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)
  set -o pipefail
fi
if [[ -z "$GARAGE_METRICS_TOKEN" ]]; then
  set +o pipefail
  GARAGE_METRICS_TOKEN=$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)
  set -o pipefail
fi
if [[ -n "$GARAGE_RPC_SECRET" ]]; then
  if [[ ! "$GARAGE_RPC_SECRET" =~ ^[0-9a-fA-F]{64}$ ]]; then
    echo "Warning: GARAGE_RPC_SECRET is invalid. Regenerating."
    GARAGE_RPC_SECRET=""
  fi
fi

if [[ -z "$GARAGE_RPC_SECRET" ]]; then
  set +o pipefail
  if command -v openssl >/dev/null 2>&1; then
    GARAGE_RPC_SECRET=$(openssl rand -hex 32)
  elif command -v xxd >/dev/null 2>&1; then
    GARAGE_RPC_SECRET=$(head -c 32 /dev/urandom | xxd -p -c 256)
  else
    GARAGE_RPC_SECRET=$(tr -dc 'a-f0-9' </dev/urandom | head -c 64)
  fi
  set -o pipefail
fi

GARAGE_RPC_SECRET="$(printf '%s' "$GARAGE_RPC_SECRET" | tr -dc 'a-fA-F0-9' | tr 'A-F' 'a-f' | head -c 64)"
if [[ ${#GARAGE_RPC_SECRET} -ne 64 ]]; then
  echo "Error: GARAGE_RPC_SECRET must be 64 hex characters."
  exit 1
fi

cat > "$GARAGE_CONFIG_PATH" <<EOF
metadata_dir = "/meta"
data_dir = "/data"
db_engine = "sqlite"

replication_factor = 1

rpc_bind_addr = "[::]:3901"
rpc_public_addr = "127.0.0.1:3901"
rpc_secret = "$GARAGE_RPC_SECRET"

[s3_api]
s3_region = "garage"
api_bind_addr = "[::]:3900"
root_domain = "s3.local"

[admin]
api_bind_addr = "[::]:3903"
admin_token = "$GARAGE_ADMIN_TOKEN"
metrics_token = "$GARAGE_METRICS_TOKEN"
EOF

rpc_line="$(grep '^rpc_secret' "$GARAGE_CONFIG_PATH" || true)"
rpc_value="${rpc_line#*= }"
rpc_value="${rpc_value#\"}"
rpc_value="${rpc_value%\"}"
rpc_len=${#rpc_value}
echo "Garage rpc_secret length: ${rpc_len}"
if [[ ! "$rpc_value" =~ ^[0-9a-fA-F]{64}$ ]]; then
  echo "Error: rpc_secret in config is invalid."
  exit 1
fi
if [[ -z "$SEED_TEST_USER_PASSWORD_B64" && -n "$SEED_TEST_USER_PASSWORD" ]]; then
  if [[ -z "$BIN_BASE64" ]]; then
    echo "Error: base64 is required to encode SEED_TEST_USER_PASSWORD."
    exit 1
  fi
  SEED_TEST_USER_PASSWORD_B64="$(printf '%s' "$SEED_TEST_USER_PASSWORD" | "$BIN_BASE64" -w0)"
fi

# Create/update server.env file with current settings
cat > "$SERVER_ENV_FILE" <<EOF
# Auto-generated by remote-deploy.sh on $(date -Iseconds)
ENV_NAME=$ENV_NAME
IMAGE_NAME=$IMAGE_NAME
IMAGE_TAG=$IMAGE_TAG
HOST_PORT=$HOST_PORT
GARAGE_IMAGE=$GARAGE_IMAGE
GARAGE_DIR=$GARAGE_DIR
GARAGE_CONFIG_PATH=$GARAGE_CONFIG_PATH
GARAGE_CONFIG_DIR=$GARAGE_CONFIG_DIR
GARAGE_API_PORT=$GARAGE_API_PORT
GARAGE_ADMIN_PORT=$GARAGE_ADMIN_PORT
GARAGE_ADMIN_TOKEN=$GARAGE_ADMIN_TOKEN
GARAGE_METRICS_TOKEN=$GARAGE_METRICS_TOKEN
GARAGE_RPC_SECRET=$GARAGE_RPC_SECRET
SCHOOL_CATALOG_ENDPOINT=$SCHOOL_CATALOG_ENDPOINT
OBJECT_STORAGE_HEALTH_URL=$OBJECT_STORAGE_HEALTH_URL
SEED_TEST_USER_EMAIL=$SEED_TEST_USER_EMAIL
SEED_TEST_USER_PASSWORD_B64=$SEED_TEST_USER_PASSWORD_B64
SEED_TEST_USER_FIRST_NAME=$SEED_TEST_USER_FIRST_NAME
SEED_TEST_USER_LAST_NAME=$SEED_TEST_USER_LAST_NAME
SEED_TEST_SCHOOL_CODE=$SEED_TEST_SCHOOL_CODE
SEED_TEST_SCHOOL_NAME=$SEED_TEST_SCHOOL_NAME
POSTGRES_DB=$POSTGRES_DB
POSTGRES_USER=$POSTGRES_USER
POSTGRES_PASSWORD=$POSTGRES_PASSWORD
POSTGRES_IMAGE=$POSTGRES_IMAGE
POSTGRES_UID=$POSTGRES_UID
POSTGRES_GID=$POSTGRES_GID
DATA_DIR=$DATA_DIR
DB_DATA_DIR=$DB_DATA_DIR
EOF

echo "Created server.env file at $SERVER_ENV_FILE"

# Create/update webapp.env file with current settings
cat > "$WEBAPP_ENV_FILE" <<EOF
# Auto-generated by remote-deploy.sh on $(date -Iseconds)
WEBAPP_IMAGE_NAME=$WEBAPP_IMAGE_NAME
WEBAPP_IMAGE_TAG=$WEBAPP_IMAGE_TAG
WEBAPP_HOST_PORT=$WEBAPP_HOST_PORT
EOF

echo "Created webapp.env file at $WEBAPP_ENV_FILE"
COMPOSE_ENV_FILES=(--env-file "$SERVER_ENV_FILE" --env-file "$WEBAPP_ENV_FILE")

# Setup nginx configuration (avoid overwriting certbot-managed SSL blocks).
setup_nginx_site() {
  local site_domain="$1"
  local upstream_port="$2"
  local template="$3"
  local nginx_site="/etc/nginx/sites-available/${site_domain}"

  if [[ ! -f "$nginx_site" ]]; then
    if [[ -f "$DEPLOY_DIR/.deploy/nginx/${site_domain}.conf" ]]; then
      sudo "$BIN_CP" "$DEPLOY_DIR/.deploy/nginx/${site_domain}.conf" "$nginx_site"
    else
      sudo "$BIN_CP" "$DEPLOY_DIR/.deploy/nginx/${template}" "$nginx_site"
      sudo "$BIN_SED" -i "s/server_name .*/server_name ${site_domain};/" "$nginx_site"
    fi
  fi

  sudo "$BIN_SED" -i "s/server_name .*/server_name ${site_domain};/" "$nginx_site"
  sudo "$BIN_SED" -i "s|proxy_pass http://127.0.0.1:[0-9]*;|proxy_pass http://127.0.0.1:${upstream_port};|g" "$nginx_site"
  sudo "$BIN_SED" -i "s|proxy_pass http://localhost:[0-9]*;|proxy_pass http://127.0.0.1:${upstream_port};|g" "$nginx_site"

  if [[ ! -e "/etc/nginx/sites-enabled/${site_domain}" ]]; then
    sudo "$BIN_LN" -s "$nginx_site" "/etc/nginx/sites-enabled/${site_domain}"
  fi
}

check_dns() {
  local site_domain="$1"
  if [[ "$SKIP_DNS_CHECK" == "true" ]]; then
    return 0
  fi
  if ! getent hosts "$site_domain" >/dev/null 2>&1; then
    echo "Error: DNS lookup failed for $site_domain. Set SKIP_DNS_CHECK=true to bypass."
    exit 1
  fi
}

run_certbot() {
  local attempt=1
  local max_attempts=3
  while true; do
    if sudo "$BIN_CERTBOT" "$@"; then
      return 0
    fi
    if [[ "$attempt" -ge "$max_attempts" ]]; then
      return 1
    fi
    echo "Certbot failed (attempt $attempt/$max_attempts). Retrying in 10s..."
    sleep 10
    attempt=$((attempt + 1))
  done
}

ensure_ssl() {
  local site_domain="$1"
  local nginx_site="/etc/nginx/sites-available/${site_domain}"
  local cert_exists=false
  local ssl_configured=false
  if sudo "$BIN_CERTBOT" certificates -d "$site_domain" 2>/dev/null | grep -q "Certificate Name"; then
    cert_exists=true
  fi
  if [[ -r "$nginx_site" ]] && grep -q "ssl_certificate" "$nginx_site"; then
    ssl_configured=true
  fi
  if [[ "$ssl_configured" == "false" ]]; then
    echo "Configuring HTTPS for ${site_domain}..."
    if [[ "$cert_exists" == "true" ]]; then
      run_certbot --nginx -d "$site_domain" --non-interactive --agree-tos -m "$LETSENCRYPT_EMAIL" --reinstall
    else
      echo "Obtaining SSL certificate for ${site_domain}..."
      run_certbot --nginx -d "$site_domain" --non-interactive --agree-tos -m "$LETSENCRYPT_EMAIL"
    fi
  fi
}

WEBAPP_TEMPLATE="app.lehrerlog.de.conf"
if [[ "$ENV_NAME" == "qa" ]]; then
  WEBAPP_TEMPLATE="app.qa.lehrerlog.de.conf"
fi

setup_nginx_site "$DOMAIN" "$HOST_PORT" "api.lehrerlog.de.conf"
setup_nginx_site "$WEBAPP_DOMAIN" "$WEBAPP_HOST_PORT" "$WEBAPP_TEMPLATE"

# Install certbot if needed
if [[ -z "$BIN_CERTBOT" ]]; then
  require_cmd "apt-get" "$BIN_APT_GET"
  sudo "$BIN_APT_GET" update
  sudo "$BIN_APT_GET" install -y certbot python3-certbot-nginx
  BIN_CERTBOT="$(command -v certbot || true)"
  require_cmd "certbot" "$BIN_CERTBOT"
fi

sudo "$BIN_NGINX" -t
sudo "$BIN_SYSTEMCTL" reload nginx

check_dns "$DOMAIN"
check_dns "$WEBAPP_DOMAIN"

ensure_ssl "$DOMAIN"
ensure_ssl "$WEBAPP_DOMAIN"
sudo "$BIN_SYSTEMCTL" reload nginx

# Docker login to GHCR
if [[ -n "$GHCR_USERNAME" ]] && [[ -n "$GHCR_TOKEN" ]]; then
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USERNAME" --password-stdin
fi

# Deploy the webapp container (used for both full and webapp-only deployments).
deploy_webapp() {
  local port="$1"
  local skip_pull="${2:-false}"

  echo ""
  echo "=== Deploying webapp (${ENV_NAME}) ==="
  if [[ "$skip_pull" != "true" ]]; then
    docker compose "${COMPOSE_ENV_FILES[@]}" pull lehrerlog-webapp
  fi
  docker compose "${COMPOSE_ENV_FILES[@]}" up -d lehrerlog-webapp

  echo "Waiting for webapp health (port ${port})..."
  local attempt=0
  local max_attempts=30
  while [[ $attempt -lt $max_attempts ]]; do
    if curl -fsS "http://localhost:${port}/health" >/dev/null 2>&1; then
      local version
      version="$(curl -fsS "http://localhost:${port}/version" 2>/dev/null || true)"
      echo "Webapp is healthy on port ${port} (${version:-unknown})"
      return 0
    fi
    attempt=$((attempt + 1))
    sleep 1
  done

  echo "Error: Webapp health check failed after ${max_attempts} attempts"
  docker logs "lehrerlog-${ENV_NAME}-webapp" --tail 20 || true
  return 1
}

# Deploy with docker compose
cd "$DEPLOY_DIR"
echo "Pulling latest images..."
if [[ "$DEPLOY_WEBAPP_ONLY" == "true" ]]; then
  docker compose "${COMPOSE_ENV_FILES[@]}" pull lehrerlog-webapp
  deploy_webapp "$WEBAPP_HOST_PORT"
  exit $?
elif [[ "$DEPLOY_SERVER_ONLY" == "true" ]]; then
  docker compose "${COMPOSE_ENV_FILES[@]}" pull lehrerlog-server db garage
else
  docker compose "${COMPOSE_ENV_FILES[@]}" pull
fi

if [[ "$DEPLOY_WEBAPP_ONLY" == "true" ]]; then
  exit 0
fi

echo "Starting services..."
if [[ "$DEPLOY_WEBAPP_ONLY" == "true" ]]; then
  docker compose "${COMPOSE_ENV_FILES[@]}" up -d lehrerlog-webapp
elif [[ "$DEPLOY_SERVER_ONLY" == "true" ]]; then
  docker compose "${COMPOSE_ENV_FILES[@]}" up -d lehrerlog-server db garage
else
  docker compose "${COMPOSE_ENV_FILES[@]}" up -d --remove-orphans
fi

if [[ "$DEPLOY_WEBAPP_ONLY" != "true" ]]; then
  docker compose "${COMPOSE_ENV_FILES[@]}" up -d --force-recreate garage
fi

init_garage_layout() {
  local garage_container="lehrerlog-${ENV_NAME}-garage"
  local status=""
  local node_id=""
  local zone="${GARAGE_ZONE:-local}"
  local capacity="${GARAGE_CAPACITY:-10GB}"

  if ! docker inspect "$garage_container" >/dev/null 2>&1; then
    return 0
  fi

  for attempt in $(seq 1 24); do
    status="$(docker inspect --format '{{.State.Status}}' "$garage_container" 2>/dev/null || true)"
    if [[ "$status" == "running" ]]; then
      break
    fi
    sleep 2
  done

  if [[ "$status" != "running" ]]; then
    echo "Error: ${garage_container} is not running (status: ${status})."
    docker logs "$garage_container" --tail 200 || true
    return 1
  fi

  for attempt in $(seq 1 24); do
    full_node_id="$(docker exec -e GARAGE_CONFIG_FILE=/etc/garage.toml "$garage_container" /garage node id 2>/dev/null | \
      awk '{print $NF}' | tr -d '\r')"
    node_id="${full_node_id%@*}"
    if [[ -n "$full_node_id" && -n "$node_id" ]]; then
      break
    fi
    sleep 2
  done

  if [[ -z "$full_node_id" || -z "$node_id" ]]; then
    echo "Error: Unable to determine Garage node ID."
    docker logs "$garage_container" --tail 200 || true
    return 1
  fi

  layout_output="$(docker exec -e GARAGE_CONFIG_FILE=/etc/garage.toml -e GARAGE_RPC_HOST="$full_node_id" "$garage_container" /garage layout show 2>/dev/null || true)"
  current_version="$(printf '%s\n' "$layout_output" | sed -n -E 's/.*Current cluster layout version: ([0-9]+).*/\\1/p' | head -n 1)"
  if [[ -z "$current_version" ]]; then
    current_version=0
  fi
  target_version=$((current_version + 1))

  current_layout="$(printf '%s\n' "$layout_output" | awk '/==== CURRENT CLUSTER LAYOUT ====/{flag=1} /==== STAGED ROLE CHANGES ====/{flag=0} flag')"
  needs_apply=false
  if [[ "$current_version" -eq 0 ]]; then
    needs_apply=true
  elif ! printf '%s\n' "$current_layout" | grep -q "$node_id"; then
    needs_apply=true
  fi

  if [[ "$needs_apply" == "true" ]]; then
    echo "Initializing Garage layout for ${garage_container}..."
    docker exec -e GARAGE_CONFIG_FILE=/etc/garage.toml -e GARAGE_RPC_HOST="$full_node_id" "$garage_container" /garage layout assign -z "$zone" -c "$capacity" "$node_id"
    docker exec -e GARAGE_CONFIG_FILE=/etc/garage.toml -e GARAGE_RPC_HOST="$full_node_id" "$garage_container" /garage layout apply --version "$target_version"
  fi
}

if [[ "$DEPLOY_WEBAPP_ONLY" != "true" ]]; then
  init_garage_layout || exit 1
fi

# Clean up old Docker images to prevent disk bloat
echo ""
echo "Cleaning up old Docker images..."
# Remove dangling images
docker image prune -f || true
if [[ "$DEPLOY_WEBAPP_ONLY" != "true" ]]; then
  # Remove old lehrerlog-server images (keep only current)
  CURRENT_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
  docker images "${IMAGE_NAME}" --format "{{.Repository}}:{{.Tag}}" | \
    grep -v "^${CURRENT_IMAGE}$" | \
    grep -v "<none>" | \
    xargs -r docker rmi || true
fi

# Wait for health check
echo ""
echo "Waiting for services to be healthy..."
sleep 5
docker compose "${COMPOSE_ENV_FILES[@]}" ps

# Wait for server container health when deploying API
if [[ "$DEPLOY_WEBAPP_ONLY" != "true" ]]; then
  SERVER_CONTAINER="lehrerlog-${ENV_NAME}-server"
  echo "Waiting for ${SERVER_CONTAINER} to report healthy..."
  SERVER_HEALTH=""
  for attempt in $(seq 1 24); do
    SERVER_HEALTH="$(docker inspect --format '{{.State.Health.Status}}' "$SERVER_CONTAINER" 2>/dev/null || true)"
    if [[ "$SERVER_HEALTH" == "healthy" ]]; then
      break
    fi
    if [[ "$SERVER_HEALTH" == "unhealthy" ]]; then
      echo "Error: ${SERVER_CONTAINER} reported unhealthy."
      docker logs "$SERVER_CONTAINER" --tail 200 || true
      exit 1
    fi
    sleep 5
  done

  if [[ "$SERVER_HEALTH" != "healthy" ]]; then
    echo "Error: ${SERVER_CONTAINER} did not become healthy in time."
    docker logs "$SERVER_CONTAINER" --tail 200 || true
    exit 1
  fi
fi

if [[ "$DEPLOY_WEBAPP_ONLY" != "true" ]]; then
  echo "Waiting for Garage to respond on port ${GARAGE_ADMIN_PORT}..."
  if [[ -z "$GARAGE_ADMIN_TOKEN" ]]; then
    echo "Error: GARAGE_ADMIN_TOKEN is required to verify Garage health."
    exit 1
  fi
  GARAGE_READY=false
  for attempt in $(seq 1 30); do
    status_code="$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${GARAGE_ADMIN_PORT}/health" || true)"
    if [[ "$status_code" == "200" ]]; then
      GARAGE_READY=true
      break
    fi
    echo "Garage /health returned ${status_code}; checking cluster health..."
    cluster_body="$(curl -s -H "Authorization: Bearer $GARAGE_ADMIN_TOKEN" \
      "http://127.0.0.1:${GARAGE_ADMIN_PORT}/v2/GetClusterHealth" || true)"
    echo "$cluster_body"

    if [[ "$status_code" == "503" ]]; then
      layout_output="$(docker exec -e GARAGE_CONFIG_FILE=/etc/garage.toml -e GARAGE_RPC_HOST="$full_node_id" "lehrerlog-${ENV_NAME}-garage" /garage layout show 2>/dev/null || true)"
      current_version="$(printf '%s\n' "$layout_output" | sed -n -E 's/.*Current cluster layout version: ([0-9]+).*/\\1/p' | head -n 1)"
      if [[ -z "$current_version" ]]; then
        current_version=0
      fi
      staged_section="$(printf '%s\n' "$layout_output" | awk '/==== STAGED ROLE CHANGES ====/{flag=1;next} /==== NEW CLUSTER LAYOUT/{flag=0} flag')"
      staged_has_changes="$(printf '%s\n' "$staged_section" | grep -v '^[[:space:]]*$' || true)"
      if [[ "$current_version" -eq 0 || -n "$staged_has_changes" ]]; then
        next_version=$((current_version + 1))
        echo "Re-applying Garage layout (version ${next_version})..."
        docker exec -e GARAGE_CONFIG_FILE=/etc/garage.toml -e GARAGE_RPC_HOST="$full_node_id" "lehrerlog-${ENV_NAME}-garage" /garage layout apply --version "$next_version" || true
      fi
    fi
    sleep 2
  done
  if [[ "$GARAGE_READY" != "true" ]]; then
    echo "Error: Garage did not become healthy."
    docker logs "lehrerlog-${ENV_NAME}-garage" --tail 200 || true
    exit 1
  fi
  echo "Garage is healthy on port ${GARAGE_ADMIN_PORT}."
fi

if [[ "$DEPLOY_SERVER_ONLY" != "true" ]]; then
  deploy_webapp "$WEBAPP_HOST_PORT" "true" || exit 1
fi

# Optional post-deploy verification
if [[ "$DEPLOY_WEBAPP_ONLY" != "true" ]]; then
  if [[ -f "$DEPLOY_DIR/.deploy/verify.sh" ]]; then
    cp "$DEPLOY_DIR/.deploy/verify.sh" "$DEPLOY_DIR/verify.sh"
    chmod +x "$DEPLOY_DIR/verify.sh"
    echo ""
    echo "Running post-deploy verification..."
    DOMAIN="$DOMAIN" \
      VERIFY_USER_EMAIL="$VERIFY_USER_EMAIL" \
      VERIFY_USER_PASSWORD="$VERIFY_USER_PASSWORD" \
      "$DEPLOY_DIR/verify.sh" || {
        echo "Post-deploy verification failed. Recent server logs:"
        docker logs "lehrerlog-${ENV_NAME}-server" --tail 200 || true
        exit 1
      }
  fi
else
  echo ""
  echo "Running webapp health check..."
  if ! curl -fsS "http://localhost:${WEBAPP_HOST_PORT}/health" | grep -q '"status"[[:space:]]*:[[:space:]]*"ok"'; then
    echo "Warning: webapp health check failed."
  fi
fi

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

  # Create cron job that runs daily at 3 AM (stagger QA at 3:30)
  if [[ "$ENV_NAME" == "qa" ]]; then
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
