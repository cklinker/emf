# EMF API Gateway

The EMF API Gateway is a Spring Cloud Gateway-based service that serves as the main ingress point for all EMF platform applications. It provides centralized authentication, authorization, dynamic routing, JSON:API processing with intelligent caching, and rate limiting capabilities.

## Features

- **Dynamic Routing**: Automatically discovers routes from the EMF control plane
- **Authentication**: JWT-based authentication using OAuth2 Resource Server
- **Authorization**: Route-level and field-level authorization policies
- **JSON:API Processing**: Intelligent include parameter processing with Redis caching
- **Rate Limiting**: Configurable rate limits per route using Redis
- **Real-time Configuration**: Updates configuration dynamically via Kafka events
- **Health Monitoring**: Comprehensive health checks and metrics via Spring Boot Actuator

## Technology Stack

- **Java 21**: Modern Java with latest features
- **Spring Boot 3.2.1**: Latest Spring Boot framework
- **Spring Cloud Gateway**: Reactive API gateway
- **Spring WebFlux**: Reactive, non-blocking web framework
- **Redis**: Caching and rate limiting
- **Kafka**: Real-time configuration updates
- **Spring Security OAuth2**: JWT validation
- **JUnit QuickCheck**: Property-based testing

## Project Structure

```
emf-gateway/
├── src/
│   ├── main/
│   │   ├── java/com/emf/gateway/
│   │   │   └── GatewayApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/com/emf/gateway/
│       │   ├── unit/              # Unit tests
│       │   └── property/          # Property-based tests
│       └── resources/
│           └── application-test.yml
├── pom.xml
└── README.md
```

## Configuration

The gateway is configured through `application.yml`. Key configuration properties:

### Control Plane
```yaml
emf.gateway.control-plane.url: ${CONTROL_PLANE_URL:http://localhost:8080}
```

### Kafka
```yaml
spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
emf.gateway.kafka.topics:
  collection-changed: emf.config.collection.changed
  authz-changed: emf.config.authz.changed
  service-changed: emf.config.service.changed
```

### Redis
```yaml
spring.data.redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}
```

### JWT
```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: ${JWT_ISSUER_URI:http://localhost:9000/realms/emf}
```

### Rate Limiting
```yaml
emf.gateway.rate-limit.default:
  requests-per-window: 1000
  window-duration: PT1M  # 1 minute
```

## Building

Build the project using Maven:

```bash
mvn clean install
```

## Running Locally

### Prerequisites

- Java 21
- Redis running on localhost:6379
- Kafka running on localhost:9092
- EMF Control Plane running on localhost:8080

### Run the application

```bash
mvn spring-boot:run
```

Or run the JAR:

```bash
java -jar target/emf-gateway-1.0.0-SNAPSHOT.jar
```

### Environment Variables

You can override configuration using environment variables:

```bash
export CONTROL_PLANE_URL=http://control-plane:8080
export KAFKA_BOOTSTRAP_SERVERS=kafka:9092
export REDIS_HOST=redis
export REDIS_PORT=6379
export JWT_ISSUER_URI=https://auth.example.com/realms/emf

mvn spring-boot:run
```

## Testing

### Run all tests

```bash
mvn test
```

### Run unit tests only

```bash
mvn test -Dtest="**/unit/**"
```

### Run property-based tests only

```bash
mvn test -Dtest="**/property/**"
```

## Health Checks

The gateway exposes health check endpoints via Spring Boot Actuator:

- **Overall Health**: `GET /actuator/health`
- **Redis Health**: `GET /actuator/health/redis`
- **Kafka Health**: `GET /actuator/health/kafka`
- **Metrics**: `GET /actuator/metrics`

## API Endpoints

The gateway routes all requests based on dynamic configuration from the control plane:

- **Control Plane**: `/control/**` → Control plane service
- **Dynamic Routes**: Configured via control plane bootstrap endpoint

### Bootstrap Endpoint

The gateway fetches initial configuration from:

```
GET http://control-plane:8080/control/bootstrap
```

This endpoint returns services, collections, and authorization configuration.

## Development

### Adding New Filters

Filters are implemented as Spring Cloud Gateway `GlobalFilter` beans. Order determines execution sequence:

- `-100`: Authentication (early)
- `-50`: Rate limiting
- `0`: Route authorization
- `50`: Header transformation
- `100`: Field authorization (after backend response)
- `200`: JSON:API include processing

### Testing Strategy

The project uses a dual testing approach:

1. **Unit Tests**: Test specific examples and edge cases
2. **Property-Based Tests**: Test universal properties across all inputs using JUnit QuickCheck

## Deployment

### Docker

Build Docker image:

```bash
docker build -t emf-gateway:latest .
```

Run Docker container:

```bash
docker run -p 8080:8080 \
  -e CONTROL_PLANE_URL=http://control-plane:8080 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e REDIS_HOST=redis \
  -e JWT_ISSUER_URI=https://auth.example.com/realms/emf \
  emf-gateway:latest
```

### Kubernetes/Helm

Deploy using Helm:

```bash
helm install emf-gateway ./helm/emf-gateway \
  --set config.controlPlane.url=http://emf-control-plane:8080 \
  --set config.kafka.bootstrapServers=kafka:9092 \
  --set config.redis.host=redis
```

## Monitoring

### Metrics

The gateway exposes Micrometer metrics:

- `gateway.requests.total`: Total request count
- `gateway.requests.duration`: Request duration histogram
- `gateway.auth.failures`: Authentication failures
- `gateway.authz.denials`: Authorization denials
- `gateway.ratelimit.exceeded`: Rate limit exceeded count
- `gateway.cache.hits`: Redis cache hits
- `gateway.cache.misses`: Redis cache misses

### Logging

Structured JSON logging with fields:

- `timestamp`: ISO 8601 timestamp
- `level`: Log level
- `correlationId`: Request correlation ID
- `path`: Request path
- `method`: HTTP method
- `status`: Response status code
- `duration`: Request duration in milliseconds

## Architecture

The gateway follows a reactive, non-blocking architecture using Spring WebFlux and Project Reactor. Configuration updates are applied dynamically without requiring service restarts.

### Request Flow

1. Client sends request with JWT token
2. Authentication filter validates JWT
3. Rate limit filter checks rate limits
4. Route authorization filter checks route policies
5. Request forwarded to backend service
6. Field authorization filter filters response fields
7. JSON:API include filter processes includes
8. Response returned to client

### Configuration Updates

1. Control plane publishes configuration change to Kafka
2. Gateway consumes Kafka event
3. Gateway updates in-memory registry/cache
4. New configuration takes effect immediately

## Contributing

See the main EMF documentation for contribution guidelines.

## License

Copyright © 2024 EMF Platform
