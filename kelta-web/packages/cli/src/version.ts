/**
 * CLI version. `KELTA_CLI_VERSION` is injected at binary-build time (bun
 * `--define`, distribution slice); the literal fallback is the source of truth
 * for dev builds and MUST match package.json (enforced by version.test.ts).
 */
declare const KELTA_CLI_VERSION: string | undefined;

export const VERSION: string =
  typeof KELTA_CLI_VERSION !== 'undefined' && KELTA_CLI_VERSION ? KELTA_CLI_VERSION : '0.1.0';
