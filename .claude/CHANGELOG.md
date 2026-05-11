# Kelta Platform changelog

This file tracks merged autopilot work. Entries are appended by autopilot workers as the final step of each task, one line per merged change, in the format `- YYYY-MM-DD <type>(<scope>): <one-line summary> (<task-id>)`. Newest dates appear at the bottom; group entries under an H2 date heading.

## 2026-05-10

- 2026-05-10 chore(autopilot): seed CHANGELOG.md (CHORE-2026-05-10-0001)

## 2026-05-11

- 2026-05-11 chore(repo): add .editorconfig with project-wide indentation rules (CHORE-2026-05-10-0004)
- 2026-05-11 doc(architecture): document the autopilot loop — topology, queue lifecycle, label gate, migration lock, bug ingress (DOC-2026-05-10-0001)
- 2026-05-11 chore(security): add SECURITY.md with vuln reporting policy and autopilot gating note (CHORE-2026-05-10-0002)
- 2026-05-11 chore(docs): add CONTRIBUTING.md outlining the autopilot workflow (CHORE-2026-05-10-0003)
- 2026-05-11 chore(autopilot): use task title+type for fallback commit message in worker.sh so `gh pr create --fill` produces a useful PR title (CHORE-2026-05-10-0005)
- 2026-05-11 doc(ui): add UI component consolidation plan covering DataTable/FilterBuilder/FieldRenderer/ResourceForm/RelatedList unification, feature superset, and migration order (DOC-2026-05-10-0002)
- 2026-05-11 chore(cache): wire NATS broadcast listeners on gateway and worker for `kelta.config.domain.changed.*` and `kelta.config.feature.changed.*` cache invalidation (CHORE-2026-05-10-0007)
- 2026-05-11 chore(ci): wire checkout-db/release-db into integration-tests job and drop postgres:15-alpine pre-pull so KeltaStack uses the kelta-ci-db pool (CHORE-2026-05-10-0009)
