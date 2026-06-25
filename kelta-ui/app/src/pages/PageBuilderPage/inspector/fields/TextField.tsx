/** `kind:'text'` → single-line text input. Writes a raw string scalar. */
import React from 'react'
import type { FieldEditorProps } from './types'

const INPUT_CLASS =
  'p-2 text-sm text-foreground bg-background border border-border rounded transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20'

export function TextField({ value, onChange, fieldId }: FieldEditorProps): React.ReactElement {
  return (
    <input
      type="text"
      className={INPUT_CLASS}
      value={typeof value === 'string' ? value : ''}
      onChange={(e) => onChange(e.target.value)}
      data-testid={fieldId}
    />
  )
}
