# Web App Hosting Plan

## Goal
Host the Kotlin/Wasm web application at:
- **Production:** `https://app.lehrerlog.de` → connects to `https://api.lehrerlog.de`
- **QA:** `https://app.qa.lehrerlog.de` → connects to `https://api.qa.lehrerlog.de`

---

## Current State Analysis

### Build Process
The wasmJS target is built via Gradle:
```bash
./gradlew :composeApp:wasmJsBrowserDistribution -PserverUrl=https://api.lehrerlog.de
```

**Key characteristics:**
- The `serverUrl` is **baked into the build** at compile time via `GenerateServerConfig` task
- This means QA and PROD builds are **separate artifacts** with different API endpoints
- Build output location: `composeApp/build/dist/wasmJs/productionExecutable/`

### Build Output Structure
```
composeApp/build/dist/wasmJs/productionExecutable/
├── index.html              # Entry point
├── composeApp.js           # Main JS loader
├── composeApp.wasm         # WebAssembly binary
├── styles.css              # Custom styles
├── manifest.json           # PWA manifest
├── service-worker.js       # Offline support
├── icon-192x192.png        # PWA icons
├── icon-512x512.png
└── [sql.js worker files]   # SQLDelight web worker dependencies
```

### CI/CD Status
The `composeapp-build.yml` workflow already:
1. Builds QA artifacts on every push to `master`
2. Builds PROD artifacts on git tags
3. Uploads `wasm-js-dist-qa` and `wasm-js-dist-prod-{tag}` as GitHub Actions artifacts

**Gap:** Artifacts are built but not deployed anywhere.

### PWA Configuration
- `manifest.json` defines app name, icons, theme colors
- `service-worker.js` provides basic offline fallback
- Current `start_url` is `/` (relative, works for any domain)

---

## Architecture: Docker Container Approach

The web app will be packaged in a Docker container using nginx, consistent with the backend deployment model.

```
                    ┌─────────────────────────────────────────────┐
                    │               VPS (Your Server)             │
                    ├─────────────────────────────────────────────┤
                    │  nginx (host) + Jitsi Meet                  │
                    │  ┌─────────────────────────────────────────┐│
                    │  │ app.lehrerlog.de:443                    ││
                    │  │   → localhost:18082 (Docker webapp)     ││
                    │  ├─────────────────────────────────────────┤│
                    │  │ app.qa.lehrerlog.de:443                 ││
                    │  │   → localhost:18083 (Docker webapp)     ││
                    │  ├─────────────────────────────────────────┤│
                    │  │ api.lehrerlog.de:443                    ││
                    │  │   → localhost:18080 (Docker server)     ││
                    │  ├─────────────────────────────────────────┤│
                    │  │ api.qa.lehrerlog.de:443                 ││
                    │  │   → localhost:18081 (Docker server)     ││
                    │  └─────────────────────────────────────────┘│
                    │                                             │
                    │  Docker Compose (PROD)                      │
                    │  ┌─────────────────────────────────────────┐│
                    │  │ Network: lehrerlog-prod-net             ││
                    │  │   lehrerlog-prod-server:18080           ││
                    │  │   lehrerlog-prod-webapp:18082           ││
                    │  │   lehrerlog-prod-db (internal)          ││
                    │  └─────────────────────────────────────────┘│
                    │                                             │
                    │  Docker Compose (QA)                        │
                    │  ┌─────────────────────────────────────────┐│
                    │  │ Network: lehrerlog-qa-net               ││
                    │  │   lehrerlog-qa-server:18081             ││
                    │  │   lehrerlog-qa-webapp:18083             ││
                    │  │   lehrerlog-qa-db (internal)            ││
                    │  └─────────────────────────────────────────┘│
                    └─────────────────────────────────────────────┘
```

**Benefits:**
- Consistent deployment model (everything in Docker)
- Health checks for monitoring
- Easy rollback via Docker image tags
- Same deployment scripts for server and webapp
- Version tracking via image tags
- Isolated environments

---

## Implementation Plan

### Phase 0: CORS Configuration (CRITICAL - Must Be Done First)

**Status:** CORS is **NOT** currently configured in the Ktor server.

The web app at `app.lehrerlog.de` needs to make API calls to `api.lehrerlog.de`. Since these are different subdomains, the browser enforces CORS (Cross-Origin Resource Sharing). Without CORS headers, all API requests from the web app will fail.

#### File to Modify
`server/src/main/kotlin/de/aarondietz/lehrerlog/Application.kt`

#### Required Import
Add this import at the top of the file:
```kotlin
import io.ktor.server.plugins.cors.routing.*
```

#### Code to Add
Insert after the `ContentNegotiation` plugin installation (around line 71):

```kotlin
install(CORS) {
    // Production web app
    allowHost("app.lehrerlog.de", schemes = listOf("https"))

    // QA web app
    allowHost("app.qa.lehrerlog.de", schemes = listOf("https"))

    // Local development (various ports used by different dev servers)
    allowHost("localhost:8080", schemes = listOf("http"))
    allowHost("localhost:8081", schemes = listOf("http"))  // wasmJsBrowserDevelopmentRun
    allowHost("127.0.0.1:8080", schemes = listOf("http"))
    allowHost("127.0.0.1:8081", schemes = listOf("http"))

    // Allow credentials (for Authorization header with JWT)
    allowCredentials = true

    // Allow non-simple content types (required for application/json requests)
    allowNonSimpleContentTypes = true

    // Allowed headers
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.Accept)

    // Allowed HTTP methods
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
    allowMethod(HttpMethod.Patch)
    allowMethod(HttpMethod.Options)

    // Max age for preflight cache (1 hour)
    maxAgeInSeconds = 3600
}
```

#### Verification Steps
After deploying the CORS configuration:

1. **Test preflight request:**
   ```bash
   curl -I -X OPTIONS https://api.lehrerlog.de/health \
     -H "Origin: https://app.lehrerlog.de" \
     -H "Access-Control-Request-Method: GET"
   ```

   Expected response headers:
   ```
   Access-Control-Allow-Origin: https://app.lehrerlog.de
   Access-Control-Allow-Credentials: true
   Access-Control-Allow-Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS
   ```

2. **Test actual request:**
   ```bash
   curl -I https://api.lehrerlog.de/health \
     -H "Origin: https://app.lehrerlog.de"
   ```

#### Common CORS Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| `No 'Access-Control-Allow-Origin' header` | CORS not installed or origin not allowed | Add origin to allowHost() |
| `Credentials not supported` | allowCredentials = false | Set allowCredentials = true |
| `Method not allowed` | Missing allowMethod() | Add the HTTP method |
| `Header not allowed` | Missing allowHeader() | Add the header name |
| Preflight fails but GET works | OPTIONS method not allowed | Add allowMethod(HttpMethod.Options) |

---

### Phase 1: Webapp Dockerfile

**File: `composeApp/Dockerfile`**
```dockerfile
# Stage 1: Build the wasmJS distribution
FROM gradle:8.7-jdk17 AS build

WORKDIR /workspace

# Copy gradle files first for better caching
COPY gradle gradle
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY shared/build.gradle.kts shared/
COPY composeApp/build.gradle.kts composeApp/

# Ensure gradlew is executable (may lose exec bit when copied)
RUN chmod +x gradlew

# Download dependencies (cacheable layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY shared/src shared/src
COPY composeApp/src composeApp/src

# Build argument for server URL (baked into the build)
ARG SERVER_URL=https://api.lehrerlog.de

# Build the wasmJS distribution
RUN ./gradlew :composeApp:wasmJsBrowserDistribution -PserverUrl=${SERVER_URL} --no-daemon

# Stage 2: Serve with nginx
FROM nginx:alpine

# Copy the built webapp
COPY --from=build /workspace/composeApp/build/dist/wasmJs/productionExecutable/ /usr/share/nginx/html/

# Copy nginx configuration
COPY composeApp/nginx.conf /etc/nginx/conf.d/default.conf

# Add version file for tracking deployments
ARG BUILD_VERSION=dev
RUN echo "${BUILD_VERSION}" > /usr/share/nginx/html/version.txt

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget -q --spider http://localhost/health || exit 1
```

---

### Phase 2: Nginx Configuration for Webapp Container

**File: `composeApp/nginx.conf`**
```nginx
server {
    listen 80;
    server_name localhost;

    root /usr/share/nginx/html;
    index index.html;

    # Health endpoint for Docker health checks and monitoring
    location /health {
        access_log off;
        default_type application/json;
        return 200 '{"status":"ok","service":"lehrerlog-webapp"}';
    }

    # Version endpoint (returns build version)
    location /version {
        access_log off;
        default_type text/plain;
        alias /usr/share/nginx/html/version.txt;
    }

    # Serve static files with SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Cache static assets aggressively
    location ~* \.(js|wasm|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # PWA manifest - moderate caching
    location = /manifest.json {
        expires 1d;
        add_header Cache-Control "public";
    }

    # Service worker - no caching (must always be fresh)
    location = /service-worker.js {
        expires -1;
        add_header Cache-Control "no-store, no-cache, must-revalidate";
    }

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # Gzip compression
    gzip on;
    gzip_vary on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/wasm;
    gzip_min_length 1000;
}
```

---

### Phase 3: Update Docker Compose

**File: `deploy/docker-compose.yml`** (updated)
```yaml
services:
  lehrerlog-server:
    container_name: lehrerlog-${ENV_NAME:-prod}-server
    image: ${IMAGE_NAME}:${IMAGE_TAG}
    restart: unless-stopped
    env_file:
      - ./server.env
    environment:
      - SCHOOL_CATALOG_PATH=/app/data/schools.json
      - DB_MODE=postgres
      - DATABASE_URL=jdbc:postgresql://db:5432/${POSTGRES_DB:-lehrerlog}
      - DATABASE_USER=${POSTGRES_USER:-lehrerlog}
      - DATABASE_PASSWORD=${POSTGRES_PASSWORD:?Database password required}
    volumes:
      - ${DATA_DIR:-./data}:/app/data
    ports:
      - "127.0.0.1:${HOST_PORT:-8080}:8080"
    depends_on:
      db:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:8080/health || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - lehrerlog-net

  lehrerlog-webapp:
    container_name: lehrerlog-${ENV_NAME:-prod}-webapp
    image: ${WEBAPP_IMAGE_NAME:-ghcr.io/your-org/lehrerlog-webapp}:${WEBAPP_IMAGE_TAG:-latest}
    restart: unless-stopped
    ports:
      - "127.0.0.1:${WEBAPP_HOST_PORT:-8082}:80"
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 5s
    networks:
      - lehrerlog-net

  db:
    container_name: lehrerlog-${ENV_NAME:-prod}-db
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      - POSTGRES_DB=${POSTGRES_DB:-lehrerlog}
      - POSTGRES_USER=${POSTGRES_USER:-lehrerlog}
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD:?Database password required}
    volumes:
      - ${DB_DATA_DIR:-./pgdata}:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-lehrerlog} -d ${POSTGRES_DB:-lehrerlog}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
    networks:
      - lehrerlog-net
    # Database is not exposed to host - only accessible within docker network

networks:
  lehrerlog-net:
    name: lehrerlog-${ENV_NAME:-prod}-net
    driver: bridge
```

**Important:** The network uses a fixed YAML key (`lehrerlog-net`) but the `name:` property is environment-specific (`lehrerlog-prod-net` or `lehrerlog-qa-net`). Docker Compose only interpolates variables in values, not YAML keys, so we use this pattern to ensure complete isolation between production and QA deployments on the same host.

---

### Phase 4: Environment Configuration Updates

**Update `deploy/.env.example`**
```bash
# Environment name (qa or prod)
ENV_NAME=prod

# === Server Configuration ===
IMAGE_NAME=ghcr.io/your-org/lehrerlog-server
IMAGE_TAG=latest
HOST_PORT=18080

# === Webapp Configuration ===
WEBAPP_IMAGE_NAME=ghcr.io/your-org/lehrerlog-webapp
WEBAPP_IMAGE_TAG=latest
WEBAPP_HOST_PORT=18082

# === Database Configuration ===
POSTGRES_DB=lehrerlog
POSTGRES_USER=lehrerlog
POSTGRES_PASSWORD=

# === Data Directories ===
DATA_DIR=$HOME/docker/lehrerlog-prod/data
DB_DATA_DIR=$HOME/docker/lehrerlog-prod/pgdata
```

**QA environment differences:**
```bash
ENV_NAME=qa
HOST_PORT=18081
WEBAPP_HOST_PORT=18083
DATA_DIR=$HOME/docker/lehrerlog-qa/data
DB_DATA_DIR=$HOME/docker/lehrerlog-qa/pgdata
```

---

### Phase 5: Host Nginx Configuration

**File: `deploy/nginx/app.lehrerlog.de.conf`**
```nginx
server {
    listen 80;
    server_name app.lehrerlog.de;

    location / {
        proxy_pass http://127.0.0.1:18082;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket support (for potential future use)
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

**File: `deploy/nginx/app.qa.lehrerlog.de.conf`**
```nginx
server {
    listen 80;
    server_name app.qa.lehrerlog.de;

    location / {
        proxy_pass http://127.0.0.1:18083;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

**HTTPS Setup:** This must be fully automated in `deploy/remote-deploy.sh`.
- The deploy script must create the nginx configs for `app.lehrerlog.de` and `app.qa.lehrerlog.de`.
- It must obtain/renew certs via certbot (`--nginx`) and reload nginx.
- The script must be idempotent (safe to run repeatedly).

---

### Phase 6: GitHub Actions Workflow for Webapp

**File: `.github/workflows/webapp-build-deploy.yml`**
```yaml
name: Webapp Build and Deploy

on:
  push:
    branches: ["master"]
    tags: ["*"]
  workflow_dispatch:
    inputs:
      environment:
        description: "Environment to deploy"
        type: choice
        options:
          - qa
          - prod
          - both
        default: qa

env:
  REGISTRY: ghcr.io

jobs:
  build-qa:
    if: github.ref == 'refs/heads/master' || github.event_name == 'workflow_dispatch'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    env:
      SERVER_URL: https://api.qa.lehrerlog.de
    outputs:
      image_tag: qa-${{ steps.short_sha.outputs.sha }}
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Get short SHA
        id: short_sha
        run: echo "sha=$(echo ${{ github.sha }} | cut -c1-7)" >> $GITHUB_OUTPUT

      - name: Set lowercase image name
        id: image_name
        run: echo "name=$(echo '${{ github.repository }}-webapp' | tr '[:upper:]' '[:lower:]')" >> $GITHUB_OUTPUT

      - name: Log in to Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ steps.image_name.outputs.name }}
          tags: |
            type=sha,prefix=qa-

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          file: composeApp/Dockerfile
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          build-args: |
            SERVER_URL=${{ env.SERVER_URL }}
            BUILD_VERSION=${{ github.sha }}

  build-prod:
    if: startsWith(github.ref, 'refs/tags/') || (github.event_name == 'workflow_dispatch' && inputs.environment != 'qa')
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    env:
      SERVER_URL: https://api.lehrerlog.de
    outputs:
      image_tag: ${{ steps.meta.outputs.version }}
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set lowercase image name
        id: image_name
        run: echo "name=$(echo '${{ github.repository }}-webapp' | tr '[:upper:]' '[:lower:]')" >> $GITHUB_OUTPUT

      - name: Log in to Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ steps.image_name.outputs.name }}
          tags: |
            type=semver,pattern={{version}}

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          file: composeApp/Dockerfile
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          build-args: |
            SERVER_URL=${{ env.SERVER_URL }}
            BUILD_VERSION=${{ github.ref_name }}

  deploy-qa:
    needs: build-qa
    if: github.ref == 'refs/heads/master' || (github.event_name == 'workflow_dispatch' && inputs.environment != 'prod')
    runs-on: ubuntu-latest
    environment: qa
    steps:
      - name: Deploy to QA
        uses: appleboy/ssh-action@v1.0.3
        env:
          WEBAPP_TAG: ${{ needs.build-qa.outputs.image_tag }}
        with:
          host: ${{ vars.DEPLOY_HOST }}
          username: ${{ vars.DEPLOY_USER }}
          key: ${{ secrets.DEPLOY_SSH_KEY }}
          envs: WEBAPP_TAG
          script: |
            cd $HOME/docker/lehrerlog-qa

            # Update .env with the exact tag that was just built
            sed -i "s/^WEBAPP_IMAGE_TAG=.*/WEBAPP_IMAGE_TAG=$WEBAPP_TAG/" .env

            # Pull new webapp image
            docker compose pull lehrerlog-webapp

            # Restart webapp container
            docker compose up -d lehrerlog-webapp

            # Wait for health check
            sleep 5
            curl -fsS http://localhost:18083/health || echo "Warning: Health check failed"

            # Cleanup old images
            docker image prune -f

  deploy-prod:
    needs: build-prod
    if: startsWith(github.ref, 'refs/tags/') || (github.event_name == 'workflow_dispatch' && inputs.environment != 'qa')
    runs-on: ubuntu-latest
    environment: production
    steps:
      - name: Deploy to Production
        uses: appleboy/ssh-action@v1.0.3
        env:
          WEBAPP_TAG: ${{ needs.build-prod.outputs.image_tag }}
        with:
          host: ${{ vars.DEPLOY_HOST }}
          username: ${{ vars.DEPLOY_USER }}
          key: ${{ secrets.DEPLOY_SSH_KEY }}
          envs: WEBAPP_TAG
          script: |
            cd $HOME/docker/lehrerlog-prod

            # Update .env with the exact tag that was just built
            sed -i "s/^WEBAPP_IMAGE_TAG=.*/WEBAPP_IMAGE_TAG=$WEBAPP_TAG/" .env

            # Pull new webapp image
            docker compose pull lehrerlog-webapp

            # Restart webapp container
            docker compose up -d lehrerlog-webapp

            # Wait for health check
            sleep 5
            curl -fsS http://localhost:18082/health || echo "Warning: Health check failed"

            # Cleanup old images
            docker image prune -f
```

---

### Phase 7: Update Deployment Script

Add webapp deployment to `deploy/remote-deploy.sh`:

```bash
# === Webapp Deployment ===
# Note: DEPLOY_DIR is already the full path (e.g., $HOME/docker/lehrerlog-qa)
# This function should be called after cd "$DEPLOY_DIR" in the main deploy flow
deploy_webapp() {
    local webapp_port="$1"

    echo "=== Deploying Webapp ($ENV_NAME) ==="

    # Already in $DEPLOY_DIR from main script

    # Pull new webapp image
    docker compose pull lehrerlog-webapp

    # Start/restart webapp container
    docker compose up -d lehrerlog-webapp

    # Wait for health
    echo "Waiting for webapp health check..."
    local max_attempts=30
    local attempt=0
    while [ $attempt -lt $max_attempts ]; do
        if curl -fsS "http://localhost:$webapp_port/health" > /dev/null 2>&1; then
            echo "Webapp is healthy!"
            # Get version
            local version=$(curl -s "http://localhost:$webapp_port/version" 2>/dev/null || echo "unknown")
            echo "Webapp version: $version"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 1
    done

    echo "Warning: Webapp health check failed after $max_attempts attempts"
    return 1
}
```

---

## Files to Create/Modify

### New Files
1. `composeApp/Dockerfile` - Multi-stage Dockerfile for webapp
2. `composeApp/nginx.conf` - Nginx config for webapp container
3. `deploy/nginx/app.lehrerlog.de.conf` - Host nginx proxy config (prod)
4. `deploy/nginx/app.qa.lehrerlog.de.conf` - Host nginx proxy config (qa)
5. `.github/workflows/webapp-build-deploy.yml` - Webapp CI/CD workflow

### Files to Modify
1. `deploy/docker-compose.yml` - Add webapp service
2. `deploy/.env.example` - Add webapp configuration variables
3. `deploy/remote-deploy.sh` - Add webapp deployment function
4. `server/src/.../Application.kt` - Add CORS configuration

---

## Health Check Endpoints

### Webapp Health (`/health`)
```json
{"status":"ok","service":"lehrerlog-webapp"}
```

### Webapp Version (`/version`)
```
qa-abc123f
```

### Server Health (`/health`) - Already exists
```json
{"status":"ok","database":"connected"}
```

---

## Port Mapping Summary

**Note:** This VPS also runs Jitsi Meet, which uses ports 80, 443, 4443, 10000 (UDP). All LehrerLog ports are chosen to avoid conflicts.

| Service | Environment | Internal Port | Host Port | Docker Network |
|---------|-------------|---------------|-----------|----------------|
| Server  | Production  | 8080          | 18080     | lehrerlog-prod-net |
| Server  | QA          | 8080          | 18081     | lehrerlog-qa-net |
| Webapp  | Production  | 80            | 18082     | lehrerlog-prod-net |
| Webapp  | QA          | 80            | 18083     | lehrerlog-qa-net |
| DB      | Production  | 5432          | (internal)| lehrerlog-prod-net |
| DB      | QA          | 5432          | (internal)| lehrerlog-qa-net |

**Port Range Reserved for LehrerLog:** 18080-18089
- This avoids conflicts with standard services and Jitsi components

---

## Deployment Flow Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                        GitHub Actions                               │
├─────────────────────────────────────────────────────────────────────┤
│  1. Push to master / tag                                            │
│           ↓                                                         │
│  2. Build Docker image with serverUrl baked in                      │
│     docker build --build-arg SERVER_URL=... -f composeApp/Dockerfile│
│           ↓                                                         │
│  3. Push to ghcr.io/your-org/lehrerlog-webapp:qa-abc1234           │
│           ↓                                                         │
│  4. SSH to VPS                                                      │
│           ↓                                                         │
│  5. docker compose pull lehrerlog-webapp                            │
│           ↓                                                         │
│  6. docker compose up -d lehrerlog-webapp                           │
│           ↓                                                         │
│  7. Verify health: curl http://localhost:18083/health               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Rollback Strategy

Docker makes rollback simple:

```bash
# List available image tags
docker images ghcr.io/your-org/lehrerlog-webapp

# Rollback to previous version
cd $HOME/docker/lehrerlog-prod

# Update .env with the previous tag
sed -i 's/^WEBAPP_IMAGE_TAG=.*/WEBAPP_IMAGE_TAG=1.2.3/' .env

# Pull and restart
docker compose pull lehrerlog-webapp
docker compose up -d lehrerlog-webapp
```

---

## Next Steps (Execution Order)

1. [ ] **Phase 0:** Add CORS configuration to Ktor server
2. [ ] **Phase 0:** Deploy server with CORS and verify with curl tests
3. [ ] **Phase 1:** Create `composeApp/Dockerfile`
4. [ ] **Phase 2:** Create `composeApp/nginx.conf`
5. [ ] **Phase 3:** Update `deploy/docker-compose.yml` with webapp service
6. [ ] **Phase 4:** Update `deploy/.env.example` with webapp variables
7. [ ] **Phase 5:** Create host nginx configs for app.* domains
8. [ ] **Phase 6:** Add DNS records for app.lehrerlog.de and app.qa.lehrerlog.de
9. [ ] **Phase 6:** Create `.github/workflows/webapp-build-deploy.yml`
10. [ ] **Phase 7:** Update `deploy/remote-deploy.sh` with webapp deployment
11. [ ] Ensure `deploy/remote-deploy.sh` automates nginx + certbot for app.* domains
12. [ ] Test full CI/CD pipeline (build, push, deploy, health check)
13. [ ] Update documentation (AGENTS.md, README.md)
