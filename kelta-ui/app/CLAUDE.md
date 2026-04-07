# kelta-ui/app — Admin UI

React 19 admin/builder UI for the Kelta Enterprise Platform.

## Stack

| Tool | Version |
|------|---------|
| React | 19 |
| Vite | 7.x |
| TypeScript | 5.x (strict mode) |
| Vitest | 4.x |
| ESLint | 9.x (flat config) |
| Prettier | 3.x |
| Tailwind CSS | 4.x |
| React Router | 7.x |
| TanStack Query | 5.x |

## Key Patterns

- Path alias `@/` maps to `src/`
- All components use named exports
- Forms use `react-hook-form` + `zod` for validation
- API calls go through `src/services/` using `axios`
- State: TanStack Query for server state, React context for UI state
- Theming: `next-themes` + Tailwind CSS variables
- Components: shadcn/ui primitives via `@kelta/components`

## Scripts

```
npm run dev           # start dev server
npm run build         # typecheck + vite build
npm run lint          # eslint
npm run format        # prettier write
npm run format:check  # prettier check
npm run test          # vitest watch
npm run test:run      # vitest run (CI)
npm run test:coverage # coverage report
```

## Reference

See `.claude/docs/` at the repo root for architecture, conventions, and testing docs.
