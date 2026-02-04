# EMF Control Plane Service Dockerfile
# Multi-stage build for efficient image creation
# Base image: Eclipse Temurin JDK 21

# =============================================================================
# Stage 1: Build runtime-core dependency
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS runtime-core-builder

WORKDIR /emf-platform

# Copy emf-platform parent pom
COPY emf-platform/pom.xml ./

# Copy runtime parent pom
COPY emf-platform/runtime/pom.xml ./runtime/

# Copy runtime-core source
COPY emf-platform/runtime/runtime-core ./runtime/runtime-core

# Copy runtime-events source (shared Kafka events)
COPY emf-platform/runtime/runtime-events ./runtime/runtime-events

# Build and install runtime modules to local Maven repository
RUN mvn clean install -DskipTests -pl runtime/runtime-core,runtime/runtime-events -am -B

# =============================================================================
# Stage 2: Build Control Plane
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /build

# Copy Maven repository from runtime-core builder
COPY --from=runtime-core-builder /root/.m2/repository /root/.m2/repository

# Copy parent pom first
COPY emf-control-plane/pom.xml ./

# Copy app pom for dependency resolution
COPY emf-control-plane/app/pom.xml ./app/

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B -pl app -am || true

# Copy source code
COPY emf-control-plane/app/src ./app/src

# Build the application
RUN mvn package -DskipTests -pl app -am -B

# =============================================================================
# Stage 3: Runtime Stage
# =============================================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

# Labels for container metadata
LABEL org.opencontainers.image.title="EMF Control Plane Service"
LABEL org.opencontainers.image.description="Central configuration management service for EMF Platform"
LABEL org.opencontainers.image.vendor="EMF"
LABEL org.opencontainers.image.version="1.0.0-SNAPSHOT"
LABEL org.opencontainers.image.source="https://github.com/emf/emf-control-plane"

# Create non-root user for security
RUN groupadd --gid 1000 emf && \
    useradd --uid 1000 --gid emf --shell /bin/bash --create-home emf

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/app/target/control-plane-app-*.jar app.jar

# Change ownership to non-root user
RUN chown -R emf:emf /app

# Switch to non-root user
USER emf

# Expose application port
EXPOSE 8080

# Health check configuration
# Uses Spring Boot Actuator health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

# JVM configuration for containers
# - UseContainerSupport: Enables container-aware memory settings
# - MaxRAMPercentage: Limits heap to 75% of container memory
# - InitialRAMPercentage: Sets initial heap to 50% of container memory
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom"

# Spring Boot entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
