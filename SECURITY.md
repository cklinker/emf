# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in the Kelta Platform, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please email security concerns to the maintainers directly. Include:

1. A description of the vulnerability
2. Steps to reproduce the issue
3. Potential impact assessment
4. Suggested fix (if any)

We will acknowledge receipt within 48 hours and provide a detailed response within 5 business days.

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest  | Yes       |

## Security Measures

- Authentication via OIDC (kelta-auth) with identity brokering and MFA support
- Authorization policies enforced via Cerbos
- API rate limiting at the gateway layer
- Webhook signature verification via Svix
- Secrets managed through environment variables, never committed to source
- Dependency updates monitored via Dependabot
- Pre-commit hooks scan for accidentally committed secrets
