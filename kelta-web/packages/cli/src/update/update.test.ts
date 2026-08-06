// @vitest-environment node
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createHash } from 'node:crypto';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

vi.mock('axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }));
vi.mock('../version.js', () => ({
  VERSION: '1.0.5',
  GIT_SHA: 'abc1234',
  BUILD_TARGET: `${process.platform === 'win32' ? 'windows' : process.platform}-${process.arch}`,
}));

import axios from 'axios';
import {
  compareVersions,
  currentTargetKey,
  fetchManifest,
  updateBaseUrl,
  DEFAULT_UPDATE_URL,
} from './manifest.js';
import { checkForUpdate, cleanupPreviousBinary, performUpdate } from './selfUpdate.js';
import { maybeNotifyUpdate } from './passiveCheck.js';
import { setConfigDirForTesting } from '../config/paths.js';

const get = vi.mocked(axios.get);

// Buffer.from() uses a pooled slab — .buffer would be the whole pool, not the bytes
function toArrayBuffer(buffer: Buffer): ArrayBuffer {
  return buffer.buffer.slice(
    buffer.byteOffset,
    buffer.byteOffset + buffer.byteLength
  ) as ArrayBuffer;
}

function manifestFor(version: string, binary: Buffer) {
  const key = currentTargetKey();
  return {
    manifestVersion: 1,
    version,
    gitSha: 'def5678',
    builtAt: '2026-08-06T12:00:00Z',
    targets: {
      [key]: {
        path: `cli/releases/${version}/kelta-${key}`,
        url: `https://downloads.kelta.io/cli/releases/${version}/kelta-${key}`,
        sha256: createHash('sha256').update(binary).digest('hex'),
        size: binary.length,
      },
    },
  };
}

beforeEach(() => {
  get.mockReset();
  delete process.env.KELTA_UPDATE_URL;
  delete process.env.KELTA_UPDATE_CHECK;
});

describe('manifest utils', () => {
  it('compares dotted triples numerically (not lexically)', () => {
    expect(compareVersions('1.0.10', '1.0.9')).toBeGreaterThan(0);
    expect(compareVersions('1.0.5', '1.0.5')).toBe(0);
    expect(compareVersions('0.9.9', '1.0.0')).toBeLessThan(0);
  });

  it('updateBaseUrl honors KELTA_UPDATE_URL', () => {
    expect(updateBaseUrl()).toBe(DEFAULT_UPDATE_URL);
    process.env.KELTA_UPDATE_URL = 'https://mirror.example/';
    expect(updateBaseUrl()).toBe('https://mirror.example');
  });

  it('rejects malformed manifests', async () => {
    get.mockResolvedValue({ data: { version: 'not-semver' } });
    await expect(fetchManifest('https://x')).rejects.toMatchObject({ code: 'MALFORMED_MANIFEST' });
  });
});

describe('performUpdate', () => {
  let dir: string;
  let exe: string;

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), 'kelta-update-'));
    exe = join(dir, 'kelta');
    writeFileSync(exe, 'OLD BINARY');
  });

  afterEach(() => {
    rmSync(dir, { recursive: true, force: true });
  });

  it('downloads, verifies sha256, and atomically replaces the binary', async () => {
    const newBinary = Buffer.from('NEW BINARY CONTENT');
    const manifest = manifestFor('1.0.9', newBinary);
    get
      .mockResolvedValueOnce({ data: manifest })
      .mockResolvedValueOnce({ data: toArrayBuffer(newBinary) });

    const result = await performUpdate('https://downloads.kelta.io', () => undefined, exe);

    expect(result.updateAvailable).toBe(true);
    expect(readFileSync(exe, 'utf-8')).toBe('NEW BINARY CONTENT');
    expect(existsSync(exe + '.new')).toBe(false);
    // download URL is base-joined from the manifest PATH (mirror-friendly)
    expect(get).toHaveBeenLastCalledWith(
      `https://downloads.kelta.io/cli/releases/1.0.9/kelta-${currentTargetKey()}`,
      expect.objectContaining({ responseType: 'arraybuffer' })
    );
  });

  it('aborts on checksum mismatch and leaves the current binary untouched', async () => {
    const newBinary = Buffer.from('NEW BINARY CONTENT');
    const manifest = manifestFor('1.0.9', newBinary);
    manifest.targets[currentTargetKey()].sha256 = 'f'.repeat(64);
    get
      .mockResolvedValueOnce({ data: manifest })
      .mockResolvedValueOnce({ data: toArrayBuffer(newBinary) });

    await expect(
      performUpdate('https://downloads.kelta.io', () => undefined, exe)
    ).rejects.toMatchObject({ code: 'CHECKSUM_MISMATCH' });
    expect(readFileSync(exe, 'utf-8')).toBe('OLD BINARY');
  });

  it('no-ops when already up to date', async () => {
    get.mockResolvedValueOnce({ data: manifestFor('1.0.5', Buffer.from('x')) });
    const result = await performUpdate('https://downloads.kelta.io', () => undefined, exe);
    expect(result.updateAvailable).toBe(false);
    expect(readFileSync(exe, 'utf-8')).toBe('OLD BINARY');
    expect(get).toHaveBeenCalledTimes(1);
  });

  it('fails clearly when the release lacks this target', async () => {
    const manifest = manifestFor('1.0.9', Buffer.from('x'));
    manifest.targets = { 'plan9-mips': manifest.targets[currentTargetKey()] };
    get.mockResolvedValueOnce({ data: manifest });
    await expect(
      performUpdate('https://downloads.kelta.io', () => undefined, exe)
    ).rejects.toMatchObject({ code: 'TARGET_NOT_PUBLISHED' });
  });

  it('cleanupPreviousBinary removes a leftover .old and tolerates absence', () => {
    writeFileSync(exe + '.old', 'stale');
    cleanupPreviousBinary(exe);
    expect(existsSync(exe + '.old')).toBe(false);
    cleanupPreviousBinary(exe); // second call: nothing to clean, no throw
  });

  it('checkForUpdate reports without touching the filesystem', async () => {
    get.mockResolvedValueOnce({ data: manifestFor('1.0.9', Buffer.from('x')) });
    const check = await checkForUpdate('https://downloads.kelta.io');
    expect(check).toMatchObject({
      currentVersion: '1.0.5',
      latestVersion: '1.0.9',
      updateAvailable: true,
    });
    expect(readFileSync(exe, 'utf-8')).toBe('OLD BINARY');
  });
});

describe('maybeNotifyUpdate', () => {
  let dir: string;
  let ttyDescriptor: PropertyDescriptor | undefined;

  beforeEach(() => {
    dir = mkdtempSync(join(tmpdir(), 'kelta-passive-'));
    setConfigDirForTesting(dir);
    ttyDescriptor = Object.getOwnPropertyDescriptor(process.stderr, 'isTTY');
    Object.defineProperty(process.stderr, 'isTTY', { value: true, configurable: true });
  });

  afterEach(() => {
    setConfigDirForTesting();
    if (ttyDescriptor) Object.defineProperty(process.stderr, 'isTTY', ttyDescriptor);
    else delete (process.stderr as { isTTY?: boolean }).isTTY;
    rmSync(dir, { recursive: true, force: true });
  });

  it('nudges once, stamps BEFORE fetching, then throttles for 24h', async () => {
    get.mockResolvedValue({ data: manifestFor('1.0.9', Buffer.from('x')) });
    const log = vi.fn();
    await maybeNotifyUpdate(log);
    expect(log).toHaveBeenCalledWith(expect.stringContaining('1.0.5 → 1.0.9'));
    expect(existsSync(join(dir, 'update-check.json'))).toBe(true);

    log.mockClear();
    get.mockClear();
    await maybeNotifyUpdate(log);
    expect(get).not.toHaveBeenCalled();
    expect(log).not.toHaveBeenCalled();
  });

  it('KELTA_UPDATE_CHECK=0 disables the check entirely', async () => {
    process.env.KELTA_UPDATE_CHECK = '0';
    const log = vi.fn();
    await maybeNotifyUpdate(log);
    expect(get).not.toHaveBeenCalled();
    expect(log).not.toHaveBeenCalled();
  });

  it('stays silent when the downloads host is unreachable', async () => {
    get.mockRejectedValue(new Error('ECONNREFUSED'));
    const log = vi.fn();
    await maybeNotifyUpdate(log);
    expect(log).not.toHaveBeenCalled();
  });
});
