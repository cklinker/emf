import { test as base, expect } from '@playwright/test';
import { DataFactory } from '../helpers/data-factory';
import { getAuthentikTokens } from './auth-tokens';

interface EMFFixtures {
  tenantSlug: string;
  apiBaseUrl: string;
  dataFactory: DataFactory;
}

export const test = base.extend<EMFFixtures>({
  tenantSlug: [process.env.E2E_TENANT_SLUG || 'default', { option: true }],
  apiBaseUrl: [
    process.env.E2E_API_BASE_URL || 'https://emf.rzware.com',
    { option: true },
  ],

  dataFactory: async ({ tenantSlug, apiBaseUrl }, use) => {
    const token = await getAuthentikTokens();
    const factory = new DataFactory({
      baseUrl: apiBaseUrl,
      token,
      tenantSlug,
    });
    await use(factory);
    await factory.cleanup();
  },
});

export { expect };
