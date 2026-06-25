/**
 * `dropdown` control (slice 2f). Maps `picklist` to a native `<select>` populated from
 * `usePicklistOptions` (FIELD/GLOBAL source resolution), with a "Select…" empty option. Mirrors the
 * markup `ObjectFormPage` renders for picklist fields. Seeded from the already-resolved `defaultValue`.
 */
import React, { useState } from 'react'
import { useI18n } from '@/context/I18nContext'
import { useFieldDef } from './useFieldDef'
import { usePicklistOptions } from './usePicklistOptions'
import { InputEmpty, InputField } from './InputShell'
import { defaultAsString, type InputControlProps } from './types'

const TESTID = 'page-input-dropdown'

export function DropdownInput(props: InputControlProps): React.ReactElement {
  const { collection, field, defaultValue, required, readOnly, mode } = props
  const { t } = useI18n()
  const { fieldDef } = useFieldDef(collection, field)
  const { options } = usePicklistOptions(fieldDef, mode === 'runtime')
  const [value, setValue] = useState(() => defaultAsString(defaultValue))

  if (!collection || !field) return <InputEmpty testid={TESTID} />

  const disabled = readOnly || mode === 'editor'
  const label = fieldDef?.displayName || field

  return (
    <InputField testid={TESTID} htmlFor={`page-input-${field}`} label={label} required={required}>
      <select
        id={`page-input-${field}`}
        className="h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
        value={value}
        disabled={disabled}
        onChange={(e) => setValue(e.target.value)}
      >
        <option value="">{t('builder.input.selectPlaceholder')}</option>
        {options.map((opt) => (
          <option key={opt} value={opt}>
            {opt}
          </option>
        ))}
      </select>
    </InputField>
  )
}
