/**
 * Secure-context-safe UUID v4 generator.
 *
 * `crypto.randomUUID()` only exists in a secure context (HTTPS or
 * http://localhost). The e2e compose stack serves the SPA over plain
 * HTTP at http://kelta-ui:8080 (container DNS, not localhost), where
 * `crypto.randomUUID` is `undefined` — calling it throws
 * "crypto.randomUUID is not a function" and crashes the component tree
 * into the error boundary ("Something went wrong").
 *
 * Prefer the native implementation when available, fall back to
 * `crypto.getRandomValues` (available in any context), and finally to
 * Math.random for non-browser/test environments.
 */
export function uuid(): string {
  const c: Crypto | undefined =
    typeof globalThis !== 'undefined' ? globalThis.crypto : undefined;

  if (c && typeof c.randomUUID === 'function') {
    return c.randomUUID();
  }

  if (c && typeof c.getRandomValues === 'function') {
    const bytes = c.getRandomValues(new Uint8Array(16));
    // Per RFC 4122 §4.4: set version (4) and variant (10xx) bits.
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0'));
    return (
      hex.slice(0, 4).join('') +
      '-' +
      hex.slice(4, 6).join('') +
      '-' +
      hex.slice(6, 8).join('') +
      '-' +
      hex.slice(8, 10).join('') +
      '-' +
      hex.slice(10, 16).join('')
    );
  }

  // Last resort (non-crypto): only reached in environments without Web
  // Crypto at all. Not cryptographically strong but collision-safe enough
  // for client-side element/block ids.
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (ch) => {
    const r = (Math.random() * 16) | 0;
    const v = ch === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
