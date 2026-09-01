import { test, expect } from "@playwright/test";
import { spawnSync } from "child_process";
import * as path from "path";
import * as fs from "fs";
import { fileURLToPath } from "url";

/**
 * Dispatcher Slack notifications — validates notify_slack() in
 * `.claude/dispatcher/lib/notify.sh` by running the shell unit test in
 * `.claude/dispatcher/tests/notify-test.sh`.
 *
 * Tests error-handling paths (missing sops dir, caller resilience under
 * set -uo pipefail) and a stub sops+curl happy path that validates the
 * JSON payload shape without real credentials.
 *
 * Skipped when the shell test is absent (different checkout / CI matrix
 * that doesn't include dispatcher files).
 */
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "../../..");
const SHELL_TEST = path.join(
  REPO_ROOT,
  ".claude/dispatcher/tests/notify-test.sh",
);

test.describe("dispatcher: slack notifications", () => {
  test.skip(
    !fs.existsSync(SHELL_TEST),
    "shell test not present in this checkout",
  );

  test("notify_slack error handling and payload shape pass their unit tests", () => {
    const result = spawnSync("bash", [SHELL_TEST], {
      encoding: "utf8",
      timeout: 30_000,
    });
    if (result.status !== 0) {
      throw new Error(
        `notify-test.sh exited ${result.status}\nstdout:\n${result.stdout}\nstderr:\n${result.stderr}`,
      );
    }
    expect(result.stdout).toContain("passed, 0 failed");
  });
});
