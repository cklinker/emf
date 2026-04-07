# Contributing

Please refer to the root contributing guide at [../CONTRIBUTING.md](../CONTRIBUTING.md) for general contribution guidelines.

## E2E-Specific Notes

- All new user-facing features must have corresponding E2E tests.
- Tests must pass locally before opening a PR (`npm test`).
- Follow the page object pattern documented in `docs/api/README.md`.
- Keep tests isolated — each test should set up and tear down its own state.
- Use named fixtures from `fixtures/` rather than raw `page` where possible.
