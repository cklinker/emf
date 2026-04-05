# Task Workflow

## 1. Create a Feature Branch

```bash
git checkout main && git pull origin main
git checkout -b feature/<task-id>-<short-description>
```

Branch naming: `feature/<task-id-lowercase>-<kebab-case-description>`
Examples: `feature/a1-field-type-migration`, `feature/b5-picklist-service`

## 2. Implement the Feature

- Follow existing patterns in the codebase
- Write unit tests for all new classes and methods
- Write integration tests that verify the feature end-to-end

### Coding Standards Checklist
- All new entities extend `BaseEntity` (UUID id, createdAt, updatedAt)
- All new JPA repositories extend `JpaRepository`
- All new REST controllers follow existing `@RestController` patterns with `@RequestMapping`
- Flyway migrations numbered sequentially (check `kelta-worker/src/main/resources/db/migration/` for next number)
- Kafka events use `ConfigEventPublisher` pattern
- Java: No unused imports, no raw types, no unchecked casts
- TypeScript: Must pass ESLint and Prettier checks

## 3. Build and Verify

Run `/verify` to execute the full build pipeline, or use `/test-java` and `/test-frontend` for targeted testing.

### Pre-PR Checklist
- [ ] `mvn verify` passes for gateway (zero test failures)
- [ ] `mvn verify` passes for worker (zero test failures)
- [ ] `npm run lint` passes in kelta-web
- [ ] `npm run typecheck` passes in kelta-web
- [ ] `npm run format:check` passes in kelta-web
- [ ] `npm run test:coverage` passes in kelta-web
- [ ] No compiler warnings introduced
- [ ] Flyway migration numbering is correct and sequential
- [ ] New tests cover the feature adequately

## 4. Commit and Push

```bash
git add <specific-files>
git commit -m "<type>(<scope>): <description>"
git push -u origin feature/<task-id>-<short-description>
```

Commit message examples:
- `feat(runtime-core): extend FieldType enum with 16 new types`
- `feat(worker): add internal permissions endpoint`
- `fix(gateway): handle lookup field resolution in IncludeResolver`

## 5. Open PR and Auto-Merge

Use `/pr` to create the PR with standard format.

```bash
gh pr create \
  --title "[<TASK-ID>] <Short description>" \
  --body "$(cat <<'EOF'
## Summary
- <bullet points describing what was implemented>

## Changes
- <list of key files added/modified>

## Testing
- <describe tests added>
- <describe how to verify manually if applicable>

## Checklist
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] No lint errors
- [ ] Flyway migration is correct
- [ ] Follows existing code patterns

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"

gh pr merge --auto --squash
```

## 6. After PR Merges

```bash
git checkout main && git pull origin main
```

Always return to main after merge to ensure next branch is based on latest code.
