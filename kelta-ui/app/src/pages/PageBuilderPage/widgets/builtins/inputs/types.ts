/**
 * Shared props + helpers for the standalone typed-input widgets (slice 2f).
 *
 * Each input binds to a `{ collection, field }` pair, reads the field's `FieldType` from
 * `useCollectionSchema`, and renders the matching control. A `defaultValue` arrives ALREADY resolved by
 * `renderNode` (the resolved-node invariant from 2a/2d) — the controls never call `resolveBindings`.
 */
import type { PropValue } from '../../../model/pageModel'

/** Shared props for every standalone typed input widget. */
export interface InputWidgetProps {
  /** Collection whose schema supplies the field's `FieldType` + options. */
  collection?: string
  /** Field name on that collection. */
  field?: string
  /** `{{$bind}}` default value (resolved before Render); a literal otherwise. */
  defaultValue?: PropValue
  /** Advisory client-side required flag; the worker is the source of truth on write. */
  required?: boolean
  /** Render disabled. */
  readOnly?: boolean
  /** Placeholder for text-like controls. */
  placeholder?: string
}

/** The editor/runtime mode flows in from the descriptor's `Render`. */
export type InputControlProps = InputWidgetProps & {
  mode: 'editor' | 'runtime'
}

/** Coerce a resolved `PropValue` default into a string seed for text-like controls. */
export function defaultAsString(value: PropValue | undefined): string {
  if (value == null) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return ''
}

/** Coerce a resolved `PropValue` default into a boolean seed (checkbox). */
export function defaultAsBoolean(value: PropValue | undefined): boolean {
  if (typeof value === 'boolean') return value
  if (typeof value === 'string') return value === 'true'
  return false
}
