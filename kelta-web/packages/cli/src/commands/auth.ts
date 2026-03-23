import { Command } from 'commander';
import { saveConfig, loadConfig, removeConfig } from '../client.js';

export function registerAuthCommands(program: Command): void {
  const auth = program.command('auth').description('Authentication management');

  auth
    .command('login')
    .description('Save API credentials')
    .requiredOption('--url <url>', 'Kelta API URL (e.g., https://api.kelta.io)')
    .requiredOption('--tenant <slug>', 'Tenant slug')
    .requiredOption('--token <token>', 'API token or JWT')
    .action((opts: { url: string; tenant: string; token: string }) => {
      saveConfig({ url: opts.url.replace(/\/$/, ''), token: opts.token, tenant: opts.tenant });
      process.stdout.write(`Authenticated as tenant "${opts.tenant}" at ${opts.url}\n`);
    });

  auth
    .command('logout')
    .description('Remove saved credentials')
    .action(() => {
      removeConfig();
      process.stdout.write('Credentials removed.\n');
    });

  auth
    .command('status')
    .description('Show current authentication status')
    .action(() => {
      const config = loadConfig();
      if (!config) {
        process.stdout.write('Not authenticated. Run: kelta auth login\n');
        process.exit(1);
      }
      process.stdout.write(`URL:    ${config.url}\n`);
      process.stdout.write(`Tenant: ${config.tenant}\n`);
      process.stdout.write(`Token:  ${config.token.substring(0, 20)}...\n`);
    });
}
