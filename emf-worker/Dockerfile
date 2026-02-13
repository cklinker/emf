# Multi-stage build for EMF Worker Service

# =============================================================================
# Stage 1: Build runtime-core and runtime-events dependencies
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
# Stage 2: Build the application
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy Maven repository from runtime-core builder
COPY --from=runtime-core-builder /root/.m2/repository /root/.m2/repository

# Copy Maven files for dependency resolution
COPY emf-worker/pom.xml .
COPY emf-worker/src ./src

# Build the application (skip tests for faster builds)
RUN mvn clean package -DskipTests -B

# =============================================================================
# Stage 3: Runtime image
# =============================================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user for security
RUN groupadd -r emf && useradd -r -g emf emf

# Copy the JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R emf:emf /app

# Switch to non-root user
USER emf

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Set JVM options for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
