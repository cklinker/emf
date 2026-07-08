/**
 * Conditional-visibility semantics (app-platform slice 1). The universal `visible`
 * prop hides a node at runtime when its RESOLVED value is hidden per `isHiddenValue`;
 * an absent prop always renders. The editor never hides — `SelectableNode` ghosts a
 * literal `false` and badges a binding instead (see `visibilityKind`).
 */
import { isBinding } from './pageModel'

/**
 * Hidden iff the resolved value is `false | 'false' | 0 | '' | null`. `undefined`
 * (prop absent) is visible. A bound-but-unresolvable expression resolves `null` (the
 * 2d contract) and therefore hides — fail-closed for "show when X" conditions.
 */
export function isHiddenValue(value: unknown): boolean {
  if (value === undefined) return false
  return value === false || value === 'false' || value === 0 || value === '' || value === null
}

/** Editor-chrome classification of the RAW (unresolved) `visible` prop. */
export type VisibilityKind = 'default' | 'literal-hidden' | 'bound'

export function visibilityKind(rawVisible: unknown): VisibilityKind {
  if (isBinding(rawVisible)) return 'bound'
  if (rawVisible !== undefined && isHiddenValue(rawVisible)) return 'literal-hidden'
  return 'default'
}
