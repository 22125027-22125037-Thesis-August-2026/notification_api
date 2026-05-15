# syntax=docker/dockerfile:1.7

# =============================================================
# Stage 1 — Build the Spring Boot fat jar
# =============================================================
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

# Pre-copy wrapper + build files first to leverage Docker layer caching.
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Pre-warm dependencies (will no-op if wrapper/jar missing — falls through).
RUN sh -c "chmod +x ./gradlew || true"

# Copy sources and build.
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# =============================================================
# Stage 2 — Runtime image
# =============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Run as non-root for safety.
RUN addgroup -S notif && adduser -S -G notif notif

# Tini for proper PID-1 signal forwarding (graceful shutdown).
RUN apk add --no-cache tini

COPY --from=builder /workspace/build/libs/notification-service.jar /app/notification-service.jar

# Mount-point for Firebase credentials JSON (read-only at runtime).
RUN mkdir -p /app/secrets && chown -R notif:notif /app

USER notif

EXPOSE 8080

ENV JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/notification-service.jar"]
