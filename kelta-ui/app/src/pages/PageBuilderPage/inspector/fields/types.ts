/**
 * Shared contract for every inspector field-editor component. `Inspector` renders all field kinds
 * uniformly through this shape: it reads the value at `schema.key` (via `getByPath`) and splices the
 * `onChange` result back into `props` at that key (via `setByPath`).
 */
import type { PropValue, PageComponent } from '../../model/pageModel'
import type { PropFieldSchema } from '../../widgets/types'

/** Base props handed to every field-editor component. */
export interface FieldEditorProps<V = PropValue> {
  schema: PropFieldSchema
  /** Current value at schema.key (already read out of props via getByPath). May be undefined. */
  value: V | undefined
  /**
   * Write the new value for this field. Inspector splices it back into props at schema.key.
   * For literal editors this is a scalar; BindableField may pass a Binding.
   */
  onChange: (value: V) => void
  /** The node being edited — needed by field-picker to read a sibling collection value. */
  node: PageComponent
  /** Stable id/testid base, e.g. `property-field-${schema.key}`. */
  fieldId: string
}
