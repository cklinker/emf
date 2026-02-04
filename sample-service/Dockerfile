# Multi-stage build for Sample Service

# Stage 1: Build runtime-core dependency
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
RUN mvn clean install -DskipTests -pl runtime/runtime-core,runtime/runtime-events -am

# Stage 2: Build Sample Service
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy Maven repository from runtime-core builder
COPY --from=runtime-core-builder /root/.m2/repository /root/.m2/repository

# Copy sample service files
COPY sample-service/pom.xml .
COPY sample-service/src ./src

# Build sample service
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port 8080
EXPOSE 8080

# Add health check endpoint
HEALTHCHECK --interval=10s --timeout=5s --retries=10 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
