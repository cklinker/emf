/**
 * Binding scope + resolver seam.
 *
 * In slice 2a this is an **identity no-op**: `resolveBindings` returns the props unchanged (the
 * builder only has literal props yet). Slice 2d replaces the body with real path/expression
 * resolution against the scope while keeping this signature, so descriptors written against it do not
 * change. The resolved-node invariant (parent spec): the `props` handed to a widget's `Render` are
 * always already resolved — descriptors must not resolve again (the sole exception is
 * `list`/`repeater`, which re-resolves its children under each per-row `item` scope).
 */
/** The reactive scope a binding resolves against. Populated for real in slice 2d. */
export interface BindingScope {
  /** The current record (or repeat row via `item`). */
  record?: Record<string, unknown>
  /** Page-level variables. */
  vars?: Record<string, unknown>
  /** Route params / page meta. */
  page?: Record<string, unknown>
  /** On-load data source results, keyed by source name. */
  data?: Record<string, unknown>
  /** Per-row scope inside a `list`/`repeater`. */
  item?: unknown
}

export const EMPTY_SCOPE: BindingScope = {}

/**
 * Resolve a node's props against a scope. Identity in 2a (no bindings exist yet); real in 2d.
 * Returns the same object reference when nothing needs resolving so editor parity is exact.
 */
export function resolveBindings(
  props: Record<string, unknown>,
  scope: BindingScope
): Record<string, unknown> {
  // 2a: identity — bindings are resolved for real in slice 2d against `scope`.
  void scope
  return props
}
