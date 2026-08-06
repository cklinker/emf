import { describe, it, expect } from 'vitest';
import { challengeS256, generateState, generateVerifier } from './pkce.js';

describe('pkce', () => {
  it('reproduces the RFC 7636 Appendix B S256 vector', () => {
    expect(challengeS256('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe(
      'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM'
    );
  });

  it('generates 43-char base64url verifiers (32 random bytes)', () => {
    const verifier = generateVerifier();
    expect(verifier).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(generateVerifier()).not.toBe(verifier);
  });

  it('generates unique url-safe state values', () => {
    const state = generateState();
    expect(state).toMatch(/^[A-Za-z0-9_-]+$/);
    expect(generateState()).not.toBe(state);
  });
});
