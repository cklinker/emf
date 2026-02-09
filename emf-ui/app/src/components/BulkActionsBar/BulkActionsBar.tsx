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
import styles from './BulkActionsBar.module.css'

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

  // Render the type-appropriate value input
  const renderValueInput = (): React.ReactElement | null => {
    if (!selectedFieldDescriptor) return null

    switch (selectedFieldDescriptor.type) {
      case 'boolean':
        return (
          <div className={styles.checkboxWrapper}>
            <input
              type="checkbox"
              id="bulk-field-value"
              checked={!!fieldValue}
              onChange={(e) => setFieldValue(e.target.checked)}
            />
            <span>{selectedFieldDescriptor.displayName || selectedFieldDescriptor.name}</span>
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
          />
        )
      case 'date':
        return (
          <input
            type="date"
            id="bulk-field-value"
            value={fieldValue as string}
            onChange={(e) => setFieldValue(e.target.value)}
          />
        )
      case 'datetime':
        return (
          <input
            type="datetime-local"
            id="bulk-field-value"
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
        className={selectedCount > 0 ? styles.bar : styles.barHidden}
        data-testid="bulk-actions-bar"
      >
        {/* Selected count */}
        <span className={styles.count}>
          {t('bulkActions.selectedCount', { count: selectedCount })}
        </span>

        {/* Progress indicator (shown during processing) */}
        {isProcessing && processingProgress && (
          <div className={styles.progressSection}>
            <span className={styles.progressText}>
              {t('bulkActions.processing', {
                current: processingProgress.current,
                total: processingProgress.total,
              })}
            </span>
            <div className={styles.progressBar}>
              <div className={styles.progressFill} style={{ width: `${progressPercent}%` }} />
            </div>
          </div>
        )}

        {/* Action buttons */}
        <div className={styles.actions}>
          <button
            type="button"
            className={styles.actionButton}
            onClick={onExport}
            disabled={isProcessing}
          >
            {t('bulkActions.export')}
          </button>

          <button
            type="button"
            className={styles.actionButton}
            onClick={openModal}
            disabled={isProcessing}
          >
            {t('bulkActions.changeFieldValue')}
          </button>

          <button
            type="button"
            className={`${styles.actionButton} ${styles.dangerButton}`}
            onClick={onDelete}
            disabled={isProcessing}
          >
            {t('bulkActions.delete')}
          </button>

          {onSubmitForApproval && (
            <button
              type="button"
              className={styles.actionButton}
              onClick={onSubmitForApproval}
              disabled={isProcessing}
            >
              {t('bulkActions.submitForApproval')}
            </button>
          )}
        </div>

        {/* Deselect All link */}
        <button type="button" className={styles.deselectLink} onClick={onDeselectAll}>
          {t('common.deselectAll')}
        </button>
      </div>

      {/* Change Field Value Modal */}
      {modalOpen && (
        <div
          className={styles.modalOverlay}
          role="presentation"
          onClick={handleOverlayClick}
          data-testid="bulk-change-value-modal"
        >
          <div className={styles.modal} role="dialog" aria-modal="true">
            <h3 className={styles.modalTitle}>{t('bulkActions.changeFieldValue')}</h3>

            {/* Field selector */}
            <div className={styles.formGroup}>
              <label htmlFor="bulk-field-select">{t('bulkActions.selectField')}</label>
              <select id="bulk-field-select" value={selectedField} onChange={handleFieldChange}>
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
              <div className={styles.formGroup}>
                <label htmlFor="bulk-field-value">{t('bulkActions.enterValue')}</label>
                {renderValueInput()}
              </div>
            )}

            {/* Modal actions */}
            <div className={styles.modalActions}>
              <button type="button" className={styles.cancelButton} onClick={closeModal}>
                {t('common.cancel')}
              </button>
              <button
                type="button"
                className={styles.applyButton}
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
