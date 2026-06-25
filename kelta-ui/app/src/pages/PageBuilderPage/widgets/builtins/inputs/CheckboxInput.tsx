/**
 * `checkbox` control (slice 2f). Maps `boolean` to the shadcn `Checkbox` with an inline label. Seeded
 * from the already-resolved `defaultValue`.
 */
import React, { useState } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { useFieldDef } from './useFieldDef'
import { InputEmpty, InputLabel } from './InputShell'
import { defaultAsBoolean, type InputControlProps } from './types'

const TESTID = 'page-input-checkbox'

export function CheckboxInput(props: InputControlProps): React.ReactElement {
  const { collection, field, defaultValue, required, readOnly, mode } = props
  const { fieldDef } = useFieldDef(collection, field)
  const [checked, setChecked] = useState(() => defaultAsBoolean(defaultValue))

  if (!collection || !field) return <InputEmpty testid={TESTID} />

  const disabled = readOnly || mode === 'editor'
  const label = fieldDef?.displayName || field

  return (
    <div className="flex items-center gap-2" data-testid={TESTID}>
      <Checkbox
        id={`page-input-${field}`}
        checked={checked}
        disabled={disabled}
        onCheckedChange={(v) => setChecked(v === true)}
      />
      <InputLabel htmlFor={`page-input-${field}`} label={label} required={required} />
    </div>
  )
}
