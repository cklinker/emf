/**
 * `text-input` control (slice 2f). Maps a string-like `FieldType` (`string`/`phone`/`email`/`url`/
 * `external_id`/`auto_number`) to a shadcn `Input` with the right HTML `type`. `auto_number` renders
 * disabled (computed on write). Local state is seeded from the already-resolved `defaultValue`.
 */
import React, { useState } from 'react'
import { Input } from '@/components/ui/input'
import { useFieldDef } from './useFieldDef'
import { InputEmpty, InputField } from './InputShell'
import { defaultAsString, type InputControlProps } from './types'

const TESTID = 'page-input-text'

/** HTML input type per UI FieldType. */
function htmlType(fieldType: string | undefined): string {
  switch (fieldType) {
    case 'email':
      return 'email'
    case 'url':
      return 'url'
    case 'phone':
      return 'tel'
    default:
      return 'text'
  }
}

export function TextInput(props: InputControlProps): React.ReactElement {
  const { collection, field, defaultValue, required, readOnly, placeholder, mode } = props
  const { fieldDef } = useFieldDef(collection, field)
  const [value, setValue] = useState(() => defaultAsString(defaultValue))

  if (!collection || !field) return <InputEmpty testid={TESTID} />

  const disabled = readOnly || mode === 'editor' || fieldDef?.type === 'auto_number'
  const label = fieldDef?.displayName || field

  return (
    <InputField testid={TESTID} htmlFor={`page-input-${field}`} label={label} required={required}>
      <Input
        id={`page-input-${field}`}
        type={htmlType(fieldDef?.type)}
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(e) => setValue(e.target.value)}
      />
    </InputField>
  )
}
