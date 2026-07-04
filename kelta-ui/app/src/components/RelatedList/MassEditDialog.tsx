/**
 * MassEditDialog Component
 *
 * Value-entry dialog for the RelatedList mass-edit flow: pick one editable
 * field, enter a typed value (via the FieldControl registry), and apply it to
 * every selected row. Follows the UpdateFieldValueDialog composition pattern
 * (typed editor + coerce + validate at submit); the caller owns persistence —
 * a rejected `onSubmit` keeps the dialog open with the error inline.
 */

import React, { useMemo, useState } from 'react'
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
import type { FieldDefinition } from '@/hooks/useCollectionSchema'

/** Matches the FieldControl edit-input styling (see fieldControl/controls.tsx). */
const SELECT_CLASS =
  'w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-primary disabled:cursor-not-allowed disabled:opacity-60'

export interface MassEditDialogProps {
  /** Editable candidate fields (already filtered by the caller). */
  fields: FieldDefinition[]
  /** Tenant slug for reference-link context. */
  tenantSlug: string
  /** Number of selected rows the value will be applied to. */
  selectedCount: number
  /** Persist the coerced value. Rejects → the error is surfaced inline and the dialog stays open. */
  onSubmit: (fieldName: string, value: unknown) => Promise<void>
  /** Close without saving. */
  onClose: () => void
}

export function MassEditDialog({
  fields,
  tenantSlug,
  selectedCount,
  onSubmit,
  onClose,
}: MassEditDialogProps): React.ReactElement {
  const [fieldName, setFieldName] = useState<string>(fields[0]?.name ?? '')
  const [rawValue, setRawValue] = useState<unknown>(undefined)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const field = useMemo(() => fields.find((f) => f.name === fieldName), [fields, fieldName])
  const control = field ? getFieldControl(field.type) : undefined
  const fieldLabel = field?.displayName || field?.name || ''

  const ctx: FieldControlContext = {
    fieldName: field?.name,
    displayName: fieldLabel,
    tenantSlug,
    targetCollection: field?.referenceTarget,
    enumValues: field?.enumValues,
    referenceOptions: field?.lookupOptions,
    required: field?.required,
  }

  const handleFieldChange = (nextName: string): void => {
    setFieldName(nextName)
    setRawValue(undefined)
    setError(null)
  }

  const handleSubmit = async (): Promise<void> => {
    if (!field || !control) return
    const coerced = control.coerce(rawValue)
    const validationError = control.validate(coerced, ctx)
    if (validationError) {
      setError(validationError)
      return
    }
    setSaving(true)
    setError(null)
    try {
      await onSubmit(field.name, coerced)
      onClose()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Update failed')
    } finally {
      setSaving(false)
    }
  }

  const Edit = control?.Edit
  const valueInputId = 'mass-edit-value'

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
    >
      <DialogContent data-testid="mass-edit-dialog">
        <DialogHeader>
          <DialogTitle>Edit field</DialogTitle>
          <DialogDescription>
            Set one field to a new value across {selectedCount} selected{' '}
            {selectedCount === 1 ? 'record' : 'records'}.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-1.5">
            <label htmlFor="mass-edit-field" className="text-sm font-medium">
              Field
            </label>
            <select
              id="mass-edit-field"
              className={SELECT_CLASS}
              value={fieldName}
              disabled={saving}
              onChange={(e) => handleFieldChange(e.target.value)}
              data-testid="mass-edit-field-select"
            >
              {fields.map((f) => (
                <option key={f.name} value={f.name}>
                  {f.displayName || f.name}
                </option>
              ))}
            </select>
          </div>
          {field && Edit && (
            <div className="space-y-1.5">
              <label htmlFor={valueInputId} className="text-sm font-medium">
                {fieldLabel}
                {ctx.required && <span className="ml-0.5 text-destructive">*</span>}
              </label>
              <Edit
                key={field.name}
                type={field.type}
                value={rawValue}
                ctx={ctx}
                onChange={(next) => {
                  setError(null)
                  setRawValue(next)
                }}
                error={error ?? undefined}
                id={valueInputId}
              />
            </div>
          )}
          {error && (
            <p className="text-xs text-destructive" role="alert" data-testid="mass-edit-error">
              {error}
            </p>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button
            onClick={() => void handleSubmit()}
            disabled={saving || !field}
            data-testid="mass-edit-submit"
          >
            {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Update {selectedCount} {selectedCount === 1 ? 'record' : 'records'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
