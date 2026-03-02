/**
 * Polling helpers for waiting on async operations.
 */

import type { Locator } from "@playwright/test";

export interface PollOptions {
  intervalMs?: number;
  timeoutMs?: number;
}

/**
 * Poll a function until it returns a truthy value or times out.
 */
export async function pollUntil<T>(
  fn: () => Promise<T>,
  options: PollOptions = {},
): Promise<T> {
  const { intervalMs = 1000, timeoutMs = 30_000 } = options;
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    const result = await fn();
    if (result) return result;
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }

  throw new Error(`Polling timed out after ${timeoutMs}ms`);
}

/**
 * Wait for any of the given locators to become visible.
 * Uses Promise.race with waitFor to properly auto-wait.
 * Returns true if any became visible, false if all timed out.
 */
export async function waitForAnyVisible(
  locators: Locator[],
  timeout = 10_000,
): Promise<boolean> {
  try {
    await Promise.race(
      locators.map((loc) => loc.waitFor({ state: "visible", timeout })),
    );
    return true;
  } catch {
    return false;
  }
}
