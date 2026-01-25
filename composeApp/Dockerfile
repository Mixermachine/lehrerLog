FROM gradle:8.14.4-jdk17 AS build

WORKDIR /workspace

# Copy Gradle files first for better caching.
COPY gradle gradle
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY shared/build.gradle.kts shared/
COPY composeApp/build.gradle.kts composeApp/

RUN chmod +x gradlew

# Node tooling used by Kotlin/Wasm requires libatomic.
RUN apt-get update \
    && apt-get install -y libatomic1 \
    && rm -rf /var/lib/apt/lists/*

# Download dependencies (cacheable layer).
RUN ./gradlew dependencies --no-daemon || true

# Copy source code.
COPY shared/src shared/src
COPY composeApp/src composeApp/src

# Build argument for server URL (baked into the build).
ARG SERVER_URL=https://api.lehrerlog.de

# Build the wasmJS distribution.
RUN ./gradlew :composeApp:wasmJsBrowserDistribution -PserverUrl=${SERVER_URL} --no-daemon

FROM nginx:alpine

COPY --from=build /workspace/composeApp/build/dist/wasmJs/productionExecutable/ /usr/share/nginx/html/
COPY composeApp/nginx.conf /etc/nginx/conf.d/default.conf

ARG BUILD_VERSION=dev
RUN echo "${BUILD_VERSION}" > /usr/share/nginx/html/version.txt

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget -q --spider http://localhost/health || exit 1
