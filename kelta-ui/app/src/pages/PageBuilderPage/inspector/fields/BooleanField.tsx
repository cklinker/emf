/** `kind:'boolean'` → checkbox. Writes a boolean. */
import React from 'react'
import type { FieldEditorProps } from './types'

export function BooleanField({
  value,
  onChange,
  fieldId,
}: FieldEditorProps<boolean>): React.ReactElement {
  return (
    <input
      type="checkbox"
      className="size-4 rounded border-border accent-primary"
      checked={value === true}
      onChange={(e) => onChange(e.target.checked)}
      data-testid={fieldId}
    />
  )
}
