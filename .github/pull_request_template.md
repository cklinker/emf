## Summary

<!-- 1–3 sentences: what changed and why. The "why" matters more than the "what". -->

## Test plan

<!-- How was this verified? Check all that apply and add specifics. -->

- [ ] `/verify` passed locally (or CI green)
- [ ] Unit tests added/updated for new logic
- [ ] E2E (Playwright) test added/updated for new UI behavior
- [ ] Manual smoke in the browser (describe steps)
- [ ] Migration tested against a non-empty DB (if applicable)

## Docs touched

<!-- Mark which .claude/docs files this PR updates, and why. Empty list is OK only for pure refactors / bug fixes that change no public surface. -->

- [ ] `.claude/docs/architecture.md` — new endpoint / entity / data flow
- [ ] `.claude/docs/conventions.md` — new pattern worth codifying
- [ ] `.claude/docs/integrations.md` — new external dependency / SDK
- [ ] `.claude/docs/concerns.md` — new risk / known issue
- [ ] `.claude/docs/testing.md` — new test pattern
- [ ] None — pure bug fix / internal refactor (explain why no doc update)

## Migration

<!-- Required if this PR adds or modifies a Flyway migration. -->

- Migration #: V___ — `<filename>`
- Backwards-compatible: yes / no
- Touches shared tables: yes / no
- If `no` to either: link to rollout plan

## Autopilot

<!-- Set by the dispatcher; humans usually leave blank. -->

- [ ] `autopilot` label applied → enables auto-merge on green CI
- Source task: `<emf-queue/in-progress/TASK-...md>` (autopilot only)
