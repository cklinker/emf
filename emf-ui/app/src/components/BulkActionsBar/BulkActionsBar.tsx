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

  // Handle Escape key to close modal
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

  // Handle overlay click (close on backdrop click)
  const handleOverlayClick = useCallback(
    (e: React.MouseEvent) => {
      if (e.target === e.currentTarget) {
        closeModal()
      }
    },
    [closeModal]
  )

  // Calculate progress percentage
  const progressPercent =
    processingProgress && processingProgress.total > 0
      ? Math.round((processingProgress.current / processingProgress.total) * 100)
      : 0

  // Shared input classes for form controls
  const inputClasses =
    'w-full p-2 border border-border rounded-md text-sm bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:border-primary'

  // Render the type-appropriate value input
  const renderValueInput = (): React.ReactElement | null => {
    if (!selectedFieldDescriptor) return null

    switch (selectedFieldDescriptor.type) {
      case 'boolean':
        return (
          <div className="flex items-center gap-2 py-2">
            <input
              type="checkbox"
              id="bulk-field-value"
              className="w-4 h-4 cursor-pointer accent-primary"
              checked={!!fieldValue}
              onChange={(e) => setFieldValue(e.target.checked)}
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
            className={inputClasses}
            value={fieldValue as string}
            onChange={(e) => setFieldValue(e.target.value === '' ? '' : Number(e.target.value))}
            placeholder={t('bulkActions.enterValue')}
          />
        )
      case 'date':
        return (
          <input
            type="date"
            id="bulk-field-value"
            className={inputClasses}
            value={fieldValue as string}
            onChange={(e) => setFieldValue(e.target.value)}
          />
        )
      case 'datetime':
        return (
          <input
            type="datetime-local"
            id="bulk-field-value"
            className={inputClasses}
            value={fieldValue as string}
            onChange={(e) => setFieldValue(e.target.value)}
          />
        )
      default:
        // string and any other type
        return (
          <input
            type="text"
            id="bulk-field-value"
            className={inputClasses}
            value={fieldValue as string}
            onChange={(e) => setFieldValue(e.target.value)}
            placeholder={t('bulkActions.enterValue')}
          />
        )
    }
  }

  return (
    <>
      {/* Floating bulk actions bar */}
      <div
        className={
          selectedCount > 0
            ? 'fixed bottom-6 left-1/2 -translate-x-1/2 bg-card border border-border rounded-lg px-4 py-3 flex items-center gap-4 shadow-[0_4px_20px_rgba(0,0,0,0.15)] z-[1000] min-w-[500px] animate-in slide-in-from-bottom-2'
            : 'hidden'
        }
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
                className="h-full bg-primary transition-[width] duration-300"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
          </div>
        )}

        {/* Action buttons */}
        <div className="flex gap-2">
          <button
            type="button"
            className="px-3 py-1 rounded-md text-sm cursor-pointer border border-border bg-card text-foreground transition-colors hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed"
            onClick={onExport}
            disabled={isProcessing}
          >
            {t('bulkActions.export')}
          </button>

          <button
            type="button"
            className="px-3 py-1 rounded-md text-sm cursor-pointer border border-border bg-card text-foreground transition-colors hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed"
            onClick={openModal}
            disabled={isProcessing}
          >
            {t('bulkActions.changeFieldValue')}
          </button>

          <button
            type="button"
            className={cn(
              'px-3 py-1 rounded-md text-sm cursor-pointer border border-border bg-card text-foreground transition-colors hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed',
              'text-destructive border-destructive hover:bg-destructive hover:text-white'
            )}
            onClick={onDelete}
            disabled={isProcessing}
          >
            {t('bulkActions.delete')}
          </button>

          {onSubmitForApproval && (
            <button
              type="button"
              className="px-3 py-1 rounded-md text-sm cursor-pointer border border-border bg-card text-foreground transition-colors hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed"
              onClick={onSubmitForApproval}
              disabled={isProcessing}
            >
              {t('bulkActions.submitForApproval')}
            </button>
          )}
        </div>

        {/* Deselect All link */}
        <button
          type="button"
          className="text-primary cursor-pointer text-sm bg-transparent border-0 p-0 no-underline whitespace-nowrap transition-colors hover:underline"
          onClick={onDeselectAll}
        >
          {t('common.deselectAll')}
        </button>
      </div>

      {/* Change Field Value Modal */}
      {modalOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-[1001] flex items-center justify-center"
          role="presentation"
          onClick={handleOverlayClick}
          data-testid="bulk-change-value-modal"
        >
          <div
            className="bg-card rounded-lg p-6 min-w-[400px] max-w-[500px] shadow-[0_8px_30px_rgba(0,0,0,0.2)]"
            role="dialog"
            aria-modal="true"
          >
            <h3 className="text-lg font-semibold m-0 mb-4 text-foreground">
              {t('bulkActions.changeFieldValue')}
            </h3>

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
                className={inputClasses}
                value={selectedField}
                onChange={handleFieldChange}
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

            {/* Modal actions */}
            <div className="flex justify-end gap-2 mt-4">
              <button
                type="button"
                className="bg-card border border-border text-foreground px-4 py-2 rounded-md text-sm cursor-pointer transition-colors hover:bg-accent"
                onClick={closeModal}
              >
                {t('common.cancel')}
              </button>
              <button
                type="button"
                className="bg-primary border border-primary text-primary-foreground px-4 py-2 rounded-md text-sm cursor-pointer transition-colors hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
                onClick={handleApply}
                disabled={!selectedField}
              >
                {t('bulkActions.apply')}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

export default BulkActionsBar
