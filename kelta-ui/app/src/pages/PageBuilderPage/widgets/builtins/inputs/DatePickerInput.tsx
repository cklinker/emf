/**
 * `datepicker` control (slice 2f). Maps `date` → `Input type="date"` and `datetime` →
 * `Input type="datetime-local"`. Seeded from the already-resolved `defaultValue`.
 */
import React, { useState } from 'react'
import { Input } from '@/components/ui/input'
import { useFieldDef } from './useFieldDef'
import { InputEmpty, InputField } from './InputShell'
import { defaultAsString, type InputControlProps } from './types'

const TESTID = 'page-input-date'

export function DatePickerInput(props: InputControlProps): React.ReactElement {
  const { collection, field, defaultValue, required, readOnly, mode } = props
  const { fieldDef } = useFieldDef(collection, field)
  const [value, setValue] = useState(() => defaultAsString(defaultValue))

  if (!collection || !field) return <InputEmpty testid={TESTID} />

  const disabled = readOnly || mode === 'editor'
  const label = fieldDef?.displayName || field
  const type = fieldDef?.type === 'datetime' ? 'datetime-local' : 'date'

  return (
    <InputField testid={TESTID} htmlFor={`page-input-${field}`} label={label} required={required}>
      <Input
        id={`page-input-${field}`}
        type={type}
        value={value}
        disabled={disabled}
        onChange={(e) => setValue(e.target.value)}
      />
    </InputField>
  )
}
