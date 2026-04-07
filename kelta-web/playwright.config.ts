// E2E tests are managed at the monorepo level. See ../e2e-tests/
// This file exists as a reference stub so tooling can detect Playwright support.

import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '../e2e-tests',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173',
  },
});
