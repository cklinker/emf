# Kelta Auth Server

OAuth2 Authorization Server and internal identity provider for the Kelta platform.

## Stack

- Java 25, Spring Boot 4.x, Spring Authorization Server
- PostgreSQL (authorization data), Redis (sessions)
- JUnit 5 + Mockito for testing, JaCoCo for coverage
- Checkstyle for linting, Spotless (google-java-format) for formatting

## Key Patterns

- All entities extend `BaseEntity` (UUID id, createdAt, updatedAt)
- Thymeleaf templates for login/consent UI in `src/main/resources/templates/`
- OIDC endpoints follow Spring Authorization Server conventions
- External IdP brokering configured via `application.yml`

## Build & Test

```bash
mvn clean package         # Build
mvn test                  # Unit tests
mvn verify -Pintegration-tests  # Integration tests
mvn checkstyle:check      # Lint
mvn spotless:check        # Format check
```
