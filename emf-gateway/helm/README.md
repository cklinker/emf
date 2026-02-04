# EMF Gateway Helm Chart

This Helm chart deploys the EMF API Gateway to Kubernetes. The gateway serves as the main ingress point for all EMF-based applications, providing authentication, authorization, dynamic routing, JSON:API processing, and rate limiting.

## Prerequisites

- Kubernetes 1.23+
- Helm 3.8+
- Running EMF Control Plane instance
- Kafka cluster for configuration updates
- Redis instance for caching and rate limiting
- OIDC/OAuth2 provider for JWT validation

## Installation

### Quick Start

```bash
# Add the EMF Helm repository (if available)
helm repo add emf https://charts.emf.example.com
helm repo update

# Install with default values
helm install emf-gateway emf/emf-gateway

# Install with custom values
helm install emf-gateway emf/emf-gateway -f values-prod.yaml
```

### Install from Local Chart

```bash
# From the helm directory
helm install emf-gateway . -n emf-system --create-namespace

# With environment-specific values
helm install emf-gateway . -n emf-system -f values-prod.yaml
```

## Configuration

### Required Configuration

The following values must be configured for your environment:

| Parameter | Description | Default |
|-----------|-------------|---------|
| `config.controlPlane.url` | Control plane service URL | `http://emf-control-plane:8080` |
| `config.kafka.bootstrapServers` | Kafka bootstrap servers | `kafka:9092` |
| `config.redis.host` | Redis host | `redis` |
| `config.jwt.issuerUri` | JWT issuer URI | `https://auth.example.com/realms/emf` |

### Common Configuration Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `replicaCount` | Number of gateway replicas | `2` |
| `image.repository` | Container image repository | `emf-gateway` |
| `image.tag` | Container image tag | Chart appVersion |
| `resources.requests.cpu` | CPU request | `500m` |
| `resources.requests.memory` | Memory request | `512Mi` |
| `resources.limits.cpu` | CPU limit | `1000m` |
| `resources.limits.memory` | Memory limit | `1Gi` |
| `autoscaling.enabled` | Enable horizontal pod autoscaling | `false` |
| `autoscaling.minReplicas` | Minimum replicas | `2` |
| `autoscaling.maxReplicas` | Maximum replicas | `10` |
| `ingress.enabled` | Enable ingress | `false` |
| `ingress.hosts` | Ingress hosts | `[gateway.example.com]` |

### Environment-Specific Values

Three environment-specific value files are provided:

#### Local Development (`values-local.yaml`)
- Single replica
- NodePort service
- Debug logging enabled
- Relaxed resource limits
- Higher rate limits for testing

```bash
helm install emf-gateway . -f values-local.yaml
```

#### Development (`values-dev.yaml`)
- 2 replicas with autoscaling (2-5)
- Ingress enabled with staging TLS
- Moderate resource limits
- Debug logging for gateway components

```bash
helm install emf-gateway . -n emf-dev -f values-dev.yaml
```

#### Production (`values-prod.yaml`)
- 3 replicas with autoscaling (3-20)
- Production TLS certificates
- Higher resource limits
- JSON structured logging
- Strong pod anti-affinity
- Production-grade security context

```bash
helm install emf-gateway . -n emf-prod -f values-prod.yaml
```

## Upgrading

```bash
# Upgrade with new values
helm upgrade emf-gateway . -f values-prod.yaml

# Upgrade with specific image tag
helm upgrade emf-gateway . --set image.tag=1.2.0

# Rollback to previous version
helm rollback emf-gateway
```

## Uninstallation

```bash
helm uninstall emf-gateway -n emf-system
```

## Configuration Examples

### Custom Rate Limits

```yaml
config:
  rateLimit:
    default:
      requestsPerWindow: 5000
      windowDuration: "PT1M"  # 1 minute
    enabled: true
```

### Redis with Password

```yaml
config:
  redis:
    host: "redis.example.com"
    port: 6379
    password: "your-redis-password"  # Will be stored in a secret
```

### Multiple Kafka Brokers

```yaml
config:
  kafka:
    bootstrapServers: "kafka-0:9092,kafka-1:9092,kafka-2:9092"
```

### Custom JWT Configuration

```yaml
config:
  jwt:
    issuerUri: "https://auth.example.com/realms/emf"
    jwkSetUri: "https://auth.example.com/realms/emf/protocol/openid-connect/certs"
```

### Ingress with Multiple Hosts

```yaml
ingress:
  enabled: true
  className: "nginx"
  hosts:
    - host: gateway.example.com
      paths:
        - path: /
          pathType: Prefix
    - host: api.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: gateway-tls
      hosts:
        - gateway.example.com
        - api.example.com
```

## Monitoring

### Health Checks

The gateway exposes health check endpoints:

```bash
# Liveness probe
curl http://gateway:8080/actuator/health/liveness

# Readiness probe
curl http://gateway:8080/actuator/health/readiness

# Detailed health
curl http://gateway:8080/actuator/health
```

### Metrics

Prometheus metrics are available at:

```bash
curl http://gateway:8080/actuator/prometheus
```

Key metrics:
- `gateway_requests_total` - Total request count
- `gateway_requests_duration` - Request duration histogram
- `gateway_auth_failures` - Authentication failures
- `gateway_authz_denials` - Authorization denials
- `gateway_ratelimit_exceeded` - Rate limit exceeded count
- `gateway_cache_hits` - Redis cache hits
- `gateway_cache_misses` - Redis cache misses

### Logs

View gateway logs:

```bash
# All logs
kubectl logs -f deployment/emf-gateway -n emf-system

# Follow logs from all pods
kubectl logs -f -l app.kubernetes.io/name=emf-gateway -n emf-system
```

## Troubleshooting

### Gateway Not Starting

Check the logs for startup errors:

```bash
kubectl logs deployment/emf-gateway -n emf-system
```

Common issues:
- Control plane not reachable
- Kafka connection failed
- Redis connection failed
- Invalid JWT issuer URI

### High Memory Usage

Adjust JVM heap settings:

```yaml
env:
  - name: JAVA_OPTS
    value: "-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
```

### Rate Limiting Not Working

Check Redis connectivity:

```bash
kubectl exec -it deployment/emf-gateway -n emf-system -- curl http://localhost:8080/actuator/health/redis
```

### Authentication Failures

Verify JWT configuration:

```bash
# Check issuer URI is accessible
curl https://auth.example.com/realms/emf/.well-known/openid-configuration

# Check gateway logs for JWT validation errors
kubectl logs deployment/emf-gateway -n emf-system | grep JWT
```

## Security Considerations

### Secrets Management

For production, use external secret management:

```yaml
envFrom:
  - secretRef:
      name: emf-gateway-secrets  # Contains Redis password, etc.
```

Create the secret:

```bash
kubectl create secret generic emf-gateway-secrets \
  --from-literal=SPRING_DATA_REDIS_PASSWORD=your-password \
  -n emf-prod
```

### Network Policies

Apply network policies to restrict traffic:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: emf-gateway-policy
spec:
  podSelector:
    matchLabels:
      app.kubernetes.io/name: emf-gateway
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 8080  # Control plane
    - protocol: TCP
      port: 9092  # Kafka
    - protocol: TCP
      port: 6379  # Redis
```

## Support

For issues and questions:
- GitHub: https://github.com/emf-platform/emf-gateway
- Documentation: https://docs.emf.example.com
- Email: platform@emf.example.com

## License

Copyright © 2024 EMF Platform Team
