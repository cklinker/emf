---
inclusion: always
---

# EMF Monorepo Structure

This workspace is a unified monorepo containing all EMF (Enterprise Microservice Framework) platform components in a single GitHub repository at `git@github.com:cklinker/emf.git`.

## Repository Overview

Each root directory is a component of the EMF platform:

### Platform-Owned Repositories

**emf-platform** - Core backend framework
- Spring Boot starters, runtime registry/router/query engine, plugin SDK, adapters
- Publishes Maven artifacts (BOM + starters + runtime libs) and Docker base images
- Structure: `bom/`, `starters/`, `runtime/`, `control-plane-lib/`, `plugin-sdk/`, `adapters/`, `examples/`

**emf-control-plane** - Runnable control plane service
- Deployable control plane application (collections/authz/ui/packaging/migrations)
- Ships Docker image + Helm charts
- Structure: `app/`, `helm/`, `docs/`

**emf-ui** - React admin/builder UI
- Self-configuring UI that boots from `/ui/config/bootstrap`
- Ships static build + Docker image
- Structure: `app/`, `plugins/`, `helm/`

**emf-gateway** - API Gateway service
- Service router, authentication, authorization, rate limiting
- Main ingress point for all EMF-based applications
- Ships Docker image + Helm charts
- Structure: Gateway configuration and routing logic

**emf-web** - TypeScript SDK + React components
- Publishes to npm: `@emf/sdk`, `@emf/components`, `@emf/plugin-sdk`
- Structure: `packages/sdk/`, `packages/components/`, `packages/plugin-sdk/`

**emfctl** - CLI for promotion/migrations
- Export/import packages, environment promotion, data moves
- Standalone tool (Go, Node, or Java)
- Structure: `cmd/`, `docs/`

**emf-helm** - Infrastructure templates
- Base Helm charts versioned separately from code
- Charts for: control plane, UI, domain services
- Structure: `charts/emf-control-plane/`, `charts/emf-ui/`, `charts/emf-service/`

**emf-docs** - Documentation site
- Docs site + ADRs + contribution guides + reference architecture
- Structure: documentation files and guides

## Working in the Monorepo

Benefits of the monorepo structure:
- **Unified versioning**: All components share the same Git history
- **Atomic changes**: Cross-component changes can be made in single commits
- **Simplified coordination**: No need to manage dependencies across multiple repos
- **Easier testing**: Integration tests can span all components
- **Single CI/CD pipeline**: Build and deploy all components together

## Component Independence

While unified in a single repo, each component:
- Can still be built and tested independently
- Maintains its own build configuration (Maven, npm, etc.)
- Publishes its own artifacts (Maven, npm, Docker)
- Has clear boundaries and responsibilities
- Minimum viable platform set: emf-platform, emf-control-plane, emf-ui, emf-web, emfctl
