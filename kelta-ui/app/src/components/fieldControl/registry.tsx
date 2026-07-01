/**
 * FieldControl registry singleton. `getFieldControl(type)` returns the merged control for a field
 * type; unknown types fall back to the string control (so plugin/custom types still render). A
 * plugin can override any member via `registerFieldControl`. Read-view plugin overrides are already
 * honored by `FieldRenderer` (which the default `View` delegates to), so registering here is only
 * needed to override edit/inline behavior.
 */
import type { FieldType } from '@/hooks/useCollectionSchema'
import type { FieldControl, PartialFieldControl } from './types'
import { CONTROLS } from './controls'

const registry = new Map<string, FieldControl>(Object.entries(CONTROLS) as [string, FieldControl][])

/** The control used for unknown/custom field types — plain text edit + string view. */
const FALLBACK: FieldControl = CONTROLS.string

export function getFieldControl(type: FieldType | string): FieldControl {
  return registry.get(type) ?? FALLBACK
}

/** Override or add a control for a field type. Unspecified members keep the current/fallback impl. */
export function registerFieldControl(type: string, control: PartialFieldControl): void {
  const base = registry.get(type) ?? FALLBACK
  registry.set(type, { ...base, ...control })
}

/** Test/reset helper — restore the built-in controls. */
export function resetFieldControls(): void {
  registry.clear()
  for (const [type, control] of Object.entries(CONTROLS)) {
    registry.set(type, control as FieldControl)
  }
}
