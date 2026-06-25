/**
 * `kind:'number'` → numeric input. Writes `number | undefined` — a blank input stores `undefined`
 * (NOT 0), matching the legacy table row-limit editor.
 */
import React from 'react'
import type { FieldEditorProps } from './types'

export function NumberField({
  value,
  onChange,
  fieldId,
}: FieldEditorProps<number | undefined>): React.ReactElement {
  return (
    <input
      type="number"
      className="p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
      value={typeof value === 'number' ? value : ''}
      onChange={(e) => onChange(e.target.value === '' ? undefined : Number(e.target.value))}
      data-testid={fieldId}
    />
  )
}
