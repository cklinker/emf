/**
 * Polling helpers for waiting on async operations.
 */

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
