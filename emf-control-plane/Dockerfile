# EMF Control Plane Service Dockerfile
# Multi-stage build with dependency caching for fast rebuilds

# =============================================================================
# Stage 1: Build runtime-core dependency
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS runtime-builder

WORKDIR /emf-platform

# Copy only POMs first for dependency layer caching
COPY emf-platform/pom.xml ./
COPY emf-platform/runtime/pom.xml ./runtime/
COPY emf-platform/runtime/runtime-core/pom.xml ./runtime/runtime-core/
COPY emf-platform/runtime/runtime-events/pom.xml ./runtime/runtime-events/
COPY emf-platform/runtime/runtime-jsonapi/pom.xml ./runtime/runtime-jsonapi/
COPY emf-platform/runtime/runtime-module-core/pom.xml ./runtime/runtime-module-core/
COPY emf-platform/runtime/runtime-module-integration/pom.xml ./runtime/runtime-module-integration/
COPY emf-platform/runtime/runtime-module-schema/pom.xml ./runtime/runtime-module-schema/

# Download runtime dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema -am || true

# Copy source and build
COPY emf-platform/runtime/runtime-core/src ./runtime/runtime-core/src
COPY emf-platform/runtime/runtime-events/src ./runtime/runtime-events/src
COPY emf-platform/runtime/runtime-jsonapi/src ./runtime/runtime-jsonapi/src
COPY emf-platform/runtime/runtime-module-core/src ./runtime/runtime-module-core/src
COPY emf-platform/runtime/runtime-module-integration/src ./runtime/runtime-module-integration/src
COPY emf-platform/runtime/runtime-module-schema/src ./runtime/runtime-module-schema/src
RUN mvn clean install -DskipTests -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema -am -B

# =============================================================================
# Stage 2: Build Control Plane
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy Maven repository from runtime builder
COPY --from=runtime-builder /root/.m2/repository /root/.m2/repository

# Copy POMs first for dependency layer caching
COPY emf-control-plane/pom.xml ./
COPY emf-control-plane/app/pom.xml ./app/

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B -pl app -am || true

# Copy source code and build
COPY emf-control-plane/app/src ./app/src
RUN mvn package -DskipTests -pl app -am -B

# =============================================================================
# Stage 3: Runtime Stage
# =============================================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

LABEL org.opencontainers.image.title="EMF Control Plane Service"
LABEL org.opencontainers.image.description="Central configuration management service for EMF Platform"
LABEL org.opencontainers.image.vendor="EMF"
LABEL org.opencontainers.image.version="1.0.0-SNAPSHOT"

# Create non-root user for security
RUN groupadd --gid 1000 emf && \
    useradd --uid 1000 --gid emf --shell /bin/bash --create-home emf

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/app/target/control-plane-app-*.jar app.jar

# Change ownership to non-root user
RUN chown -R emf:emf /app

USER emf

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
