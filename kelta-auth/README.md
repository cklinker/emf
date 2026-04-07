# Kelta Auth Server

Internal OIDC provider for the Kelta platform. Handles user authentication, identity brokering, MFA, and token issuance.

## Features

- OAuth2 Authorization Server (Spring Authorization Server)
- Identity brokering with external IdPs (Google, GitHub, etc.)
- TOTP-based multi-factor authentication
- Session management via Redis
- PostgreSQL-backed persistence for authorization data
- Thymeleaf-based login and consent UI

## Prerequisites

- Java 25 (Temurin)
- Maven 3.9+
- PostgreSQL
- Redis

## Getting Started

```bash
# Build
make build

# Run tests
make test

# Run locally
make dev
```

## Configuration

See [.env.example](.env.example) for required environment variables.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

## API

See [docs/api/](docs/api/) for endpoint documentation.

## Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify -Pintegration-tests

# Coverage report
mvn test jacoco:report
# Report at target/site/jacoco/index.html
```
