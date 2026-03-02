import { defineConfig, devices } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

dotenv.config({ path: path.resolve(__dirname, '.env.local') });
dotenv.config({ path: path.resolve(__dirname, '.env') });

const BASE_URL = process.env.E2E_BASE_URL || 'https://emf-ui.rzware.com';
const CI = !!process.env.CI;

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: CI,
  retries: CI ? 2 : 0,
  workers: CI ? 1 : 2,
  reporter: CI
    ? [
        ['html', { open: 'never', outputFolder: 'playwright-report' }],
        ['junit', { outputFile: 'test-results/junit.xml' }],
        ['list'],
      ]
    : [['html', { open: 'on-failure' }], ['list']],

  use: {
    baseURL: BASE_URL,
    trace: CI ? 'on-first-retry' : 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: CI ? 'on-first-retry' : 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    viewport: { width: 1280, height: 800 },
  },

  projects: [
    {
      name: 'auth-setup',
      testDir: './auth',
      testMatch: 'auth.setup.ts',
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState: './auth/storage-state.json',
      },
      dependencies: ['auth-setup'],
    },
    ...(CI
      ? [
          {
            name: 'firefox',
            use: {
              ...devices['Desktop Firefox'],
              storageState: './auth/storage-state.json',
            },
            dependencies: ['auth-setup'],
          },
        ]
      : []),
  ],

  expect: {
    timeout: 10_000,
  },
});
