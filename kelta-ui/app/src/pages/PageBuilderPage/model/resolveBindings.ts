/**
 * The REAL binding resolver (slice 2d). Replaces the 2a identity no-op shim that `bindingScope.ts`
 * re-exports — see {@link ./bindingScope}. Turns every {@link Binding} in a props object into its
 * resolved value against the current {@link BindingScope}, recursively, immutably.
 *
 * Two resolution modes (parent §"The shared model"):
 *  - `mode:'path'` (default) — dotted/indexed access (`record.name`, `data.accounts[0].name`) via the
 *    hand-written {@link getPath} walker. `@kelta/formula`'s parser is flat-key only (it stops at `.`),
 *    so dotted access NEVER goes through the formula engine.
 *  - `mode:'expr'` — flatten the leaves the expression references into a flat `Record<string, unknown>`
 *    (the flat-scope bridge) then call `FormulaEvaluator.evaluate`. This is the only path through the
 *    shared expression engine.
 *
 * Resolution is 100% client-side. The server never parses `$bind` — every record read stays on the
 * authorized JSON:API path so Cerbos/FLS remain the only data gate.
 */
import { FormulaEvaluator } from '@kelta/formula'
import { isBinding, type Binding, type PropValue } from './pageModel'
import { getPath, type BindingScope } from './bindingScope'

/** One shared evaluator (caches parsed ASTs; cheap to reuse across resolves). */
const evaluator = new FormulaEvaluator()

/** True in a Vite dev build — gate the single warn so production stays silent. */
function isDev(): boolean {
  return import.meta.env?.DEV === true
}

/**
 * Resolve a single binding against the scope. Never throws — a malformed expression or a missing path
 * falls back to `null` (and a single dev-only warn). Missing leaves match the formula engine's
 * `undefined → null` semantics so `path` and `expr` agree on absent values.
 */
export function resolveBinding(binding: Binding, scope: BindingScope): unknown {
  const expr = binding.$bind
  const mode = binding.mode ?? 'path'
  if (!expr) return null
  try {
    if (mode === 'path') {
      return getPath(scope, expr)
    }
    // mode === 'expr': flatten referenced leaves → call FormulaEvaluator.evaluate.
    return evaluator.evaluate(expr, flattenScopeForExpr(expr, scope))
  } catch (err) {
    if (isDev()) {
      console.warn(`[resolveBinding] "${expr}" (${mode}) failed:`, err)
    }
    return null
  }
}

/**
 * The flat-scope bridge to `@kelta/formula`. The parser is flat-key only (it cannot read `record.name`
 * as one identifier — `parser.ts` `parseIdentifierOrFunction` stops at `.`), so for `mode:'expr'` we:
 *   1. spread the `record` / `item` / `vars` LEAVES into a flat map, so a bare `count` reads
 *      `vars.count` the way a formula-field expression references a plain field name;
 *   2. overlay each identifier `extractFieldRefs(expr)` reports, resolved via {@link getPath} against the
 *      scope root, so flat names and same-root dotted refs both resolve;
 *   3. hand back a flat `Record<string, unknown>` keyed by those identifiers.
 * Identifiers not present resolve to `null` (the engine treats `undefined`/`null` alike).
 */
export function flattenScopeForExpr(expr: string, scope: BindingScope): Record<string, unknown> {
  const flat: Record<string, unknown> = {}
  // 1. Spread record/item/vars leaves so bare leaf names read like field formulas.
  for (const root of [scope.record, scope.item, scope.vars]) {
    if (root && typeof root === 'object' && !Array.isArray(root)) {
      Object.assign(flat, root as Record<string, unknown>)
    }
  }
  // 2. Overlay the exact identifiers the parser saw, resolved against the scope root.
  let refs: string[]
  try {
    refs = evaluator.extractFieldRefs(expr)
  } catch {
    refs = []
  }
  for (const ref of refs) {
    const resolved = getPath(scope, ref)
    // Only overlay when getPath finds something at the scope root; otherwise keep the spread leaf
    // (a bare `count` resolves from the spread vars/record/item, not from `scope.count`).
    if (resolved !== null) {
      flat[ref] = resolved
    } else if (!(ref in flat)) {
      flat[ref] = null
    }
  }
  return flat
}

/**
 * Deep-resolve every {@link Binding} in a props object. Leaves literals untouched; recurses into arrays
 * and plain objects. Returns a NEW object (does not mutate). Called by `renderNode` before invoking a
 * descriptor's `Render`, satisfying the resolved-node invariant.
 *
 * Identity on literal-only props: a props object with no `$bind` resolves to structurally-equal values
 * (the 2a golden-snapshot parity guard depends on this).
 */
export function resolveBindings(
  props: Record<string, unknown>,
  scope: BindingScope
): Record<string, unknown> {
  return resolveValue(props as PropValue, scope) as Record<string, unknown>
}

function resolveValue(value: PropValue, scope: BindingScope): unknown {
  if (isBinding(value)) return resolveBinding(value, scope)
  if (Array.isArray(value)) return value.map((v) => resolveValue(v, scope))
  if (value !== null && typeof value === 'object') {
    const out: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(value)) out[k] = resolveValue(v as PropValue, scope)
    return out
  }
  // string | number | boolean | null — returned as-is.
  return value
}
