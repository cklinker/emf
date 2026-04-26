/** A field on the target payload schema (an output the consumer wants filled). */
export interface TargetField {
  /** Dot-path within the payload, e.g. "subject" or "user.email" */
  path: string
  /** Human-readable label shown next to the row */
  label: string
  /** Optional one-line description rendered below the label */
  description?: string
  /** JSON Schema-ish hint: "string" | "number" | "boolean" | "object" | "array" */
  type?: string
  /** Whether this field is required to produce a valid payload */
  required?: boolean
}

/** A flat-path source variable available from the surrounding flow state. */
export interface SourceVariable {
  path: string
  label?: string
  type?: string
}

/** Per-target binding kind — drives the editor row's UI. */
export type BindingKind = 'unset' | 'constant' | 'variable' | 'expression'

export interface TargetBinding {
  kind: BindingKind
  /** Constant string (kind=constant), variable token (kind=variable), or JSONata expression (kind=expression). */
  value: string
}

/** The serialized template form — round-trips through the backend mapper. */
export type SerializedMapping = Record<string, unknown>

/** Working state on the UI side: target path → binding. */
export type MappingMap = Record<string, TargetBinding>
