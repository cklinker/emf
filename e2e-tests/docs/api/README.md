# E2E Test Patterns & Page Objects

## Overview

Tests are organized under `tests/` by feature area. Each feature area maps to one or more page objects in `pages/`.

## Page Object Pattern

Page objects encapsulate selectors and actions for a given UI surface. They extend no base class — they receive the Playwright `Page` instance via constructor.

```typescript
// pages/login.page.ts
import { Page } from '@playwright/test';

export class LoginPage {
  constructor(private readonly page: Page) {}

  async goto() {
    await this.page.goto('/login');
  }

  async login(email: string, password: string) {
    await this.page.getByLabel('Email').fill(email);
    await this.page.getByLabel('Password').fill(password);
    await this.page.getByRole('button', { name: 'Sign in' }).click();
  }
}
```

## Fixtures

Shared test fixtures live in `fixtures/`. Import via the `@fixtures` path alias:

```typescript
import { test, expect } from '@fixtures';
```

The custom `test` object extends Playwright's base `test` with pre-authenticated pages and common setup.

## Helpers

Utility functions (API clients, data factories, wait helpers) live in `helpers/`. Import via `@helpers/<name>`.

## Test Structure

```
tests/
  auth/           # Login, logout, MFA, password reset
  admin/          # Admin UI: collections, users, settings
  end-user/       # End-user portal flows
  journeys/       # Cross-feature user journeys
pages/            # Page object classes
fixtures/         # Playwright fixtures (auth state, etc.)
helpers/          # API helpers, data factories, utilities
```

## Selectors

Prefer user-facing selectors in this order:

1. `getByRole()` — ARIA roles
2. `getByLabel()` — form labels
3. `getByText()` — visible text
4. `getByTestId()` — `data-testid` attributes as last resort

Avoid CSS class selectors and XPath.
