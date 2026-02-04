# EMF Gateway Deployment Guide

This guide provides instructions for building and deploying the EMF API Gateway using Docker and Helm.

## Building the Docker Image

### Prerequisites

- Docker 20.10+
- Maven 3.8+ (for local builds)
- Java 21 (for local builds)

### Build Steps

#### Option 1: Build with Docker (Recommended)

The Dockerfile includes a multi-stage build that compiles the application:

```bash
# From the emf-gateway directory
docker build -t emf-gateway:latest .

# Build with specific tag
docker build -t emf-gateway:1.0.0 .

# Build and push to registry
docker build -t registry.example.com/emf-gateway:1.0.0 .
docker push registry.example.com/emf-gateway:1.0.0
```

#### Option 2: Build JAR First, Then Docker Image

```bash
# Build the application with Maven
mvn clean package -DskipTests

# Build Docker image (using pre-built JAR)
docker build -t emf-gateway:latest .
```

### Test the Docker Image Locally

```bash
# Run the container
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e EMF_GATEWAY_CONTROL_PLANE_URL=http://host.docker.internal:8081 \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  emf-gateway:latest

# Check health
curl http://localhost:8080/actuator/health
```

## Deploying with Helm

### Prerequisites

- Kubernetes cluster (1.23+)
- Helm 3.8+
- kubectl configured to access your cluster
- Running instances of:
  - EMF Control Plane
  - Kafka
  - Redis
  - OIDC/OAuth2 provider

### Deployment Steps

#### 1. Prepare Your Environment

```bash
# Create namespace
kubectl create namespace emf-system

# Create image pull secret (if using private registry)
kubectl create secret docker-registry registry-credentials \
  --docker-server=registry.example.com \
  --docker-username=your-username \
  --docker-password=your-password \
  --docker-email=your-email@example.com \
  -n emf-system
```

#### 2. Configure Values

Create a custom values file or use one of the provided environment-specific files:

```bash
# Copy and customize for your environment
cp helm/values-prod.yaml my-values.yaml

# Edit the file to match your environment
vim my-values.yaml
```

Key values to configure:
- `image.repository` and `image.tag`
- `config.controlPlane.url`
- `config.kafka.bootstrapServers`
- `config.redis.host`
- `config.jwt.issuerUri`
- `ingress.hosts`

#### 3. Install the Chart

```bash
# Install with default values
helm install emf-gateway ./helm -n emf-system

# Install with custom values
helm install emf-gateway ./helm -n emf-system -f my-values.yaml

# Install with inline overrides
helm install emf-gateway ./helm -n emf-system \
  --set image.tag=1.0.0 \
  --set replicaCount=3
```

#### 4. Verify Deployment

```bash
# Check pod status
kubectl get pods -n emf-system -l app.kubernetes.io/name=emf-gateway

# Check logs
kubectl logs -f deployment/emf-gateway -n emf-system

# Check health
kubectl exec -it deployment/emf-gateway -n emf-system -- \
  curl http://localhost:8080/actuator/health

# Check service
kubectl get svc emf-gateway -n emf-system
```

#### 5. Access the Gateway

```bash
# If using Ingress
curl https://gateway.example.com/actuator/health

# If using port-forward for testing
kubectl port-forward svc/emf-gateway 8080:8080 -n emf-system
curl http://localhost:8080/actuator/health
```

## Environment-Specific Deployments

### Local Development (Minikube/Docker Desktop)

```bash
# Build image locally
docker build -t emf-gateway:latest .

# For Minikube, load image into Minikube
minikube image load emf-gateway:latest

# Deploy with local values
helm install emf-gateway ./helm -f helm/values-local.yaml

# Access via NodePort
kubectl get svc emf-gateway
# Note the NodePort and access via http://localhost:<nodeport>
```

### Development Environment

```bash
# Build and push to dev registry
docker build -t registry.dev.emf.example.com/emf-gateway:dev-latest .
docker push registry.dev.emf.example.com/emf-gateway:dev-latest

# Deploy to dev cluster
kubectl config use-context dev-cluster
helm install emf-gateway ./helm -n emf-dev -f helm/values-dev.yaml

# Verify
kubectl get ingress -n emf-dev
curl https://gateway.dev.emf.example.com/actuator/health
```

### Production Environment

```bash
# Build and push to prod registry with version tag
VERSION=1.0.0
docker build -t registry.prod.emf.example.com/emf-gateway:${VERSION} .
docker push registry.prod.emf.example.com/emf-gateway:${VERSION}

# Deploy to prod cluster
kubectl config use-context prod-cluster

# Create secrets first
kubectl create secret generic emf-gateway-secrets \
  --from-literal=SPRING_DATA_REDIS_PASSWORD=your-secure-password \
  -n emf-prod

# Deploy with production values
helm install emf-gateway ./helm -n emf-prod -f helm/values-prod.yaml \
  --set image.tag=${VERSION}

# Verify deployment
kubectl get pods -n emf-prod -l app.kubernetes.io/name=emf-gateway
kubectl get ingress -n emf-prod

# Test
curl https://gateway.emf.example.com/actuator/health
```

## Upgrading

### Rolling Update

```bash
# Build new version
VERSION=1.1.0
docker build -t registry.example.com/emf-gateway:${VERSION} .
docker push registry.example.com/emf-gateway:${VERSION}

# Upgrade with Helm
helm upgrade emf-gateway ./helm -n emf-system \
  --set image.tag=${VERSION} \
  -f my-values.yaml

# Monitor rollout
kubectl rollout status deployment/emf-gateway -n emf-system

# Check new pods
kubectl get pods -n emf-system -l app.kubernetes.io/name=emf-gateway
```

### Rollback

```bash
# Rollback to previous version
helm rollback emf-gateway -n emf-system

# Rollback to specific revision
helm rollback emf-gateway 2 -n emf-system

# Check rollout status
kubectl rollout status deployment/emf-gateway -n emf-system
```

## Scaling

### Manual Scaling

```bash
# Scale replicas
kubectl scale deployment emf-gateway --replicas=5 -n emf-system

# Or via Helm
helm upgrade emf-gateway ./helm -n emf-system \
  --set replicaCount=5 \
  -f my-values.yaml
```

### Auto-scaling

Enable HPA in values:

```yaml
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 20
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80
```

Apply:

```bash
helm upgrade emf-gateway ./helm -n emf-system -f my-values.yaml

# Check HPA status
kubectl get hpa -n emf-system
kubectl describe hpa emf-gateway -n emf-system
```

## Monitoring

### View Logs

```bash
# All pods
kubectl logs -f -l app.kubernetes.io/name=emf-gateway -n emf-system

# Specific pod
kubectl logs -f emf-gateway-<pod-id> -n emf-system

# Previous container (if crashed)
kubectl logs emf-gateway-<pod-id> -n emf-system --previous
```

### Check Metrics

```bash
# Prometheus metrics
kubectl port-forward svc/emf-gateway 8080:8080 -n emf-system
curl http://localhost:8080/actuator/prometheus

# Pod metrics
kubectl top pods -n emf-system -l app.kubernetes.io/name=emf-gateway
```

### Health Checks

```bash
# Liveness
kubectl exec -it deployment/emf-gateway -n emf-system -- \
  curl http://localhost:8080/actuator/health/liveness

# Readiness
kubectl exec -it deployment/emf-gateway -n emf-system -- \
  curl http://localhost:8080/actuator/health/readiness

# Detailed health
kubectl exec -it deployment/emf-gateway -n emf-system -- \
  curl http://localhost:8080/actuator/health
```

## Troubleshooting

### Pod Not Starting

```bash
# Check pod status
kubectl describe pod emf-gateway-<pod-id> -n emf-system

# Check events
kubectl get events -n emf-system --sort-by='.lastTimestamp'

# Check logs
kubectl logs emf-gateway-<pod-id> -n emf-system
```

### Connection Issues

```bash
# Test control plane connectivity
kubectl exec -it deployment/emf-gateway -n emf-system -- \
  curl http://emf-control-plane:8080/control/bootstrap

# Test Redis connectivity
kubectl exec -it deployment/emf-gateway -n emf-system -- \
  curl http://localhost:8080/actuator/health/redis

# Test Kafka connectivity
kubectl exec -it deployment/emf-gateway -n emf-system -- \
  curl http://localhost:8080/actuator/health/kafka
```

### Performance Issues

```bash
# Check resource usage
kubectl top pods -n emf-system -l app.kubernetes.io/name=emf-gateway

# Check HPA status
kubectl get hpa emf-gateway -n emf-system

# Increase resources
helm upgrade emf-gateway ./helm -n emf-system \
  --set resources.requests.cpu=1000m \
  --set resources.requests.memory=1Gi \
  --set resources.limits.cpu=2000m \
  --set resources.limits.memory=2Gi
```

## Cleanup

### Uninstall

```bash
# Uninstall Helm release
helm uninstall emf-gateway -n emf-system

# Delete namespace (if no other resources)
kubectl delete namespace emf-system
```

### Remove Docker Images

```bash
# Remove local images
docker rmi emf-gateway:latest
docker rmi registry.example.com/emf-gateway:1.0.0

# Clean up build cache
docker builder prune
```

## CI/CD Integration

### Example GitHub Actions Workflow

```yaml
name: Build and Deploy

on:
  push:
    branches: [main]
    tags: ['v*']

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2
      
      - name: Login to Registry
        uses: docker/login-action@v2
        with:
          registry: registry.example.com
          username: ${{ secrets.REGISTRY_USERNAME }}
          password: ${{ secrets.REGISTRY_PASSWORD }}
      
      - name: Build and push
        uses: docker/build-push-action@v4
        with:
          context: .
          push: true
          tags: registry.example.com/emf-gateway:${{ github.sha }}
  
  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Install Helm
        uses: azure/setup-helm@v3
      
      - name: Deploy to Kubernetes
        run: |
          helm upgrade --install emf-gateway ./helm \
            -n emf-prod \
            -f helm/values-prod.yaml \
            --set image.tag=${{ github.sha }}
```

## Support

For issues and questions:
- GitHub: https://github.com/emf-platform/emf-gateway
- Documentation: https://docs.emf.example.com
- Email: platform@emf.example.com
