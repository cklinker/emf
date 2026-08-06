// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { allCommands } from '../registry/registry.js';
import { buildManifest } from '../registry/manifest.js';
import { renderAgentsMd, renderCommandsMd } from './render.js';

function committed(name: string): string {
  return readFileSync(fileURLToPath(new URL(`../../${name}`, import.meta.url)), 'utf-8');
}

describe('generated docs freshness', () => {
  it('AGENTS.md matches the source guide — run `npm run gen:docs` when this fails', () => {
    expect(committed('AGENTS.md')).toBe(renderAgentsMd());
  });

  it('COMMANDS.md matches the registry — run `npm run gen:docs` when this fails', () => {
    expect(committed('COMMANDS.md')).toBe(renderCommandsMd(buildManifest(allCommands)));
  });
});
