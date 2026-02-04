# EMF Control Plane Service

The Control Plane Service is the central configuration management service for the EMF (Enterprise Microservice Framework) platform. It provides REST APIs for managing collection definitions, authorization policies, UI configuration, OIDC providers, packages, and schema migrations.

## Features

- **Collection Management**: Define and manage data collection schemas with versioning
- **Field Management**: Add, update, and remove fields from collections
- **Authorization Management**: Configure role-based access control with route and field-level policies
- **OIDC Provider Management**: Configure trusted identity providers for authentication
- **UI Configuration**: Manage pages and menus for the admin interface
- **Package Management**: Export and import configuration for environment promotion
- **Migration Management**: Plan and track schema migrations
- **Resource Discovery**: Dynamic API discovery for domain services
- **OpenAPI Generation**: Auto-generated API documentation

## Technology Stack

- **Framework**: Spring Boot 3.2+
- **Language**: Java 21+
- **Database**: PostgreSQL 15+ (via Spring Data JPA)
- **Messaging**: Apache Kafka (via Spring Kafka)
- **Caching**: Redis (via Spring Data Redis)
- **Security**: Spring Security (OAuth2 Resource Server)
- **API Documentation**: springdoc-openapi 2.x
- **Observability**: Spring Actuator, Micrometer, OpenTelemetry

## Project Structure

```
emf-control-plane/
├── app/                          # Main application module
│   ├── src/main/java/           # Java source files
│   │   └── com/emf/controlplane/
│   │       ├── config/          # Configuration classes
│   │       ├── controller/      # REST controllers
│   │       ├── service/         # Business logic
│   │       ├── repository/      # Data access
│   │       ├── entity/          # JPA entities
│   │       ├── dto/             # Data transfer objects
│   │       └── event/           # Kafka event publishing
│   ├── src/main/resources/      # Configuration files
│   └── src/test/                # Test files
├── helm/                         # Helm charts (future)
└── docs/                         # Documentation (future)
```

## Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 15+
- Apache Kafka 3.x
- Redis 7.x

## Building

```bash
# Build the project
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Run tests
mvn test
```

## Running Locally

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `emf_control_plane` |
| `DB_USERNAME` | Database username | `emf` |
| `DB_PASSWORD` | Database password | `emf` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `localhost:9092` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `OIDC_ISSUER_URI` | OIDC issuer URI | - |
| `OIDC_JWKS_URI` | OIDC JWKS URI | - |
| `SERVER_PORT` | Server port | `8080` |

### Running with Docker Compose (Development)

```bash
# Start dependencies
docker-compose up -d postgres kafka redis

# Run the application
mvn spring-boot:run -pl app
```

### Running the JAR

```bash
java -jar app/target/control-plane-app-1.0.0-SNAPSHOT.jar
```

## API Endpoints

### Health & Monitoring
- `GET /actuator/health` - Health check
- `GET /actuator/health/liveness` - Liveness probe
- `GET /actuator/health/readiness` - Readiness probe
- `GET /actuator/metrics` - Metrics
- `GET /actuator/prometheus` - Prometheus metrics

### API Documentation
- `GET /openapi` - OpenAPI specification (JSON)
- `GET /swagger-ui` - Swagger UI

### Control Plane APIs
- `GET/POST /control/collections` - Collection management
- `GET/PUT/DELETE /control/collections/{id}` - Single collection operations
- `GET/POST /control/collections/{id}/fields` - Field management
- `GET/POST /control/roles` - Role management
- `GET/POST /control/policies` - Policy management
- `GET/POST/PUT/DELETE /control/oidc/providers` - OIDC provider management
- `GET/POST/PUT /ui/pages` - UI page management
- `GET/PUT /ui/menus` - UI menu management
- `POST /control/packages/export` - Package export
- `POST /control/packages/import` - Package import
- `POST /control/migrations/plan` - Migration planning
- `GET /control/migrations/runs` - Migration history
- `GET /api/_meta/resources` - Resource discovery

## Configuration

The application uses `application.yml` for configuration. Key configuration sections:

- **Database**: PostgreSQL connection settings
- **Kafka**: Message broker settings
- **Redis**: Cache settings
- **Security**: OAuth2/OIDC settings
- **Observability**: Logging, metrics, and tracing

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=CollectionServiceTest

# Run with coverage
mvn test jacoco:report
```

## License

Copyright © 2024 EMF Platform
