import axios from 'axios';
import { createHash } from 'node:crypto';
import { chmodSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';
import { CliError, EXIT } from '../errors.js';
import { BUILD_TARGET, VERSION } from '../version.js';
import {
  compareVersions,
  currentTargetKey,
  fetchManifest,
  type ReleaseManifest,
} from './manifest.js';

export interface UpdateCheck {
  currentVersion: string;
  latestVersion: string;
  updateAvailable: boolean;
  target: string;
}

export function assertSelfUpdatable(): void {
  if (BUILD_TARGET === 'dev') {
    throw new CliError(
      'This is a dev build (node/dist) — only released binaries self-update. ' +
        'Install a release: https://downloads.kelta.io/cli/install.sh',
      { code: 'NOT_A_RELEASE_BINARY', exitCode: EXIT.USAGE }
    );
  }
}

export async function checkForUpdate(
  baseUrl: string
): Promise<UpdateCheck & { manifest: ReleaseManifest }> {
  const manifest = await fetchManifest(baseUrl);
  return {
    currentVersion: VERSION,
    latestVersion: manifest.version,
    updateAvailable: compareVersions(manifest.version, VERSION) > 0,
    target: currentTargetKey(),
    manifest,
  };
}

/**
 * Remove the `.old` binary a previous Windows update left behind (the running
 * exe cannot be deleted, only renamed — see performUpdate). Safe no-op elsewhere.
 */
export function cleanupPreviousBinary(execPath: string): void {
  try {
    unlinkSync(execPath + '.old');
  } catch {
    // nothing to clean
  }
}

/**
 * Download, verify, and atomically swap the running binary.
 *
 * POSIX: write `<exe>.new` beside the target (same filesystem), chmod 755,
 * rename(2) over the current path — readers either see old or new, never torn.
 * Windows: the running exe cannot be replaced in place, but it CAN be renamed —
 * current → `<exe>.old`, then `.new` → exe; `.old` is deleted on the next run.
 */
export async function performUpdate(
  baseUrl: string,
  log: (message: string) => void,
  execPath = process.execPath
): Promise<UpdateCheck> {
  assertSelfUpdatable();
  cleanupPreviousBinary(execPath);
  const check = await checkForUpdate(baseUrl);
  if (!check.updateAvailable) return check;

  const target = check.manifest.targets[check.target];
  if (!target) {
    throw new CliError(`Release ${check.latestVersion} has no binary for ${check.target}`, {
      code: 'TARGET_NOT_PUBLISHED',
      exitCode: EXIT.API,
    });
  }

  log(
    `Downloading ${check.latestVersion} for ${check.target} (${String(Math.round(target.size / 1_000_000))} MB)…`
  );
  const response = await axios.get<ArrayBuffer>(`${baseUrl}/${target.path.replace(/^\//, '')}`, {
    responseType: 'arraybuffer',
    timeout: 300_000,
  });
  const binary = Buffer.from(response.data);

  const digest = createHash('sha256').update(binary).digest('hex');
  if (digest !== target.sha256) {
    throw new CliError(
      `Checksum mismatch for ${check.target} — expected ${target.sha256}, got ${digest}. Aborting.`,
      { code: 'CHECKSUM_MISMATCH', exitCode: EXIT.API }
    );
  }

  const staging = execPath + '.new';
  writeFileSync(staging, binary);
  chmodSync(staging, 0o755);
  if (process.platform === 'win32') {
    renameSync(execPath, execPath + '.old');
    renameSync(staging, execPath);
  } else {
    renameSync(staging, execPath);
  }
  return check;
}
