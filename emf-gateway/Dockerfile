# Multi-stage build for EMF API Gateway
# Optimized with dependency caching layers for fast rebuilds

# =============================================================================
# Stage 1: Build runtime-core and runtime-events dependencies
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
# Stage 2: Build the application
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy Maven repository from runtime builder
COPY --from=runtime-builder /root/.m2/repository /root/.m2/repository

# Copy POM first for dependency layer caching
COPY emf-gateway/pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B || true

# Copy source and build
COPY emf-gateway/src ./src
RUN mvn clean package -DskipTests -B && \
    mv target/emf-gateway-*.jar target/emf-gateway.jar

# =============================================================================
# Stage 3: Runtime image
# =============================================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd -r emf && useradd -r -g emf emf

COPY --from=builder /build/target/emf-gateway.jar app.jar

RUN chown -R emf:emf /app

USER emf

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
