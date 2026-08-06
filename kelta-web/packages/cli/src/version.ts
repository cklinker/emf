/**
 * CLI build identity. The `KELTA_CLI_*` globals are injected at binary-build
 * time (bun `--define`, see scripts/build-binaries.mjs); the fallbacks are the
 * source of truth for dev builds — VERSION must match package.json (enforced
 * by version.test.ts). TARGET === 'dev' also disables self-update: only
 * released, compiled binaries may replace themselves.
 */
declare const KELTA_CLI_VERSION: string | undefined;
declare const KELTA_CLI_SHA: string | undefined;
declare const KELTA_CLI_TARGET: string | undefined;

export const VERSION: string =
  typeof KELTA_CLI_VERSION !== 'undefined' && KELTA_CLI_VERSION ? KELTA_CLI_VERSION : '0.1.0';

export const GIT_SHA: string =
  typeof KELTA_CLI_SHA !== 'undefined' && KELTA_CLI_SHA ? KELTA_CLI_SHA : 'dev';

/** `<os>-<arch>` of a released binary (e.g. darwin-arm64), or 'dev'. */
export const BUILD_TARGET: string =
  typeof KELTA_CLI_TARGET !== 'undefined' && KELTA_CLI_TARGET ? KELTA_CLI_TARGET : 'dev';
