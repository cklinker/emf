# EMF Monorepo

This repository contains the complete EMF (Enterprise Microservice Framework) platform as a unified monorepo.

## Repository Structure

```
emf/
├── emf-platform/          # Core backend framework (Maven artifacts, runtime libs)
├── emf-control-plane/     # Control plane service (Docker + Helm)
├── emf-ui/                # React admin/builder UI (static build + Docker)
├── emf-gateway/           # API Gateway service (Docker + Helm)
├── emf-web/               # TypeScript SDK + React components (npm packages)
├── emfctl/                # CLI for promotion/migrations
├── emf-helm/              # Infrastructure Helm charts
├── emf-docs/              # Documentation site
├── sample-service/        # Sample service implementation
├── scripts/               # Development and testing scripts
└── docker/                # Docker configurations
```

## Monorepo Benefits

- **Unified versioning**: All components versioned together
- **Atomic changes**: Cross-component changes in single commits
- **Simplified CI/CD**: Single pipeline for all components
- **Easier development**: All code in one place
- **Consistent tooling**: Shared configurations and scripts

## Getting Started

See individual component READMEs for specific setup instructions:
- [emf-platform](./emf-platform/README.md)
- [emf-control-plane](./emf-control-plane/README.md)
- [emf-ui](./emf-ui/README.md)
- [emf-gateway](./emf-gateway/README.md)
- [emf-web](./emf-web/README.md)
- [emfctl](./emfctl/README.md)

## Development

### Prerequisites
- Java 17+
- Node.js 18+
- Maven 3.8+
- Docker & Docker Compose

### Quick Start
```bash
# Start all services
docker-compose up -d

# Run integration tests
./scripts/run-integration-tests.sh
```

## Migration from Multi-Repo

This repository was consolidated from multiple independent repositories. Each subdirectory maintains its original structure and can still be built/tested independently.

## License

See individual component licenses.
