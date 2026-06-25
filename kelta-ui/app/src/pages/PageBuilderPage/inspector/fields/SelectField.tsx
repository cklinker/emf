/** `kind:'select'` → native select over `schema.options`. Writes the selected option's value string. */
import React from 'react'
import type { FieldEditorProps } from './types'

export function SelectField({
  schema,
  value,
  onChange,
  fieldId,
}: FieldEditorProps): React.ReactElement {
  const options = schema.options ?? []
  return (
    <select
      className="p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
      value={typeof value === 'string' ? value : ''}
      onChange={(e) => onChange(e.target.value)}
      data-testid={fieldId}
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  )
}
