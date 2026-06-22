import '@testing-library/jest-dom/vitest';
import { configure } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

// Raise the async-utility timeout (default 1000ms). The Build-and-Deploy
// workflow runs the frontend suite concurrently with heavy container image
// builds, so data-fetch `waitFor`/`findBy` assertions can exceed 1s under load
// and flake (e.g. DataTable's "John Doe" row), even though they pass in the
// standalone PR CI. 5s absorbs the load without masking real failures.
configure({ asyncUtilTimeout: 5000 });

// Clean up after each test
afterEach(() => {
  // Reset any mocks or state between tests
});

// Global setup
beforeAll(() => {
  // Any global setup needed
});

// Global teardown
afterAll(() => {
  // Any global cleanup needed
});
