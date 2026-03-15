# Multi-stage build for Kelta Auth Server

# =============================================================================
# Stage 1: Build the application
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy POM first for dependency layer caching
COPY kelta-auth/pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B || true

# Copy source and build
COPY kelta-auth/src ./src
RUN mvn clean package -DskipTests -B && \
    mv target/kelta-auth-*.jar target/kelta-auth.jar

# =============================================================================
# Stage 2: Runtime image
# =============================================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd -r kelta && useradd -r -g kelta kelta

COPY --from=builder /build/target/kelta-auth.jar app.jar

RUN curl -fL -o opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.25.0/opentelemetry-javaagent.jar && \
    test -s opentelemetry-javaagent.jar

RUN chown -R kelta:kelta /app

USER kelta

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -javaagent:/app/opentelemetry-javaagent.jar"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
