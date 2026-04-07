# Security Policy

See the [root SECURITY.md](../SECURITY.md) for the full security policy and vulnerability reporting process.

## Auth-Specific Security Notes

- OAuth2 tokens are signed with RSA keys managed by Spring Authorization Server
- Passwords are hashed with BCrypt
- TOTP secrets are encrypted at rest using Kelta's encryption service
- Sessions are stored in Redis with configurable TTL
- CSRF protection enabled for all form-based endpoints
