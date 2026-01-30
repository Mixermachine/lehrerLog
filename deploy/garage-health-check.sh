#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <qa|prod>" >&2
}

ENV_NAME="${1:-}"
if [[ -z "$ENV_NAME" ]]; then
  usage
  exit 1
fi
if [[ "$ENV_NAME" != "qa" && "$ENV_NAME" != "prod" ]]; then
  echo "Invalid environment: $ENV_NAME" >&2
  usage
  exit 1
fi

BASE_DIR="${GARAGE_BASE_DIR:-/home/aaron/docker}"
if [[ "$ENV_NAME" == "qa" ]]; then
  ENV_DIR="$BASE_DIR/lehrerlog-qa"
  DEFAULT_ADMIN_PORT=3913
else
  ENV_DIR="$BASE_DIR/lehrerlog"
  DEFAULT_ADMIN_PORT=3903
fi

ENV_FILE="${GARAGE_ENV_FILE:-$ENV_DIR/.env}"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

GARAGE_ADMIN_PORT="${GARAGE_ADMIN_PORT:-$DEFAULT_ADMIN_PORT}"
if [[ -z "${GARAGE_ADMIN_TOKEN:-}" ]]; then
  echo "GARAGE_ADMIN_TOKEN is not set in $ENV_FILE" >&2
  exit 1
fi

HEALTH_URL="http://127.0.0.1:${GARAGE_ADMIN_PORT}/health"
CLUSTER_URL="http://127.0.0.1:${GARAGE_ADMIN_PORT}/v2/GetClusterHealth"

status_code="$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" || true)"
echo "Garage /health HTTP status: $status_code"

cluster_body="$(curl -s -H "Authorization: Bearer $GARAGE_ADMIN_TOKEN" "$CLUSTER_URL" || true)"
echo "$cluster_body"

if [[ "$status_code" != "200" ]]; then
  exit 1
fi

