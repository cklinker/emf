import { test, expect } from "@playwright/test";
import { spawnSync } from "child_process";
import * as path from "path";
import * as fs from "fs";
import { fileURLToPath } from "url";

/**
 * Dispatcher repo-registry — validates `queue_resolve_repo` and
 * `queue_repo_default_branch` in `.claude/dispatcher/lib/queue.sh` by running
 * the shell unit test in `.claude/dispatcher/tests/queue-repo-test.sh`.
 *
 * These helpers gate every worker's worktree — a broken resolver would silently
 * retarget a task at the wrong repo. Runs bash directly (no browser); is a
 * cross-check so this behavior travels in the e2e-tests suite too.
 *
 * Skipped by default because Playwright's CI project loads Kelta auth
 * fixtures, and the runner box may not have `bash` on the resolved PATH. The
 * check runs in the shell test directly (`bash .claude/dispatcher/tests/queue-repo-test.sh`)
 * as part of dispatcher-repo development.
 */
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "../../..");
const SHELL_TEST = path.join(
  REPO_ROOT,
  ".claude/dispatcher/tests/queue-repo-test.sh",
);

test.describe("dispatcher: repo registry", () => {
  test.skip(
    !fs.existsSync(SHELL_TEST),
    "shell test not present in this checkout",
  );

  test("queue_resolve_repo + queue_repo_default_branch pass their unit tests", () => {
    const result = spawnSync("bash", [SHELL_TEST], {
      encoding: "utf8",
      timeout: 30_000,
    });
    if (result.status !== 0) {
      throw new Error(
        `queue-repo-test.sh exited ${result.status}\nstdout:\n${result.stdout}\nstderr:\n${result.stderr}`,
      );
    }
    expect(result.stdout).toContain("passed, 0 failed");
  });
});
