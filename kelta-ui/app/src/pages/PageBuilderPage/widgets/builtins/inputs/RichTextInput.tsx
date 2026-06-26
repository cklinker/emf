/**
 * `rich-text` control (slice 2f).
 *
 * SECURITY (parent §"Security — binding & action output safety", spec §3.1/§6.2a): `rich_text` values
 * are author/data-controlled HTML, and a `{ $bind }` default can resolve to a hostile string. Any
 * *render* of HTML-bearing output goes through the SAME sanitizer `FieldRenderer`'s `rich_text` path
 * uses — here by rendering the read-only / editor-preview value through `<FieldRenderer type="rich_text">`,
 * which strips all tags before React escapes the result. We NEVER `dangerouslySetInnerHTML` on the bound
 * HTML. The interactive edit control (runtime) is the TipTap `RichTextEditor`, which parses HTML into its
 * own document model (it does not inject raw HTML into the page).
 */
import React, { useState } from 'react'
import { RichTextEditor } from '@/components/RichTextEditor'
import { FieldRenderer } from '@/components/FieldRenderer/FieldRenderer'
import { useFieldDef } from './useFieldDef'
import { InputEmpty, InputField } from './InputShell'
import { defaultAsString, type InputControlProps } from './types'

const TESTID = 'page-input-richtext'

export function RichTextInput(props: InputControlProps): React.ReactElement {
  const { collection, field, defaultValue, required, readOnly, placeholder, mode } = props
  const { fieldDef } = useFieldDef(collection, field)
  const [value, setValue] = useState(() => defaultAsString(defaultValue))

  if (!collection || !field) return <InputEmpty testid={TESTID} />

  const label = fieldDef?.displayName || field
  // Editor-preview and read-only render the value as SANITIZED display (FieldRenderer strips tags).
  const showStatic = readOnly || mode === 'editor'

  return (
    <InputField testid={TESTID} label={label} required={required}>
      {showStatic ? (
        <div
          className="rounded-md border border-border p-3 text-sm"
          data-testid={`${TESTID}-display`}
        >
          <FieldRenderer type="rich_text" value={value} truncate={false} />
        </div>
      ) : (
        <RichTextEditor
          value={value}
          onChange={setValue}
          placeholder={placeholder}
          testId={`${TESTID}-editor`}
        />
      )}
    </InputField>
  )
}
