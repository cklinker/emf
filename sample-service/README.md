# Sample Service

A sample domain service for integration testing the EMF platform. This service demonstrates how to use the `emf-platform/runtime-core` library to build a service with dynamic collections, automatic REST APIs, and JSON:API support.

## Features

- **Two Collections**: Projects and Tasks with a relationship between them
- **Automatic Table Creation**: Uses `StorageAdapter` to create database tables automatically
- **Automatic REST API**: Uses `DynamicCollectionRouter` for CRUD endpoints
- **JSON:API Format**: All responses follow JSON:API specification
- **Include Parameter Support**: Enhanced router supports fetching related resources
- **Redis Caching**: Resources are cached for efficient include processing
- **Event-Driven**: Publishes events on create/update/delete operations
- **Validation**: Built-in validation for required fields and data types

## Collections

### Projects Collection
- **Fields**:
  - `name` (string, required)
  - `description` (string, optional)
  - `status` (enum: PLANNING, ACTIVE, COMPLETED, ARCHIVED)
- **Endpoints**:
  - `GET /api/collections/projects` - List projects
  - `GET /api/collections/projects/{id}` - Get project by ID
  - `POST /api/collections/projects` - Create project
  - `PUT /api/collections/projects/{id}` - Update project
  - `DELETE /api/collections/projects/{id}` - Delete project

### Tasks Collection
- **Fields**:
  - `title` (string, required)
  - `description` (string, optional)
  - `completed` (boolean, default: false)
  - `project_id` (reference to projects)
- **Endpoints**:
  - `GET /api/collections/tasks` - List tasks
  - `GET /api/collections/tasks/{id}?include=project` - Get task with project
  - `POST /api/collections/tasks` - Create task
  - `PUT /api/collections/tasks/{id}` - Update task
  - `DELETE /api/collections/tasks/{id}` - Delete task

## Architecture

### Components

1. **CollectionInitializer**: Defines and registers collections on startup
2. **ControlPlaneRegistration**: Registers service and collections with control plane
3. **EventPublishingQueryEngine**: Wraps query engine to publish Spring events
4. **CacheEventListener**: Listens to events and updates Redis cache
5. **ResourceCacheService**: Manages Redis cache for resources
6. **EnhancedCollectionRouter**: Extends DynamicCollectionRouter with include support

### Dependencies

- **emf-platform/runtime-core**: Core EMF runtime library
- **Spring Boot**: Web framework and dependency injection
- **Spring Data Redis**: Redis integration for caching
- **PostgreSQL**: Database for collection storage
- **H2**: In-memory database for testing

## Configuration

### Application Properties

```yaml
emf:
  storage:
    mode: PHYSICAL_TABLES  # Each collection gets its own table
  control-plane:
    url: http://localhost:8081
```

### Profiles

- **default**: Local development (localhost services)
- **integration-test**: Docker environment (service names as hosts)
- **test**: Unit testing (H2 in-memory database)

## Building

```bash
# Build with Maven
mvn clean package

# Build Docker image
docker build -t sample-service:latest .
```

## Running

### Local Development
```bash
# Start dependencies (PostgreSQL, Redis)
docker-compose up -d postgres redis

# Run the service
mvn spring-boot:run
```

### Docker
```bash
# Run with docker-compose
docker-compose up sample-service
```

## Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=DynamicCollectionRouterTest
```

## API Examples

### Create a Project
```bash
curl -X POST http://localhost:8082/api/collections/projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Project",
    "description": "A test project",
    "status": "PLANNING"
  }'
```

### Create a Task
```bash
curl -X POST http://localhost:8082/api/collections/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "My Task",
    "description": "A test task",
    "completed": false,
    "project_id": "PROJECT_ID_HERE"
  }'
```

### Get Task with Project (Include)
```bash
curl http://localhost:8082/api/collections/tasks/TASK_ID?include=project
```

Response:
```json
{
  "data": {
    "type": "tasks",
    "id": "...",
    "attributes": {
      "title": "My Task",
      "description": "A test task",
      "completed": false
    },
    "relationships": {
      "project": {
        "data": {
          "type": "projects",
          "id": "..."
        }
      }
    }
  },
  "included": [
    {
      "type": "projects",
      "id": "...",
      "attributes": {
        "name": "My Project",
        "description": "A test project",
        "status": "PLANNING"
      }
    }
  ]
}
```

## Health Check

```bash
curl http://localhost:8082/actuator/health
```

## Integration with EMF Platform

This service demonstrates the key integration points with the EMF platform:

1. **Runtime Core**: Uses `CollectionDefinition`, `StorageAdapter`, `QueryEngine`, `ValidationEngine`
2. **Control Plane**: Registers service and collections on startup
3. **API Gateway**: Routes requests through gateway in integration tests
4. **Redis**: Caches resources for include processing
5. **Kafka**: (Optional) Publishes events for configuration changes

## Next Steps

After implementing the sample service, the next tasks are:

- Task 5: Checkpoint - Verify Sample Service
- Task 6: Implement Test Framework Base Classes
- Task 7+: Implement Integration Test Suites
