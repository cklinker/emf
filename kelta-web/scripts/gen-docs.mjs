// Regenerates packages/cli/{AGENTS.md,COMMANDS.md} from the built CLI
// registry. Run: npm run gen:docs (builds the cli first). Freshness is
// enforced by packages/cli/src/docsgen/docsgen.test.ts.
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const cliDist = join(here, '..', 'packages', 'cli', 'dist');
const cliRoot = join(here, '..', 'packages', 'cli');

const { allCommands } = await import(join(cliDist, 'registry', 'registry.js'));
const { buildManifest } = await import(join(cliDist, 'registry', 'manifest.js'));
const { renderAgentsMd, renderCommandsMd } = await import(join(cliDist, 'docsgen', 'render.js'));

writeFileSync(join(cliRoot, 'AGENTS.md'), renderAgentsMd());
writeFileSync(join(cliRoot, 'COMMANDS.md'), renderCommandsMd(buildManifest(allCommands)));
console.log('Wrote packages/cli/AGENTS.md and packages/cli/COMMANDS.md');
