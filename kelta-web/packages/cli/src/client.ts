import axios, { AxiosInstance } from 'axios';
import { readFileSync, writeFileSync, existsSync, unlinkSync } from 'fs';
import { homedir } from 'os';
import { join } from 'path';

export interface KeltaConfig {
  url: string;
  token: string;
  tenant: string;
}

const CONFIG_PATH = join(homedir(), '.keltarc');

export function loadConfig(): KeltaConfig | null {
  if (!existsSync(CONFIG_PATH)) return null;
  try {
    return JSON.parse(readFileSync(CONFIG_PATH, 'utf-8')) as KeltaConfig;
  } catch {
    return null;
  }
}

export function saveConfig(config: KeltaConfig): void {
  writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2), { mode: 0o600 });
}

export function removeConfig(): void {
  if (existsSync(CONFIG_PATH)) unlinkSync(CONFIG_PATH);
}

export function createClient(configOverride?: Partial<KeltaConfig>): AxiosInstance {
  const config = loadConfig();
  const url = configOverride?.url || config?.url;
  const token = configOverride?.token || config?.token;
  const tenant = configOverride?.tenant || config?.tenant;

  if (!url || !token || !tenant) {
    process.stderr.write(
      'Not authenticated. Run: kelta auth login --url <url> --tenant <slug> --token <token>\n'
    );
    process.exit(1);
  }

  return axios.create({
    baseURL: `${url}/${tenant}`,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/vnd.api+json',
    },
    validateStatus: () => true,
  });
}
