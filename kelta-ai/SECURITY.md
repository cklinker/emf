# Security Policy

See the root [../SECURITY.md](../SECURITY.md) for the general security policy, responsible disclosure process, and supported versions.

## AI Service — Additional Notes

### API Key Handling

- `ANTHROPIC_API_KEY` must be provided via environment variable only — never hardcoded, never committed.
- The key must not appear in logs, error messages, or API responses.
- Rotate the key immediately if exposure is suspected; contact Anthropic to revoke the compromised key.
- In Kubernetes, the key is stored in a sealed secret and injected at pod startup.

### Prompt Injection

- User-supplied input is treated as untrusted. It is passed as the `user` role in the messages array and never interpolated directly into the system prompt.
- System prompts are constructed server-side from validated, tenant-specific schema data only.
- Responses from Claude are not evaluated as code.

### Tenant Isolation

- Every request is scoped to a single tenant via `TenantContext`.
- Schema context, conversation history, and cached state are keyed by tenant ID and user ID.
- Cross-tenant data access in prompt context is not possible by design.

### Secrets Scanning

This repository uses `gitleaks` (see `.gitleaks.toml`) to prevent accidental secret commits in CI.
