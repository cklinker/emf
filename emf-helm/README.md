# EMF Helm Charts

This repository contains Helm charts for deploying EMF (Enterprise Microservice Framework) platform components.

## Available Charts

| Chart | Description | Version |
|-------|-------------|---------|
| [emf-control-plane](./charts/emf-control-plane) | Central configuration management service | 0.1.0 |

## Prerequisites

- Kubernetes 1.23+
- Helm 3.8+

## Usage

### Add the Helm Repository

```bash
helm repo add emf https://charts.emf.io
helm repo update
```

### Install a Chart

```bash
helm install <release-name> emf/<chart-name> --namespace <namespace> --create-namespace
```

### Example: Install Control Plane

```bash
helm install emf-control-plane emf/emf-control-plane \
  --namespace emf \
  --create-namespace \
  --set database.host=postgresql \
  --set secrets.dbPassword=$(echo -n 'your-password' | base64)
```

## Development

### Lint Charts

```bash
helm lint charts/*
```

### Template Charts

```bash
helm template emf-control-plane charts/emf-control-plane
```

### Package Charts

```bash
helm package charts/emf-control-plane
```

## Repository Structure

```
emf-helm/
├── charts/
│   ├── emf-control-plane/    # Control Plane Service chart
│   ├── emf-ui/               # UI chart (coming soon)
│   └── emf-service/          # Domain Service chart (coming soon)
└── README.md
```

## License

Copyright © EMF Team. All rights reserved.
