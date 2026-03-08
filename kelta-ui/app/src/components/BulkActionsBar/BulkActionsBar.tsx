/**
 * BulkActionsBar Component
 *
 * A floating action bar that appears at the bottom of the viewport when
 * records are selected in the list view. Provides bulk operations including:
 * - Export selected records
 * - Change field value across selected records
 * - Delete selected records
 * - Submit selected records for approval (optional)
 *
 * Includes a modal for changing field values with type-appropriate inputs
 * and a progress indicator for batch operations.
 *
 * Supports keyboard navigation (Escape to close modal) and i18n.
 */

import React, { useState, useCallback, useEffect } from 'react'
import { useI18n } from '../../context/I18nContext'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'

/** Editable field types that can be bulk-updated */
const EDITABLE_FIELD_TYPES = new Set(['string', 'number', 'boolean', 'date', 'datetime'])

/**
 * Field descriptor for the Change Field Value modal
 */
interface FieldDescriptor {
  name: string
  displayName?: string
  type: string
}

/**
 * Props for the BulkActionsBar component
 */
export interface BulkActionsBarProps {
  /** Number of currently selected records */
  selectedCount: number
  /** Callback to deselect all records */
  onDeselectAll: () => void
  /** Callback to export selected records */
  onExport: () => void
  /** Callback to delete selected records */
  onDelete: () => void
  /** Callback to change a field value across selected records */
  onChangeFieldValue: (fieldName: string, value: unknown) => void
  /** Optional callback to submit selected records for approval */
  onSubmitForApproval?: () => void
  /** Available fields for the Change Field Value modal */
  fields: FieldDescriptor[]
  /** Whether a bulk operation is currently in progress */
  isProcessing?: boolean
  /** Progress of the current bulk operation */
  processingProgress?: { current: number; total: number }
}

/**
 * BulkActionsBar Component
 *
 * Renders a floating bar at the bottom of the viewport with bulk action
 * buttons when records are selected. Hides when no records are selected.
 */
export function BulkActionsBar({
  selectedCount,
  onDeselectAll,
  onExport,
  onDelete,
  onChangeFieldValue,
  onSubmitForApproval,
  fields,
  isProcessing = false,
  processingProgress,
}: BulkActionsBarProps): React.ReactElement {
  const { t } = useI18n()

  const [modalOpen, setModalOpen] = useState(false)
  const [selectedField, setSelectedField] = useState('')
  const [fieldValue, setFieldValue] = useState<unknown>('')

  // Filter fields to only editable types
  const editableFields = fields.filter((f) => EDITABLE_FIELD_TYPES.has(f.type))

  // Get the selected field descriptor
  const selectedFieldDescriptor = editableFields.find((f) => f.name === selectedField)

  // Reset modal state when it opens/closes
  const openModal = useCallback(() => {
    setSelectedField('')
    setFieldValue('')
    setModalOpen(true)
  }, [])

  const closeModal = useCallback(() => {
    setModalOpen(false)
    setSelectedField('')
    setFieldValue('')
  }, [])

  // Handle Escape key to close modal (backup for Dialog built-in)
  useEffect(() => {
    if (!modalOpen) return

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        closeModal()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [modalOpen, closeModal])

  // Reset value when field selection changes
  const handleFieldChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    const fieldName = e.target.value
    setSelectedField(fieldName)
    setFieldValue('')
  }, [])

  // Handle Apply in the modal
  const handleApply = useCallback(() => {
    if (!selectedField) return
    onChangeFieldValue(selectedField, fieldValue)
    closeModal()
  }, [selectedField, fieldValue, onChangeFieldValue, closeModal])

  // Calculate progress percentage
  const progressPercent =
    processingProgress && processingProgress.total > 0
      ? Math.round((processingProgress.current / processingProgress.total) * 100)
      : 0

  // Render the type-appropriate value input
  const renderValueInput = (): React.ReactElement | null => {
    if (!selectedFieldDescriptor) return null

    const inputBaseClass =
      'w-full p-2 border border-border rounded-md text-sm bg-background text-foreground box-border focus:outline-2 focus:-outline-offset-1 focus:outline-primary focus:border-primary'

    switch (selectedFieldDescriptor.type) {
      case 'boolean':
        return (
          <div className="flex items-center gap-2 py-2">
            <input
              type="checkbox"
              id="bulk-field-value"
              checked={!!fieldValue}
              onChange={(e) => setFieldValue(e.target.checked)}
              className="w-4 h-4 cursor-pointer"
            />
            <span className="text-sm text-foreground">
              {selectedFieldDescriptor.displayName || selectedFieldDescriptor.name}
            </span>
          </div>
        )
      case 'number':
        return (
          <input
            type="number"
            id="bulk-field-value"
            value={fieldValue as string}
            onChange={(e) => setFieldValue(e.target.value === '' ? '' : Number(e.target.value))}
            placeholder={t('bulkActions.enterValue')}
            className={inputBaseClass}
          />
        )
      case 'date':
        return (
          <input
            type="date"
            id="bulk-field-value"
            value={fieldValue as string}
            onChange={(e) => setFieldValue(e.target.value)}
            className={inputBaseClass}
          />
        )
      case 'datetime':
        return (
          <input
            type="datetime-local"
            id="bulk-field-value"
            value={fieldValue as string}
            onChange={(e) => setFieldValue(e.target.value)}
            className={inputBaseClass}
          />
        )
      default:
        // string and any other type
        return (
          <input
            type="text"
            id="bulk-field-value"
            value={fieldValue as string}
            onChange={(e) => setFieldValue(e.target.value)}
            placeholder={t('bulkActions.enterValue')}
            className={inputBaseClass}
          />
        )
    }
  }

  return (
    <>
      {/* Floating bulk actions bar */}
      <div
        className={cn(
          selectedCount > 0
            ? cn(
                'fixed bottom-6 left-1/2 -translate-x-1/2',
                'bg-background border border-border rounded-lg',
                'px-4 py-3 flex items-center gap-4',
                'shadow-[0_4px_20px_rgba(0,0,0,0.15)] dark:shadow-[0_4px_20px_rgba(0,0,0,0.4)]',
                'z-[1000] min-w-[500px]',
                'animate-in slide-in-from-bottom-2 fade-in motion-reduce:animate-none'
              )
            : 'hidden'
        )}
        data-testid="bulk-actions-bar"
      >
        {/* Selected count */}
        <span className="font-semibold text-sm whitespace-nowrap text-foreground">
          {t('bulkActions.selectedCount', { count: selectedCount })}
        </span>

        {/* Progress indicator (shown during processing) */}
        {isProcessing && processingProgress && (
          <div className="flex flex-col gap-1 flex-1 min-w-[120px]">
            <span className="text-xs text-muted-foreground whitespace-nowrap">
              {t('bulkActions.processing', {
                current: processingProgress.current,
                total: processingProgress.total,
              })}
            </span>
            <div className="h-1 bg-muted rounded-sm overflow-hidden">
              <div
                className="h-full bg-primary transition-[width] duration-300 motion-reduce:transition-none"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
          </div>
        )}

        {/* Action buttons */}
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={onExport} disabled={isProcessing}>
            {t('bulkActions.export')}
          </Button>

          <Button variant="outline" size="sm" onClick={openModal} disabled={isProcessing}>
            {t('bulkActions.changeFieldValue')}
          </Button>

          <Button variant="destructive" size="sm" onClick={onDelete} disabled={isProcessing}>
            {t('bulkActions.delete')}
          </Button>

          {onSubmitForApproval && (
            <Button
              variant="outline"
              size="sm"
              onClick={onSubmitForApproval}
              disabled={isProcessing}
            >
              {t('bulkActions.submitForApproval')}
            </Button>
          )}
        </div>

        {/* Deselect All link */}
        <button
          type="button"
          className={cn(
            'text-primary cursor-pointer text-sm bg-transparent border-0 p-0',
            'no-underline whitespace-nowrap',
            'transition-colors motion-reduce:transition-none',
            'hover:underline',
            'focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2'
          )}
          onClick={onDeselectAll}
        >
          {t('common.deselectAll')}
        </button>
      </div>

      {/* Change Field Value Modal */}
      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent data-testid="bulk-change-value-modal">
          <DialogHeader>
            <DialogTitle>{t('bulkActions.changeFieldValue')}</DialogTitle>
          </DialogHeader>

          {/* Field selector */}
          <div className="mb-4">
            <label
              htmlFor="bulk-field-select"
              className="block text-sm font-medium mb-1 text-muted-foreground"
            >
              {t('bulkActions.selectField')}
            </label>
            <select
              id="bulk-field-select"
              value={selectedField}
              onChange={handleFieldChange}
              className="w-full p-2 border border-border rounded-md text-sm bg-background text-foreground box-border focus:outline-2 focus:-outline-offset-1 focus:outline-primary focus:border-primary"
            >
              <option value="">{t('bulkActions.selectField')}</option>
              {editableFields.map((field) => (
                <option key={field.name} value={field.name}>
                  {field.displayName || field.name}
                </option>
              ))}
            </select>
          </div>

          {/* Value input (type-appropriate) */}
          {selectedFieldDescriptor && (
            <div className="mb-4">
              <label
                htmlFor="bulk-field-value"
                className="block text-sm font-medium mb-1 text-muted-foreground"
              >
                {t('bulkActions.enterValue')}
              </label>
              {renderValueInput()}
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={closeModal}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleApply} disabled={!selectedField}>
              {t('bulkActions.apply')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

export default BulkActionsBar
