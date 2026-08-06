// Compiles the kelta CLI for every release target and writes the downloads
// tree: releases/<version>/kelta-<os>-<arch>[.exe], manifest.json, latest.txt,
// and releases/<version>/SHA256SUMS. Requires `bun` on PATH.
//
//   node|bun scripts/build-binaries.mjs --version 1.0.42 --sha abc1234 \
//     [--out ../dist-downloads] [--base-url https://downloads.kelta.io] [--targets linux-x64,...]
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ALL_TARGETS = {
  'linux-x64': 'bun-linux-x64',
  'linux-arm64': 'bun-linux-arm64',
  'darwin-x64': 'bun-darwin-x64',
  'darwin-arm64': 'bun-darwin-arm64',
  'windows-x64': 'bun-windows-x64',
};

function arg(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index === -1 ? fallback : process.argv[index + 1];
}

const version = arg('version');
const sha = arg('sha', 'unknown');
if (!version || !/^\d+\.\d+\.\d+$/.test(version)) {
  console.error('--version <MAJOR.MINOR.PATCH> is required');
  process.exit(2);
}
const here = dirname(fileURLToPath(import.meta.url));
const cliEntry = join(here, '..', 'packages', 'cli', 'src', 'index.ts');
const outRoot = arg('out', join(here, '..', 'dist-downloads'));
const baseUrl = arg('base-url', 'https://downloads.kelta.io').replace(/\/$/, '');
const targetKeys = (arg('targets', Object.keys(ALL_TARGETS).join(',')) ?? '').split(',');

const releaseDir = join(outRoot, 'releases', version);
mkdirSync(releaseDir, { recursive: true });

const targets = {};
const sums = [];
for (const key of targetKeys) {
  const bunTarget = ALL_TARGETS[key];
  if (!bunTarget) {
    console.error(`Unknown target ${key}`);
    process.exit(2);
  }
  const fileName = `kelta-${key}${key.startsWith('windows') ? '.exe' : ''}`;
  const outFile = join(releaseDir, fileName);
  console.log(`Compiling ${key} …`);
  const result = spawnSync(
    'bun',
    [
      'build',
      '--compile',
      `--target=${bunTarget}`,
      `--define`, `KELTA_CLI_VERSION="${version}"`,
      `--define`, `KELTA_CLI_SHA="${sha}"`,
      `--define`, `KELTA_CLI_TARGET="${key}"`,
      cliEntry,
      '--outfile', outFile,
    ],
    { stdio: 'inherit' }
  );
  if (result.status !== 0) process.exit(result.status ?? 1);
  // bun appends .exe itself for windows targets when missing — normalize
  const finalFile = outFile;
  const binary = readFileSync(finalFile);
  const digest = createHash('sha256').update(binary).digest('hex');
  const path = `cli/releases/${version}/${fileName}`;
  targets[key] = {
    path,
    url: `${baseUrl}/${path}`,
    sha256: digest,
    size: statSync(finalFile).size,
  };
  sums.push(`${digest}  ${fileName}`);
}

const manifest = {
  manifestVersion: 1,
  version,
  gitSha: sha,
  builtAt: new Date().toISOString(),
  targets,
};
writeFileSync(join(outRoot, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
writeFileSync(join(outRoot, 'latest.txt'), version + '\n');
writeFileSync(join(releaseDir, 'SHA256SUMS'), sums.join('\n') + '\n');
console.log(`Wrote ${Object.keys(targets).length} target(s) to ${outRoot} (version ${version})`);
