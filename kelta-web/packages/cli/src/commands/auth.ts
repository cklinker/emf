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
      console.log(`Authenticated as tenant "${opts.tenant}" at ${opts.url}`);
    });

  auth
    .command('logout')
    .description('Remove saved credentials')
    .action(() => {
      removeConfig();
      console.log('Credentials removed.');
    });

  auth
    .command('status')
    .description('Show current authentication status')
    .action(() => {
      const config = loadConfig();
      if (!config) {
        console.log('Not authenticated. Run: kelta auth login');
        process.exit(1);
      }
      console.log(`URL:    ${config.url}`);
      console.log(`Tenant: ${config.tenant}`);
      console.log(`Token:  ${config.token.substring(0, 20)}...`);
    });
}
