import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { configDir } from '../config/paths.js';
import { BUILD_TARGET, VERSION } from '../version.js';
import { compareVersions, fetchManifest, updateBaseUrl } from './manifest.js';

const CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000;
/** Passive check must never make a command feel slow. */
const PASSIVE_TIMEOUT_MS = 750;

function stampPath(): string {
  return join(configDir(), 'update-check.json');
}

function throttleDue(now: number): boolean {
  try {
    const stamp = JSON.parse(readFileSync(stampPath(), 'utf-8')) as { checkedAt?: number };
    return now - (stamp.checkedAt ?? 0) >= CHECK_INTERVAL_MS;
  } catch {
    return true;
  }
}

/**
 * At most once per 24h, on a TTY, released builds only: fetch the manifest
 * (750 ms budget) and print a one-line stderr hint when a newer version
 * exists. Never auto-installs. Disable with KELTA_UPDATE_CHECK=0.
 */
export async function maybeNotifyUpdate(log: (message: string) => void): Promise<void> {
  if (BUILD_TARGET === 'dev') return;
  if (!process.stderr.isTTY) return;
  const flag = process.env.KELTA_UPDATE_CHECK;
  if (flag === '0' || flag === 'false') return;
  const now = Date.now();
  if (!throttleDue(now)) return;

  // stamp BEFORE the network call: a failing downloads host must not turn
  // every command into a daily retry storm
  try {
    writeFileSync(stampPath(), JSON.stringify({ checkedAt: now }));
  } catch {
    return; // no config dir → skip quietly
  }

  try {
    const manifest = await fetchManifest(updateBaseUrl(), PASSIVE_TIMEOUT_MS);
    if (compareVersions(manifest.version, VERSION) > 0) {
      log(`A new kelta version is available: ${VERSION} → ${manifest.version}. Run: kelta update`);
    }
  } catch {
    // offline or slow — stay silent
  }
}
