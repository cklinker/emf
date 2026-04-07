# kelta-gateway

Spring Cloud Gateway service for the Kelta platform. Serves as the single ingress point for all external traffic, providing JWT authentication, Cerbos-based authorization, per-tenant rate limiting, and reactive request forwarding to upstream services.

## Prerequisites

- Java 25 (temurin-25.0.2+10.0.LTS recommended via `.tool-versions`)
- Maven 3.9+

Build dependencies (`runtime-events`, `runtime-jsonapi`) must be installed locally before building this module:

```bash
./mvnw clean install -DskipTests -f ../kelta-platform/pom.xml \
  -pl runtime/runtime-events,runtime/runtime-jsonapi -am -B
```

## Build

```bash
make build        # compile only
make test         # unit tests
make verify       # full build + tests + Checkstyle + coverage report
```

Or with Maven directly:

```bash
./mvnw verify
```

## Running Locally

Requires Redis, Kafka, and `kelta-auth` running. Copy `.env.example` to `.env` and adjust values, then:

```bash
make dev
```

The gateway starts on port **8080** by default.

## Linting and Formatting

```bash
make lint         # Checkstyle (warnings, non-blocking)
make format       # Check Spotless formatting
make format-fix   # Apply Spotless formatting
```

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full request lifecycle, routing, auth, and rate limiting design.

## API Reference

See [docs/api/README.md](docs/api/README.md) for gateway-owned endpoints and injected headers.
