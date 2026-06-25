/**
 * Immutable dotted-key walkers over a node's `props` object. These are a thin static walker used by
 * the schema-driven inspector to read/write values at (possibly nested) keys like `dataView.collection`
 * — NOT the binding resolver (that operates on a live scope and lands in 2d). `setByPath`/`deleteByPath`
 * always return a NEW props object; the input is never mutated.
 */
import type { PropValue } from '../model/pageModel'

/** Read the value at a dotted path. Returns `undefined` when any segment is missing. */
export function getByPath(obj: Record<string, unknown> | undefined, path: string): unknown {
  if (!obj) return undefined
  const segments = path.split('.')
  let current: unknown = obj
  for (const segment of segments) {
    if (current == null || typeof current !== 'object' || Array.isArray(current)) {
      return undefined
    }
    current = (current as Record<string, unknown>)[segment]
  }
  return current
}

/**
 * Write `value` at a dotted path, returning a new props object. Intermediate objects are created
 * (immutably) when absent. The original object and any untouched branches are left referentially intact.
 */
export function setByPath(
  obj: Record<string, PropValue>,
  path: string,
  value: PropValue
): Record<string, PropValue> {
  const segments = path.split('.')
  const [head, ...rest] = segments
  if (rest.length === 0) {
    return { ...obj, [head]: value }
  }
  const existing = obj[head]
  const child =
    existing && typeof existing === 'object' && !Array.isArray(existing)
      ? (existing as Record<string, PropValue>)
      : {}
  return { ...obj, [head]: setByPath(child, rest.join('.'), value) }
}

/** Delete the value at a dotted path, returning a new props object. */
export function deleteByPath(
  obj: Record<string, PropValue>,
  path: string
): Record<string, PropValue> {
  const segments = path.split('.')
  const [head, ...rest] = segments
  if (!(head in obj)) return { ...obj }
  if (rest.length === 0) {
    const next = { ...obj }
    delete next[head]
    return next
  }
  const existing = obj[head]
  if (!existing || typeof existing !== 'object' || Array.isArray(existing)) {
    return { ...obj }
  }
  return {
    ...obj,
    [head]: deleteByPath(existing as Record<string, PropValue>, rest.join('.')),
  }
}
