# Security Policy — kelta-worker

See the root [../SECURITY.md](../SECURITY.md) for the full security policy, vulnerability reporting process, and supported versions.

## Worker-Specific Security Notes

### Database Access

- The worker connects to PostgreSQL using credentials supplied via environment variables (`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). Never hard-code credentials.
- Flyway runs DDL migrations on startup — the database user requires DDL privileges in development. In production, use a dedicated migration user with restricted access after initial setup.
- All tenant data is co-located in the same database. Row-level isolation is enforced at the application layer; no PostgreSQL RLS is currently applied. Queries must always include tenant scoping.

### Kafka Security

- In production, Kafka connections must use TLS (`security.protocol=SSL`) and SASL authentication. Configure via `SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL` and related SASL environment variables.
- The worker publishes sensitive record change events to `kelta.record.changed`. Ensure Kafka ACLs restrict topic access to authorized services only.
- Never log full Kafka message payloads — they may contain PII.

### Secret Management

- Use `.env.example` as a reference; never commit `.env` or any file containing real credentials.
- In Kubernetes, secrets are injected via K8s `Secret` objects mounted as environment variables.
- Rotate `SVIX_AUTH_TOKEN` and AWS credentials via the homelab secrets rotation procedure.

### Authorization

- All collection record operations are authorized via Cerbos. Do not bypass Cerbos checks for internal routes without explicit justification.
- Internal endpoints (`/internal/**`) are accessible only from within the cluster (gateway-to-worker). Verify network policy enforces this in production.

### Dependency Scanning

- Dependabot is configured at the repo root. Review security alerts before merging dependency updates.
- Run `mvn dependency:check` (OWASP) periodically for CVE scanning.
