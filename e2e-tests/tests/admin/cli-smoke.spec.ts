import { test, expect } from "@playwright/test";
import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Smoke test for the kelta CLI against the deployed stack.
 *
 * Uses the pure env-override auth path (KELTA_URL/KELTA_TENANT/KELTA_TOKEN)
 * with the pre-issued E2E PAT — no login flow needed. The CLI must be built
 * first (the e2e workflow builds kelta-web/packages/cli); when the dist is
 * absent (local run without a build) the suite skips.
 */

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const CLI_ENTRY = path.resolve(
  __dirname,
  "../../../kelta-web/packages/cli/dist/index.js",
);

const API_BASE = process.env.E2E_API_BASE_URL || "https://api.kelta.io";
const TENANT = process.env.E2E_TENANT_SLUG || "default";
const TOKEN = process.env.E2E_API_TOKEN;

interface CliResult {
  status: number | null;
  stdout: string;
  stderr: string;
}

function runCli(args: string[], extraEnv: Record<string, string | undefined> = {}): CliResult {
  const result = spawnSync(process.execPath, [CLI_ENTRY, ...args], {
    encoding: "utf-8",
    timeout: 30_000,
    env: {
      ...process.env,
      KELTA_CONFIG_DIR: configDir,
      KELTA_URL: API_BASE,
      KELTA_TENANT: TENANT,
      KELTA_TOKEN: TOKEN,
      ...extraEnv,
    },
  });
  return { status: result.status, stdout: result.stdout, stderr: result.stderr };
}

let configDir: string;

test.describe("kelta CLI smoke", () => {
  test.skip(!existsSync(CLI_ENTRY), "CLI dist not built");
  test.skip(!TOKEN, "E2E_API_TOKEN not set");

  test.beforeAll(() => {
    configDir = mkdtempSync(path.join(tmpdir(), "kelta-cli-e2e-"));
  });

  test.afterAll(() => {
    rmSync(configDir, { recursive: true, force: true });
  });

  test("version prints and exits 0", () => {
    const result = runCli(["--version"]);
    expect(result.status).toBe(0);
    expect(result.stdout.trim()).toMatch(/^\d+\.\d+\.\d+$/);
  });

  test("collections list returns flattened JSON rows", () => {
    const result = runCli(["collections", "list", "--output", "json"]);
    expect(result.status, result.stderr).toBe(0);
    const rows = JSON.parse(result.stdout) as { id?: string; name?: string }[];
    expect(Array.isArray(rows)).toBe(true);
    expect(rows.length).toBeGreaterThan(0);
    expect(rows[0]).toHaveProperty("id");
  });

  test("records list works against a system collection with pagination", () => {
    const result = runCli(["records", "list", "collections", "--size", "1", "--output", "json"]);
    expect(result.status, result.stderr).toBe(0);
    const rows = JSON.parse(result.stdout) as unknown[];
    expect(Array.isArray(rows)).toBe(true);
    expect(rows.length).toBeLessThanOrEqual(1);
  });

  test("--raw returns the JSON:API envelope", () => {
    const result = runCli(["collections", "list", "--output", "json", "--raw"]);
    expect(result.status, result.stderr).toBe(0);
    const body = JSON.parse(result.stdout) as { data?: unknown[] };
    expect(Array.isArray(body.data)).toBe(true);
  });

  test("missing auth fails with the machine-readable error contract and exit 3", () => {
    const result = runCli(["collections", "list", "--output", "json"], {
      KELTA_TOKEN: undefined,
      KELTA_URL: undefined,
      KELTA_TENANT: undefined,
    });
    expect(result.status).toBe(3);
    const payload = JSON.parse(result.stderr) as { error: { code: string } };
    expect(payload.error.code).toBe("AUTH_REQUIRED");
  });

  test("destructive command without --yes is refused off-TTY with exit 2", () => {
    const result = runCli(["records", "delete", "collections", "nonexistent", "--output", "json"]);
    expect(result.status).toBe(2);
    const payload = JSON.parse(result.stderr) as { error: { code: string } };
    expect(payload.error.code).toBe("CONFIRMATION_REQUIRED");
  });
});
