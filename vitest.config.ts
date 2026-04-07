import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    projects: ['kelta-web/vitest.config.ts', 'kelta-ui/app/vitest.config.ts'],
  },
});
