#!/usr/bin/env bash
set -euo pipefail

DOMAIN="${DOMAIN:-lehrerlog.9d4.de}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/docker/lehrerlog}"
IMAGE_NAME="${IMAGE_NAME:-ghcr.io/${REPO_OWNER:-lehrerlog}/lehrerlog-server}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
HOST_PORT="${HOST_PORT:-8080}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-}"
GHCR_USERNAME="${GHCR_USERNAME:-}"
GHCR_TOKEN="${GHCR_TOKEN:-}"
SKIP_DNS_CHECK="${SKIP_DNS_CHECK:-false}"

if [[ -z "$LETSENCRYPT_EMAIL" ]]; then
  echo "LETSENCRYPT_EMAIL is required."
  exit 1
fi

mkdir -p "$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR/data"
mkdir -p "$DEPLOY_DIR/.deploy"

if [[ -f "$DEPLOY_DIR/.deploy/docker-compose.yml" ]]; then
  cp "$DEPLOY_DIR/.deploy/docker-compose.yml" "$DEPLOY_DIR/docker-compose.yml"
fi

if [[ -f "$DEPLOY_DIR/.deploy/app.env.example" ]] && [[ ! -f "$DEPLOY_DIR/app.env" ]]; then
  cp "$DEPLOY_DIR/.deploy/app.env.example" "$DEPLOY_DIR/app.env"
fi

touch "$DEPLOY_DIR/.env"

update_env_var() {
  local key="$1"
  local value="$2"
  if grep -qE "^${key}=" "$DEPLOY_DIR/.env"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$DEPLOY_DIR/.env"
  else
    echo "${key}=${value}" >> "$DEPLOY_DIR/.env"
  fi
}

update_env_var "IMAGE_NAME" "$IMAGE_NAME"
update_env_var "IMAGE_TAG" "$IMAGE_TAG"
update_env_var "HOST_PORT" "$HOST_PORT"

if [[ -f "$DEPLOY_DIR/.deploy/nginx/$DOMAIN.conf" ]]; then
  sudo cp "$DEPLOY_DIR/.deploy/nginx/$DOMAIN.conf" "/etc/nginx/sites-available/$DOMAIN"
else
  sudo cp "$DEPLOY_DIR/.deploy/nginx/lehrerlog.9d4.de.conf" "/etc/nginx/sites-available/$DOMAIN"
  sudo sed -i "s/server_name lehrerlog.9d4.de;/server_name $DOMAIN;/" "/etc/nginx/sites-available/$DOMAIN"
fi

if [[ ! -f "/etc/nginx/sites-enabled/$DOMAIN" ]]; then
  sudo ln -s "/etc/nginx/sites-available/$DOMAIN" "/etc/nginx/sites-enabled/$DOMAIN"
fi

if ! command -v certbot >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y certbot python3-certbot-nginx
fi

sudo nginx -t
sudo systemctl reload nginx

if [[ "$SKIP_DNS_CHECK" != "true" ]]; then
  if ! getent hosts "$DOMAIN" >/dev/null 2>&1; then
    echo "DNS lookup failed for $DOMAIN. Set SKIP_DNS_CHECK=true to bypass."
    exit 1
  fi
fi

if ! sudo certbot certificates -d "$DOMAIN" >/dev/null 2>&1; then
  sudo certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos -m "$LETSENCRYPT_EMAIL"
  sudo systemctl reload nginx
fi

if [[ -n "$GHCR_USERNAME" ]] && [[ -n "$GHCR_TOKEN" ]]; then
  echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USERNAME" --password-stdin
fi

cd "$DEPLOY_DIR"
docker compose pull
docker compose up -d
