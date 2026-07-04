/**
 * UpdateFieldValueDialog Component
 *
 * Interactive value-entry dialog for `update_field` quick actions that have no
 * preset `setValue`. Looks up the target field's definition from the collection
 * schema to render the right typed editor (via the FieldControl registry), or a
 * restricted <select> when the action config lists `allowedValues`.
 *
 * The dialog itself is the confirmation interaction — actions with
 * `requiresConfirmation` surface their `confirmationMessage` as the dialog
 * description rather than showing a second prompt.
 */

import React, { useState } from 'react'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { getFieldControl, type FieldControlContext } from '@/components/fieldControl'
import { useCollectionSchema, type FieldType } from '@/hooks/useCollectionSchema'
import type {
  QuickActionDefinition,
  QuickActionExecutionContext,
  UpdateFieldConfig,
} from '@/types/quickActions'

/** Matches the FieldControl edit-input styling (see fieldControl/controls.tsx). */
const SELECT_CLASS =
  'w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-primary disabled:cursor-not-allowed disabled:opacity-60'

export interface UpdateFieldValueDialogProps {
  /** The quick action being executed (title + optional confirmation message). */
  action: QuickActionDefinition
  /** The update_field configuration (fieldName, allowedValues). */
  config: UpdateFieldConfig
  /** Execution context — supplies collection name and the current record values. */
  executionContext: QuickActionExecutionContext
  /** Persist the coerced value. Rejects → the error is surfaced inline and the dialog stays open. */
  onSubmit: (value: unknown) => Promise<void>
  /** Close without saving. */
  onClose: () => void
}

export function UpdateFieldValueDialog({
  action,
  config,
  executionContext,
  onSubmit,
  onClose,
}: UpdateFieldValueDialogProps): React.ReactElement {
  const { fields } = useCollectionSchema(executionContext.collectionName)
  const fieldDef = fields.find((f) => f.name === config.fieldName)
  const fieldType: FieldType = fieldDef?.type ?? 'string'
  const control = getFieldControl(fieldType)
  const fieldLabel = fieldDef?.displayName || config.fieldName

  const ctx: FieldControlContext = {
    fieldName: config.fieldName,
    displayName: fieldLabel,
    enumValues: fieldDef?.enumValues,
    referenceOptions: fieldDef?.lookupOptions,
    required: fieldDef?.required,
  }

  const allowedValues = config.allowedValues ?? []
  const hasAllowedValues = allowedValues.length > 0
  const currentValue = executionContext.record?.[config.fieldName]

  // For the allowed-values select the raw value is always the option string;
  // for the generic editor it's whatever the control emits.
  const [rawValue, setRawValue] = useState<unknown>(() => {
    if (!hasAllowedValues) {
      return currentValue
    }
    const match = allowedValues.find((v) => String(v) === String(currentValue ?? ''))
    return match !== undefined ? String(match) : ''
  })
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const inputId = `quick-action-field-${config.fieldName}`
  const Edit = control.Edit

  const handleSubmit = async (): Promise<void> => {
    // Map an allowed-values selection back to the original (possibly non-string) value.
    const selected = hasAllowedValues
      ? (allowedValues.find((v) => String(v) === rawValue) ?? null)
      : rawValue
    const coerced = control.coerce(selected)
    const validationError = control.validate(coerced, ctx)
    if (validationError) {
      setError(validationError)
      return
    }
    setSaving(true)
    setError(null)
    try {
      await onSubmit(coerced)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Update failed')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{action.label}</DialogTitle>
          <DialogDescription>
            {action.confirmationMessage || `Set a new value for ${fieldLabel}.`}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1.5">
          <label htmlFor={inputId} className="text-sm font-medium">
            {fieldLabel}
            {ctx.required && <span className="ml-0.5 text-destructive">*</span>}
          </label>
          {hasAllowedValues ? (
            <select
              id={inputId}
              className={SELECT_CLASS}
              value={typeof rawValue === 'string' ? rawValue : ''}
              disabled={saving}
              onChange={(e) => {
                setError(null)
                setRawValue(e.target.value)
              }}
              aria-label={fieldLabel}
            >
              <option value="" />
              {allowedValues.map((v) => (
                <option key={String(v)} value={String(v)}>
                  {String(v)}
                </option>
              ))}
            </select>
          ) : (
            <Edit
              type={fieldType}
              value={rawValue}
              ctx={ctx}
              onChange={(next) => {
                setError(null)
                setRawValue(next)
              }}
              error={error ?? undefined}
              id={inputId}
            />
          )}
          {error && (
            <p
              className="text-xs text-destructive"
              role="alert"
              data-testid={`quick-action-field-error-${config.fieldName}`}
            >
              {error}
            </p>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={() => void handleSubmit()} disabled={saving}>
            {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
