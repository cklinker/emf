import { createHash, randomBytes } from 'node:crypto';

/** RFC 7636 code verifier: 43-char base64url string from 32 random bytes. */
export function generateVerifier(): string {
  return randomBytes(32).toString('base64url');
}

/** RFC 7636 S256 code challenge for a verifier. */
export function challengeS256(verifier: string): string {
  return createHash('sha256').update(verifier, 'ascii').digest('base64url');
}

/** Opaque CSRF state parameter. */
export function generateState(): string {
  return randomBytes(16).toString('base64url');
}
