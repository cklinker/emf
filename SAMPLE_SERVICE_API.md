# Sample Service API Documentation

The Sample Service is a test domain service that implements two JSON:API collections: **projects** and **tasks**. It demonstrates the EMF platform's collection management capabilities and serves as the primary test target for integration tests.

## Table of Contents

- [Overview](#overview)
- [Base URL](#base-url)
- [Authentication](#authentication)
- [Collections](#collections)
- [Projects API](#projects-api)
- [Tasks API](#tasks-api)
- [Error Responses](#error-responses)
- [Examples](#examples)

## Overview

The Sample Service uses the **emf-platform/runtime-core** library to provide automatic:
- REST API endpoints for CRUD operations
- JSON:API format compliance
- Request validation
- Pagination, sorting, and filtering
- Relationship handling

All endpoints follow the [JSON:API specification](https://jsonapi.org/).

## Base URL

```
http://localhost:8082
```

When accessed through the API Gateway:
```
http://localhost:8080
```

## Authentication

All requests require a valid JWT token obtained from Keycloak.

### Get Token

```bash
curl -X POST http://localhost:8180/realms/emf/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=emf-client" \
  -d "username=admin" \
  -d "password=admin"
```

Response:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 300,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer"
}
```

### Use Token

Include the token in the Authorization header:

```bash
curl -H "Authorization: Bearer {access_token}" \
  http://localhost:8080/api/collections/projects
```

## Collections

### Projects Collection

Represents project entities with the following fields:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| id | UUID | Auto-generated | Unique identifier |
| name | String | Yes | Project name (1-100 chars) |
| description | String | No | Project description (max 500 chars) |
| status | Enum | No | Project status: PLANNING, ACTIVE, COMPLETED, ARCHIVED |
| created_at | Timestamp | Auto-generated | Creation timestamp |
| updated_at | Timestamp | Auto-generated | Last update timestamp |

**Relationships**:
- `tasks`: Has many tasks (one-to-many)

### Tasks Collection

Represents task entities with the following fields:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| id | UUID | Auto-generated | Unique identifier |
| title | String | Yes | Task title (1-200 chars) |
| description | String | No | Task description (max 1000 chars) |
| completed | Boolean | No | Completion status (default: false) |
| project_id | UUID | No | Reference to parent project |
| created_at | Timestamp | Auto-generated | Creation timestamp |
| updated_at | Timestamp | Auto-generated | Last update timestamp |

**Relationships**:
- `project`: Belongs to project (many-to-one)

## Projects API

### List Projects

```http
GET /api/collections/projects
```

**Query Parameters**:

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| page[number] | Integer | Page number (1-based) | `page[number]=1` |
| page[size] | Integer | Items per page (default: 20) | `page[size]=10` |
| sort | String | Sort field(s), prefix with `-` for desc | `sort=-created_at` |
| filter[{field}] | String | Filter by field value | `filter[status]=ACTIVE` |
| include | String | Include related resources | `include=tasks` |

**Example Request**:

```bash
curl -H "Authorization: Bearer {token}" \
  "http://localhost:8080/api/collections/projects?page[size]=10&sort=-created_at&filter[status]=ACTIVE"
```

**Example Response**:

```json
{
  "data": [
    {
      "type": "projects",
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "attributes": {
        "name": "EMF Platform",
        "description": "Enterprise Microservice Framework",
        "status": "ACTIVE",
        "created_at": "2024-01-15T10:30:00Z",
        "updated_at": "2024-01-15T10:30:00Z"
      },
      "relationships": {
        "tasks": {
          "data": [
            {"type": "tasks", "id": "456e7890-e89b-12d3-a456-426614174001"},
            {"type": "tasks", "id": "789e0123-e89b-12d3-a456-426614174002"}
          ]
        }
      }
    }
  ],
  "meta": {
    "page": {
      "number": 1,
      "size": 10,
      "total": 1
    }
  },
  "links": {
    "self": "/api/collections/projects?page[number]=1&page[size]=10",
    "first": "/api/collections/projects?page[number]=1&page[size]=10",
    "last": "/api/collections/projects?page[number]=1&page[size]=10"
  }
}
```

### Get Project

```http
GET /api/collections/projects/{id}
```

**Path Parameters**:
- `id` (UUID): Project identifier

**Query Parameters**:
- `include` (String): Include related resources (e.g., `include=tasks`)

**Example Request**:

```bash
curl -H "Authorization: Bearer {token}" \
  "http://localhost:8080/api/collections/projects/123e4567-e89b-12d3-a456-426614174000?include=tasks"
```

**Example Response**:

```json
{
  "data": {
    "type": "projects",
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "attributes": {
      "name": "EMF Platform",
      "description": "Enterprise Microservice Framework",
      "status": "ACTIVE",
      "created_at": "2024-01-15T10:30:00Z",
      "updated_at": "2024-01-15T10:30:00Z"
    },
    "relationships": {
      "tasks": {
        "data": [
          {"type": "tasks", "id": "456e7890-e89b-12d3-a456-426614174001"},
          {"type": "tasks", "id": "789e0123-e89b-12d3-a456-426614174002"}
        ]
      }
    }
  },
  "included": [
    {
      "type": "tasks",
      "id": "456e7890-e89b-12d3-a456-426614174001",
      "attributes": {
        "title": "Implement authentication",
        "description": "Add JWT authentication",
        "completed": true,
        "created_at": "2024-01-15T11:00:00Z",
        "updated_at": "2024-01-16T09:00:00Z"
      },
      "relationships": {
        "project": {
          "data": {"type": "projects", "id": "123e4567-e89b-12d3-a456-426614174000"}
        }
      }
    },
    {
      "type": "tasks",
      "id": "789e0123-e89b-12d3-a456-426614174002",
      "attributes": {
        "title": "Add authorization",
        "description": "Implement role-based access control",
        "completed": false,
        "created_at": "2024-01-15T11:30:00Z",
        "updated_at": "2024-01-15T11:30:00Z"
      },
      "relationships": {
        "project": {
          "data": {"type": "projects", "id": "123e4567-e89b-12d3-a456-426614174000"}
        }
      }
    }
  ]
}
```

### Create Project

```http
POST /api/collections/projects
```

**Request Body**:

```json
{
  "data": {
    "type": "projects",
    "attributes": {
      "name": "New Project",
      "description": "Project description",
      "status": "PLANNING"
    }
  }
}
```

**Example Request**:

```bash
curl -X POST \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "data": {
      "type": "projects",
      "attributes": {
        "name": "New Project",
        "description": "Project description",
        "status": "PLANNING"
      }
    }
  }' \
  http://localhost:8080/api/collections/projects
```

**Example Response** (201 Created):

```json
{
  "data": {
    "type": "projects",
    "id": "abc12345-e89b-12d3-a456-426614174003",
    "attributes": {
      "name": "New Project",
      "description": "Project description",
      "status": "PLANNING",
      "created_at": "2024-01-20T14:30:00Z",
      "updated_at": "2024-01-20T14:30:00Z"
    },
    "relationships": {
      "tasks": {
        "data": []
      }
    }
  }
}
```

### Update Project

```http
PATCH /api/collections/projects/{id}
```

**Path Parameters**:
- `id` (UUID): Project identifier

**Request Body**:

```json
{
  "data": {
    "type": "projects",
    "id": "abc12345-e89b-12d3-a456-426614174003",
    "attributes": {
      "status": "ACTIVE"
    }
  }
}
```

**Example Request**:

```bash
curl -X PATCH \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "data": {
      "type": "projects",
      "id": "abc12345-e89b-12d3-a456-426614174003",
      "attributes": {
        "status": "ACTIVE"
      }
    }
  }' \
  http://localhost:8080/api/collections/projects/abc12345-e89b-12d3-a456-426614174003
```

**Example Response** (200 OK):

```json
{
  "data": {
    "type": "projects",
    "id": "abc12345-e89b-12d3-a456-426614174003",
    "attributes": {
      "name": "New Project",
      "description": "Project description",
      "status": "ACTIVE",
      "created_at": "2024-01-20T14:30:00Z",
      "updated_at": "2024-01-20T15:00:00Z"
    },
    "relationships": {
      "tasks": {
        "data": []
      }
    }
  }
}
```

### Delete Project

```http
DELETE /api/collections/projects/{id}
```

**Path Parameters**:
- `id` (UUID): Project identifier

**Example Request**:

```bash
curl -X DELETE \
  -H "Authorization: Bearer {token}" \
  http://localhost:8080/api/collections/projects/abc12345-e89b-12d3-a456-426614174003
```

**Example Response** (204 No Content):

No response body.

## Tasks API

### List Tasks

```http
GET /api/collections/tasks
```

**Query Parameters**: Same as Projects API

**Example Request**:

```bash
curl -H "Authorization: Bearer {token}" \
  "http://localhost:8080/api/collections/tasks?filter[completed]=false&include=project"
```

**Example Response**:

```json
{
  "data": [
    {
      "type": "tasks",
      "id": "def45678-e89b-12d3-a456-426614174004",
      "attributes": {
        "title": "Write documentation",
        "description": "Create API documentation",
        "completed": false,
        "created_at": "2024-01-20T16:00:00Z",
        "updated_at": "2024-01-20T16:00:00Z"
      },
      "relationships": {
        "project": {
          "data": {"type": "projects", "id": "123e4567-e89b-12d3-a456-426614174000"}
        }
      }
    }
  ],
  "included": [
    {
      "type": "projects",
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "attributes": {
        "name": "EMF Platform",
        "description": "Enterprise Microservice Framework",
        "status": "ACTIVE",
        "created_at": "2024-01-15T10:30:00Z",
        "updated_at": "2024-01-15T10:30:00Z"
      }
    }
  ],
  "meta": {
    "page": {
      "number": 1,
      "size": 20,
      "total": 1
    }
  }
}
```

### Get Task

```http
GET /api/collections/tasks/{id}
```

**Path Parameters**:
- `id` (UUID): Task identifier

**Query Parameters**:
- `include` (String): Include related resources (e.g., `include=project`)

**Example Request**:

```bash
curl -H "Authorization: Bearer {token}" \
  "http://localhost:8080/api/collections/tasks/def45678-e89b-12d3-a456-426614174004?include=project"
```

**Example Response**: Similar to List Tasks response, but with single task in `data`.

### Create Task

```http
POST /api/collections/tasks
```

**Request Body**:

```json
{
  "data": {
    "type": "tasks",
    "attributes": {
      "title": "New Task",
      "description": "Task description",
      "completed": false
    },
    "relationships": {
      "project": {
        "data": {
          "type": "projects",
          "id": "123e4567-e89b-12d3-a456-426614174000"
        }
      }
    }
  }
}
```

**Example Request**:

```bash
curl -X POST \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "data": {
      "type": "tasks",
      "attributes": {
        "title": "New Task",
        "description": "Task description",
        "completed": false
      },
      "relationships": {
        "project": {
          "data": {
            "type": "projects",
            "id": "123e4567-e89b-12d3-a456-426614174000"
          }
        }
      }
    }
  }' \
  http://localhost:8080/api/collections/tasks
```

**Example Response** (201 Created):

```json
{
  "data": {
    "type": "tasks",
    "id": "ghi78901-e89b-12d3-a456-426614174005",
    "attributes": {
      "title": "New Task",
      "description": "Task description",
      "completed": false,
      "created_at": "2024-01-20T17:00:00Z",
      "updated_at": "2024-01-20T17:00:00Z"
    },
    "relationships": {
      "project": {
        "data": {"type": "projects", "id": "123e4567-e89b-12d3-a456-426614174000"}
      }
    }
  }
}
```

### Update Task

```http
PATCH /api/collections/tasks/{id}
```

**Path Parameters**:
- `id` (UUID): Task identifier

**Request Body**:

```json
{
  "data": {
    "type": "tasks",
    "id": "ghi78901-e89b-12d3-a456-426614174005",
    "attributes": {
      "completed": true
    }
  }
}
```

**Example Request**:

```bash
curl -X PATCH \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "data": {
      "type": "tasks",
      "id": "ghi78901-e89b-12d3-a456-426614174005",
      "attributes": {
        "completed": true
      }
    }
  }' \
  http://localhost:8080/api/collections/tasks/ghi78901-e89b-12d3-a456-426614174005
```

**Example Response** (200 OK): Similar to Create Task response with updated values.

### Delete Task

```http
DELETE /api/collections/tasks/{id}
```

**Path Parameters**:
- `id` (UUID): Task identifier

**Example Request**:

```bash
curl -X DELETE \
  -H "Authorization: Bearer {token}" \
  http://localhost:8080/api/collections/tasks/ghi78901-e89b-12d3-a456-426614174005
```

**Example Response** (204 No Content): No response body.

## Error Responses

All errors follow the JSON:API error format.

### 400 Bad Request

**Validation Error**:

```json
{
  "errors": [
    {
      "status": "400",
      "code": "VALIDATION_ERROR",
      "title": "Validation Failed",
      "detail": "The 'name' field is required",
      "source": {
        "pointer": "/data/attributes/name"
      }
    }
  ]
}
```

**Invalid JSON**:

```json
{
  "errors": [
    {
      "status": "400",
      "code": "INVALID_JSON",
      "title": "Invalid JSON",
      "detail": "Unexpected character at position 15"
    }
  ]
}
```

### 401 Unauthorized

**Missing Token**:

```json
{
  "errors": [
    {
      "status": "401",
      "code": "UNAUTHORIZED",
      "title": "Authentication Required",
      "detail": "Missing or invalid Authorization header"
    }
  ]
}
```

**Expired Token**:

```json
{
  "errors": [
    {
      "status": "401",
      "code": "TOKEN_EXPIRED",
      "title": "Token Expired",
      "detail": "The provided JWT token has expired"
    }
  ]
}
```

### 403 Forbidden

**Insufficient Permissions**:

```json
{
  "errors": [
    {
      "status": "403",
      "code": "FORBIDDEN",
      "title": "Access Denied",
      "detail": "User does not have required role: ADMIN"
    }
  ]
}
```

### 404 Not Found

**Resource Not Found**:

```json
{
  "errors": [
    {
      "status": "404",
      "code": "NOT_FOUND",
      "title": "Resource Not Found",
      "detail": "Project with id '123e4567-e89b-12d3-a456-426614174000' not found"
    }
  ]
}
```

### 500 Internal Server Error

**Server Error**:

```json
{
  "errors": [
    {
      "status": "500",
      "code": "INTERNAL_ERROR",
      "title": "Internal Server Error",
      "detail": "An unexpected error occurred"
    }
  ]
}
```

## Examples

### Complete Workflow Example

```bash
# 1. Get authentication token
TOKEN=$(curl -s -X POST http://localhost:8180/realms/emf/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=emf-client" \
  -d "username=admin" \
  -d "password=admin" | jq -r '.access_token')

# 2. Create a project
PROJECT_ID=$(curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "data": {
      "type": "projects",
      "attributes": {
        "name": "My Project",
        "description": "A test project",
        "status": "PLANNING"
      }
    }
  }' \
  http://localhost:8080/api/collections/projects | jq -r '.data.id')

echo "Created project: $PROJECT_ID"

# 3. Create tasks for the project
TASK1_ID=$(curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"data\": {
      \"type\": \"tasks\",
      \"attributes\": {
        \"title\": \"Task 1\",
        \"description\": \"First task\",
        \"completed\": false
      },
      \"relationships\": {
        \"project\": {
          \"data\": {
            \"type\": \"projects\",
            \"id\": \"$PROJECT_ID\"
          }
        }
      }
    }
  }" \
  http://localhost:8080/api/collections/tasks | jq -r '.data.id')

echo "Created task 1: $TASK1_ID"

TASK2_ID=$(curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"data\": {
      \"type\": \"tasks\",
      \"attributes\": {
        \"title\": \"Task 2\",
        \"description\": \"Second task\",
        \"completed\": false
      },
      \"relationships\": {
        \"project\": {
          \"data\": {
            \"type\": \"projects\",
            \"id\": \"$PROJECT_ID\"
          }
        }
      }
    }
  }" \
  http://localhost:8080/api/collections/tasks | jq -r '.data.id')

echo "Created task 2: $TASK2_ID"

# 4. Get project with included tasks
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/projects/$PROJECT_ID?include=tasks" | jq

# 5. Update task to completed
curl -s -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"data\": {
      \"type\": \"tasks\",
      \"id\": \"$TASK1_ID\",
      \"attributes\": {
        \"completed\": true
      }
    }
  }" \
  "http://localhost:8080/api/collections/tasks/$TASK1_ID" | jq

# 6. Update project status
curl -s -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"data\": {
      \"type\": \"projects\",
      \"id\": \"$PROJECT_ID\",
      \"attributes\": {
        \"status\": \"ACTIVE\"
      }
    }
  }" \
  "http://localhost:8080/api/collections/projects/$PROJECT_ID" | jq

# 7. List all active projects
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/projects?filter[status]=ACTIVE" | jq

# 8. List incomplete tasks
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/tasks?filter[completed]=false&include=project" | jq

# 9. Delete tasks
curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/tasks/$TASK1_ID"

curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/tasks/$TASK2_ID"

# 10. Delete project
curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/projects/$PROJECT_ID"

echo "Cleanup complete"
```

### Pagination Example

```bash
# Get first page
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/projects?page[number]=1&page[size]=5"

# Get second page
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/projects?page[number]=2&page[size]=5"
```

### Sorting Example

```bash
# Sort by name ascending
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/projects?sort=name"

# Sort by created_at descending
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/projects?sort=-created_at"

# Multiple sort fields
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/projects?sort=status,-created_at"
```

### Filtering Example

```bash
# Filter by status
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/projects?filter[status]=ACTIVE"

# Filter by multiple fields
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/tasks?filter[completed]=false&filter[project_id]=$PROJECT_ID"
```

### Include Example

```bash
# Include related tasks
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/projects/$PROJECT_ID?include=tasks"

# Include related project
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/collections/tasks/$TASK_ID?include=project"
```

## Testing with curl

### Save Token to File

```bash
# Get token and save to file
curl -s -X POST http://localhost:8180/realms/emf/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=emf-client" \
  -d "username=admin" \
  -d "password=admin" | jq -r '.access_token' > token.txt

# Use token from file
curl -H "Authorization: Bearer $(cat token.txt)" \
  http://localhost:8080/api/collections/projects
```

### Pretty Print JSON

```bash
# Use jq for pretty printing
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/collections/projects | jq

# Use jq to extract specific fields
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/collections/projects | jq '.data[].attributes.name'
```

## Additional Resources

- [JSON:API Specification](https://jsonapi.org/)
- [Integration Tests README](INTEGRATION_TESTS_README.md)
- [Architecture Documentation](INTEGRATION_TESTS_ARCHITECTURE.md)
- [Troubleshooting Guide](INTEGRATION_TESTS_TROUBLESHOOTING.md)
