# EMF Control Plane Helm Chart

This Helm chart deploys the EMF Control Plane Service - the central configuration management service for the EMF Platform.

## Prerequisites

- Kubernetes 1.23+
- Helm 3.8+
- PostgreSQL 15+ (external or in-cluster)
- Apache Kafka (optional, for event publishing)
- Redis (optional, for caching)

## Installation

### Add the EMF Helm repository

```bash
helm repo add emf https://charts.emf.io
helm repo update
```

### Install the chart

```bash
helm install emf-control-plane emf/emf-control-plane \
  --namespace emf \
  --create-namespace \
  --set database.host=postgresql \
  --set secrets.dbPassword=$(echo -n 'your-password' | base64)
```

### Install with custom values

```bash
helm install emf-control-plane emf/emf-control-plane \
  --namespace emf \
  --create-namespace \
  -f my-values.yaml
```

## Configuration

### Key Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `image.repository` | Container image repository | `emf/control-plane` |
| `image.tag` | Container image tag | Chart appVersion |
| `replicaCount` | Number of replicas | `1` |
| `resources.limits.cpu` | CPU limit | `1000m` |
| `resources.limits.memory` | Memory limit | `1Gi` |
| `resources.requests.cpu` | CPU request | `250m` |
| `resources.requests.memory` | Memory request | `512Mi` |

### Database Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `database.host` | PostgreSQL host | `postgresql` |
| `database.port` | PostgreSQL port | `5432` |
| `database.name` | Database name | `emf_control_plane` |
| `database.username` | Database username | `emf` |
| `secrets.dbPassword` | Database password (base64) | `Y2hhbmdlbWU=` (changeme) |

### Kafka Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `kafka.enabled` | Enable Kafka event publishing | `true` |
| `kafka.bootstrapServers` | Kafka bootstrap servers | `kafka:9092` |

### Redis Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `redis.enabled` | Enable Redis caching | `true` |
| `redis.host` | Redis host | `redis` |
| `redis.port` | Redis port | `6379` |
| `secrets.redisPassword` | Redis password (base64) | `` |

### Security Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `security.enabled` | Enable security | `true` |
| `security.oidc.issuerUri` | OIDC issuer URI | `` |
| `security.oidc.jwksUri` | OIDC JWKS URI | `` |

### Health Probes

| Parameter | Description | Default |
|-----------|-------------|---------|
| `livenessProbe.enabled` | Enable liveness probe | `true` |
| `livenessProbe.initialDelaySeconds` | Initial delay | `60` |
| `readinessProbe.enabled` | Enable readiness probe | `true` |
| `readinessProbe.initialDelaySeconds` | Initial delay | `30` |
| `startupProbe.enabled` | Enable startup probe | `true` |
| `startupProbe.failureThreshold` | Failure threshold | `30` |

## Example Values

### Production Configuration

```yaml
replicaCount: 3

resources:
  limits:
    cpu: 2000m
    memory: 2Gi
  requests:
    cpu: 500m
    memory: 1Gi

autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70

database:
  host: postgresql.database.svc.cluster.local
  pool:
    maxSize: 20

kafka:
  enabled: true
  bootstrapServers: kafka-0.kafka:9092,kafka-1.kafka:9092,kafka-2.kafka:9092

redis:
  enabled: true
  host: redis-master.cache.svc.cluster.local

security:
  enabled: true
  oidc:
    issuerUri: https://auth.example.com/realms/emf
    jwksUri: https://auth.example.com/realms/emf/protocol/openid-connect/certs

observability:
  tracing:
    enabled: true
    sampleRate: "0.1"
    otlpEndpoint: http://otel-collector.observability:4317

ingress:
  enabled: true
  className: nginx
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
  hosts:
    - host: control-plane.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: control-plane-tls
      hosts:
        - control-plane.example.com
```

### Development Configuration

```yaml
replicaCount: 1

resources:
  limits:
    cpu: 500m
    memory: 512Mi
  requests:
    cpu: 100m
    memory: 256Mi

database:
  host: postgresql
  
kafka:
  enabled: false

redis:
  enabled: false

security:
  enabled: false

observability:
  tracing:
    enabled: false
```

## Using External Secrets

For production deployments, it's recommended to use external secret management:

```yaml
secrets:
  existingSecret: my-external-secret
```

The external secret should contain:
- `DB_PASSWORD`: PostgreSQL password
- `REDIS_PASSWORD`: Redis password (optional)

## Upgrading

```bash
helm upgrade emf-control-plane emf/emf-control-plane \
  --namespace emf \
  -f my-values.yaml
```

## Uninstalling

```bash
helm uninstall emf-control-plane --namespace emf
```

## Troubleshooting

### Check pod status

```bash
kubectl get pods -n emf -l app.kubernetes.io/name=emf-control-plane
```

### View logs

```bash
kubectl logs -n emf -l app.kubernetes.io/name=emf-control-plane -f
```

### Check health endpoints

```bash
kubectl port-forward -n emf svc/emf-control-plane 8080:8080
curl http://localhost:8080/actuator/health/readiness
```

## License

Copyright © EMF Team. All rights reserved.
