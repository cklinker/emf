# runtime-module-core

A compile-time `KeltaModule` (`CoreActionsModule`) that contributes the **data / CRUD /
control** flow action handlers: create / update / delete record, query records, decision,
log message, field update, create task, trigger flow.

Handlers here follow the **same registration model** as `runtime-module-integration` — they
implement `io.kelta.runtime.workflow.ActionHandler`, are **constructed** in
`CoreActionsModule.onStartup(ModuleContext)` and returned from `getActionHandlers()`, and are
**not** `@Component`-scanned. The module is a `@Bean` in `kelta-worker/.../config/FlowConfig.java`
(`coreActionsModule()`).

- Handlers live in `handlers/` (e.g. `CreateRecordActionHandler`, `QueryRecordsActionHandler`,
  `DecisionActionHandler`).
- Full SPI explanation + the "add a handler" recipe: see
  `kelta-platform/runtime/runtime-module-integration/CLAUDE.md` and
  `.claude/docs/playbooks.md` → "Add a flow action handler".

```bash
mvn test -f kelta-platform/pom.xml -pl runtime/runtime-module-core
```
