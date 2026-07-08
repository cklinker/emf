/**
 * Computed page variables (app-platform slice 2). A `PageVariable` with
 * `kind:'computed'` derives its value from `expression` over the live binding scope;
 * the result feeds `scope.vars` transparently so bindings/visibility/actions read it
 * like any variable.
 *
 * Expressions use BARE identifiers (`a + 1`), the 2d expr contract: `@kelta/formula`'s
 * parser is flat-key only — a dotted `vars.a` throws at the `.` and evaluates to null.
 * The flat-scope bridge spreads `vars` leaves, so a bare name reads a static var or an
 * already-computed sibling. Data-source results are NOT readable here in v1 (the
 * engine can't express nested access; path-mode prop bindings remain the way to read
 * `data.*`).
 *
 * Evaluation order is a Kahn topological sort over bare-name references to computed
 * siblings. Cycle members evaluate to `null` with one dev warn. Each expression runs
 * through the 2d `resolveBinding` expr path (same never-throws/null-on-error
 * semantics) against a progressive scope whose `vars` include the statics plus
 * already-computed values.
 */
import type { PageVariable } from '../pageConfig'
import type { BindingScope } from './bindingScope'
import { extractExpressionRefs, resolveBinding } from './resolveBindings'

/** True in a Vite dev build — gate the single warn so production stays silent. */
function isDev(): boolean {
  return import.meta.env?.DEV === true
}

/** The computed subset of a page's variables (named, with an expression). */
export function computedVariablesOf(variables: PageVariable[]): PageVariable[] {
  return variables.filter(
    (v) => v.kind === 'computed' && v.name.trim() !== '' && (v.expression ?? '').trim() !== ''
  )
}

/**
 * Names a computed expression depends on, restricted to the given sibling names.
 * References are bare identifiers (the parser rejects dots — see module doc).
 */
function dependenciesOf(expression: string, siblingNames: Set<string>): string[] {
  const deps = new Set<string>()
  for (const ref of extractExpressionRefs(expression)) {
    if (siblingNames.has(ref)) deps.add(ref)
  }
  return [...deps]
}

/**
 * Evaluate every computed variable against the scope. Returns a map keyed by variable
 * name; cycle members and failed evaluations are `null`. Pure — does not mutate scope.
 */
export function evaluateComputedVariables(
  variables: PageVariable[],
  scope: BindingScope
): Record<string, unknown> {
  const computed = computedVariablesOf(variables)
  if (computed.length === 0) return {}

  const names = new Set(computed.map((v) => v.name))
  const deps = new Map<string, string[]>()
  for (const v of computed) {
    deps.set(v.name, dependenciesOf(v.expression ?? '', names))
  }

  // Kahn: repeatedly evaluate variables whose remaining deps are all settled.
  const order: string[] = []
  const settled = new Set<string>()
  let progressed = true
  while (progressed) {
    progressed = false
    for (const v of computed) {
      if (settled.has(v.name)) continue
      if (deps.get(v.name)!.every((d) => settled.has(d))) {
        order.push(v.name)
        settled.add(v.name)
        progressed = true
      }
    }
  }
  const cyclic = computed.filter((v) => !settled.has(v.name))
  if (cyclic.length > 0 && isDev()) {
    console.warn(
      `[computedVars] dependency cycle — evaluating to null: ${cyclic.map((v) => v.name).join(', ')}`
    )
  }

  const byName = new Map(computed.map((v) => [v.name, v]))
  const values: Record<string, unknown> = {}
  for (const v of cyclic) values[v.name] = null
  for (const name of order) {
    const variable = byName.get(name)!
    const progressiveScope: BindingScope = {
      ...scope,
      vars: { ...(scope.vars ?? {}), ...values },
    }
    values[name] = resolveBinding({ $bind: variable.expression!, mode: 'expr' }, progressiveScope)
  }
  return values
}
