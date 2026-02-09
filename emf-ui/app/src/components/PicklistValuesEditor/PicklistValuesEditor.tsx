import React, { useState, useCallback, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast } from '../Toast'
import { LoadingSpinner } from '../LoadingSpinner'
import { ErrorMessage } from '../ErrorMessage'
import type { PicklistValue } from '../../types/collections'
import styles from './PicklistValuesEditor.module.css'

export interface PicklistValuesEditorProps {
  picklistId: string
  picklistName: string
  onClose: () => void
}

interface LocalValue {
  key: string
  value: string
  label: string
  color: string
  isDefault: boolean
  active: boolean
  description: string
}

interface ValueErrors {
  value?: string
  label?: string
}

let nextKey = 0
function generateKey(): string {
  return `new-${++nextKey}`
}

function toLocalValues(values: PicklistValue[]): LocalValue[] {
  return values.map((v) => ({
    key: v.id ?? generateKey(),
    value: v.value,
    label: v.label,
    color: v.color ?? '',
    isDefault: v.isDefault,
    active: v.active,
    description: v.description ?? '',
  }))
}

export function PicklistValuesEditor({
  picklistId,
  picklistName,
  onClose,
}: PicklistValuesEditorProps): React.ReactElement {
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()
  const queryClient = useQueryClient()

  const [editedValues, setEditedValues] = useState<LocalValue[] | null>(null)
  const [errors, setErrors] = useState<Record<string, ValueErrors>>({})

  const {
    data: serverValues,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['picklist-values', picklistId],
    queryFn: () => apiClient.get<PicklistValue[]>(`/control/picklists/global/${picklistId}/values`),
  })

  const initialValues = useMemo(
    () => (serverValues ? toLocalValues(serverValues) : []),
    [serverValues]
  )

  const localValues = editedValues ?? initialValues

  const saveMutation = useMutation({
    mutationFn: (values: PicklistValue[]) =>
      apiClient.put<PicklistValue[]>(`/control/picklists/global/${picklistId}/values`, values),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['picklist-values', picklistId] })
      showToast(t('picklists.valueSaved'), 'success')
      onClose()
    },
    onError: (err: Error) => {
      showToast(err.message || t('errors.generic'), 'error')
    },
  })

  const validate = useCallback(
    (values: LocalValue[]): Record<string, ValueErrors> => {
      const newErrors: Record<string, ValueErrors> = {}
      const seenValues = new Set<string>()

      for (const v of values) {
        const rowErrors: ValueErrors = {}
        if (!v.value.trim()) {
          rowErrors.value = t('picklists.valueRequired')
        } else if (seenValues.has(v.value.trim())) {
          rowErrors.value = t('picklists.duplicateValue')
        } else {
          seenValues.add(v.value.trim())
        }
        if (!v.label.trim()) {
          rowErrors.label = t('picklists.labelRequired')
        }
        if (Object.keys(rowErrors).length > 0) {
          newErrors[v.key] = rowErrors
        }
      }
      return newErrors
    },
    [t]
  )

  const handleAdd = useCallback(() => {
    setEditedValues([
      ...localValues,
      {
        key: generateKey(),
        value: '',
        label: '',
        color: '',
        isDefault: false,
        active: true,
        description: '',
      },
    ])
  }, [localValues])

  const handleRemove = useCallback(
    (key: string) => {
      setEditedValues(localValues.filter((v) => v.key !== key))
      setErrors((prev) => {
        const next = { ...prev }
        delete next[key]
        return next
      })
    },
    [localValues]
  )

  const handleChange = useCallback(
    (key: string, field: keyof LocalValue, fieldValue: string | boolean) => {
      setEditedValues(localValues.map((v) => (v.key === key ? { ...v, [field]: fieldValue } : v)))
      if (typeof fieldValue === 'string') {
        setErrors((prev) => {
          const rowErrors = { ...prev[key] }
          delete rowErrors[field as keyof ValueErrors]
          if (Object.keys(rowErrors).length === 0) {
            const next = { ...prev }
            delete next[key]
            return next
          }
          return { ...prev, [key]: rowErrors }
        })
      }
    },
    [localValues]
  )

  const handleMoveUp = useCallback(
    (index: number) => {
      if (index <= 0) return
      const next = [...localValues]
      ;[next[index - 1], next[index]] = [next[index], next[index - 1]]
      setEditedValues(next)
    },
    [localValues]
  )

  const handleMoveDown = useCallback(
    (index: number) => {
      if (index >= localValues.length - 1) return
      const next = [...localValues]
      ;[next[index], next[index + 1]] = [next[index + 1], next[index]]
      setEditedValues(next)
    },
    [localValues]
  )

  const handleSave = useCallback(() => {
    const validationErrors = validate(localValues)
    setErrors(validationErrors)
    if (Object.keys(validationErrors).length > 0) return

    const payload: PicklistValue[] = localValues.map((v, index) => ({
      value: v.value.trim(),
      label: v.label.trim(),
      isDefault: v.isDefault,
      active: v.active,
      sortOrder: index,
      color: v.color.trim() || undefined,
      description: v.description.trim() || undefined,
    }))
    saveMutation.mutate(payload)
  }, [localValues, validate, saveMutation])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
      }
    },
    [onClose]
  )

  const isSaving = saveMutation.isPending

  return (
    <div
      className={styles.modalOverlay}
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
      onKeyDown={handleKeyDown}
      role="presentation"
      data-testid="picklist-values-overlay"
    >
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions */}
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="values-editor-title"
        onMouseDown={(e) => e.stopPropagation()}
        data-testid="picklist-values-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="values-editor-title" className={styles.modalTitle}>
            {t('picklists.valuesTitle')} — {picklistName}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onClose}
            aria-label={t('common.close')}
            data-testid="values-editor-close"
          >
            &times;
          </button>
        </div>

        <div className={styles.modalBody}>
          {isLoading ? (
            <div className={styles.loadingContainer}>
              <LoadingSpinner size="medium" label={t('common.loading')} />
            </div>
          ) : error ? (
            <ErrorMessage error={error instanceof Error ? error : new Error(t('errors.generic'))} />
          ) : localValues.length === 0 ? (
            <div className={styles.emptyState} data-testid="values-empty-state">
              <p>{t('picklists.noValues')}</p>
            </div>
          ) : (
            <div className={styles.valuesList} data-testid="values-list">
              {localValues.map((v, index) => (
                <div key={v.key} className={styles.valueRow} data-testid={`value-row-${index}`}>
                  <div className={styles.orderButtons}>
                    <button
                      type="button"
                      className={styles.orderButton}
                      onClick={() => handleMoveUp(index)}
                      disabled={index === 0 || isSaving}
                      aria-label={t('picklists.moveUp')}
                      data-testid={`value-move-up-${index}`}
                    >
                      &#9650;
                    </button>
                    <button
                      type="button"
                      className={styles.orderButton}
                      onClick={() => handleMoveDown(index)}
                      disabled={index === localValues.length - 1 || isSaving}
                      aria-label={t('picklists.moveDown')}
                      data-testid={`value-move-down-${index}`}
                    >
                      &#9660;
                    </button>
                  </div>

                  <div className={styles.fieldGroup}>
                    <label className={styles.fieldLabel} htmlFor={`value-${v.key}`}>
                      Value *
                    </label>
                    <input
                      id={`value-${v.key}`}
                      type="text"
                      className={`${styles.input} ${errors[v.key]?.value ? styles.inputError : ''}`}
                      value={v.value}
                      onChange={(e) => handleChange(v.key, 'value', e.target.value)}
                      placeholder={t('picklists.valuePlaceholder')}
                      disabled={isSaving}
                      data-testid={`value-input-${index}`}
                    />
                    {errors[v.key]?.value && (
                      <span className={styles.errorText}>{errors[v.key].value}</span>
                    )}
                  </div>

                  <div className={styles.fieldGroup}>
                    <label className={styles.fieldLabel} htmlFor={`label-${v.key}`}>
                      Label *
                    </label>
                    <input
                      id={`label-${v.key}`}
                      type="text"
                      className={`${styles.input} ${errors[v.key]?.label ? styles.inputError : ''}`}
                      value={v.label}
                      onChange={(e) => handleChange(v.key, 'label', e.target.value)}
                      placeholder={t('picklists.labelPlaceholder')}
                      disabled={isSaving}
                      data-testid={`label-input-${index}`}
                    />
                    {errors[v.key]?.label && (
                      <span className={styles.errorText}>{errors[v.key].label}</span>
                    )}
                  </div>

                  <div className={styles.fieldGroup}>
                    <label className={styles.fieldLabel} htmlFor={`color-${v.key}`}>
                      Color
                    </label>
                    <input
                      id={`color-${v.key}`}
                      type="text"
                      className={styles.input}
                      value={v.color}
                      onChange={(e) => handleChange(v.key, 'color', e.target.value)}
                      placeholder={t('picklists.colorPlaceholder')}
                      disabled={isSaving}
                      data-testid={`color-input-${index}`}
                    />
                  </div>

                  <div className={styles.checkboxGroup}>
                    <input
                      id={`default-${v.key}`}
                      type="checkbox"
                      checked={v.isDefault}
                      onChange={(e) => handleChange(v.key, 'isDefault', e.target.checked)}
                      disabled={isSaving}
                      data-testid={`default-checkbox-${index}`}
                    />
                    <label className={styles.checkboxLabel} htmlFor={`default-${v.key}`}>
                      Default
                    </label>
                  </div>

                  <div className={styles.checkboxGroup}>
                    <input
                      id={`active-${v.key}`}
                      type="checkbox"
                      checked={v.active}
                      onChange={(e) => handleChange(v.key, 'active', e.target.checked)}
                      disabled={isSaving}
                      data-testid={`active-checkbox-${index}`}
                    />
                    <label className={styles.checkboxLabel} htmlFor={`active-${v.key}`}>
                      Active
                    </label>
                  </div>

                  <button
                    type="button"
                    className={styles.removeButton}
                    onClick={() => handleRemove(v.key)}
                    disabled={isSaving}
                    aria-label={t('picklists.removeValue')}
                    data-testid={`value-remove-${index}`}
                  >
                    &times;
                  </button>
                </div>
              ))}
            </div>
          )}

          {!isLoading && !error && (
            <button
              type="button"
              className={styles.addButton}
              onClick={handleAdd}
              disabled={isSaving}
              data-testid="add-value-button"
            >
              + {t('picklists.addValue')}
            </button>
          )}
        </div>

        <div className={styles.modalFooter}>
          <button
            type="button"
            className={styles.cancelButton}
            onClick={onClose}
            disabled={isSaving}
            data-testid="values-editor-cancel"
          >
            {t('common.cancel')}
          </button>
          <button
            type="button"
            className={styles.saveButton}
            onClick={handleSave}
            disabled={isSaving || isLoading}
            data-testid="values-editor-save"
          >
            {isSaving ? (
              <>
                <LoadingSpinner size="small" />
                <span>{t('common.saving')}</span>
              </>
            ) : (
              t('picklists.saveValues')
            )}
          </button>
        </div>
      </div>
    </div>
  )
}

export default PicklistValuesEditor
