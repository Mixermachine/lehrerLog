FROM gradle:8.7-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :server:installDist --no-daemon

FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/server/build/install/server/ /app/
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
