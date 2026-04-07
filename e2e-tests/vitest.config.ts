import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html'],
      reportsDirectory: './coverage',
      include: ['helpers/**/*.ts', 'fixtures/**/*.ts'],
      exclude: ['node_modules/**', 'test-results/**', 'dist/**'],
    },
  },
});
