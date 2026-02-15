import React, { useState, useCallback, useMemo, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { useToast } from '../Toast'
import { LoadingSpinner } from '../LoadingSpinner'
import type {
  RecordType,
  FieldDefinition,
  PicklistValue,
  RecordTypePicklistOverride,
} from '../../types/collections'
import styles from './RecordTypePicklistEditor.module.css'

export interface RecordTypePicklistEditorProps {
  collectionId: string
  recordType: RecordType
  fields: FieldDefinition[]
  onClose: () => void
  onSaved: () => void
}

interface FieldOverrideState {
  fieldId: string
  fieldName: string
  allValues: PicklistValue[]
  checkedValues: Set<string>
  defaultValue: string
  hasExistingOverride: boolean
}

export function RecordTypePicklistEditor({
  collectionId,
  recordType,
  fields,
  onClose,
  onSaved,
}: RecordTypePicklistEditorProps): React.ReactElement {
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isSaving, setIsSaving] = useState(false)
  const [fieldStates, setFieldStates] = useState<Record<string, FieldOverrideState>>({})
  const [collapsedFields, setCollapsedFields] = useState<Set<string>>(new Set())

  // Filter to only picklist/multi_picklist fields
  const picklistFields = useMemo(
    () => fields.filter((f) => f.type === 'picklist' || f.type === 'multi_picklist'),
    [fields]
  )

  // Fetch existing overrides for this record type
  const { data: existingOverrides = [], isLoading: loadingOverrides } = useQuery({
    queryKey: ['record-type-picklist-overrides', recordType.id],
    queryFn: () =>
      apiClient.get<RecordTypePicklistOverride[]>(
        `/control/collections/${collectionId}/record-types/${recordType.id}/picklists`
      ),
  })

  // Fetch values for each picklist field
  const { data: fieldValues, isLoading: loadingValues } = useQuery({
    queryKey: ['picklist-field-values', picklistFields.map((f) => f.id).join(',')],
    queryFn: async () => {
      const results: Record<string, PicklistValue[]> = {}
      await Promise.all(
        picklistFields.map(async (field) => {
          try {
            const values = await apiClient.get<PicklistValue[]>(
              `/control/picklists/fields/${field.id}/values`
            )
            results[field.id] = values
          } catch {
            results[field.id] = []
          }
        })
      )
      return results
    },
    enabled: picklistFields.length > 0,
  })

  // Initialize field states when data is loaded
  useEffect(() => {
    if (!fieldValues || loadingOverrides) return

    const overrideMap = new Map<string, RecordTypePicklistOverride>()
    for (const override of existingOverrides) {
      overrideMap.set(override.fieldId, override)
    }

    const newStates: Record<string, FieldOverrideState> = {}
    for (const field of picklistFields) {
      const values = fieldValues[field.id] ?? []
      const override = overrideMap.get(field.id)

      let checkedValues: Set<string>
      let defaultValue = ''
      const hasExistingOverride = !!override

      if (override) {
        // Parse available values from override (JSON string)
        try {
          const parsed = JSON.parse(override.availableValues) as string[]
          checkedValues = new Set(parsed)
        } catch {
          checkedValues = new Set(values.map((v) => v.value))
        }
        defaultValue = override.defaultValue ?? ''
      } else {
        // No override — all values are available
        checkedValues = new Set(values.map((v) => v.value))
      }

      newStates[field.id] = {
        fieldId: field.id,
        fieldName: field.displayName || field.name,
        allValues: values,
        checkedValues,
        defaultValue,
        hasExistingOverride,
      }
    }

    setFieldStates(newStates)
  }, [fieldValues, existingOverrides, picklistFields, loadingOverrides])

  const handleToggleValue = useCallback((fieldId: string, value: string) => {
    setFieldStates((prev) => {
      const state = prev[fieldId]
      if (!state) return prev

      const newChecked = new Set(state.checkedValues)
      if (newChecked.has(value)) {
        newChecked.delete(value)
        // If the default value was unchecked, clear it
        const newDefault = state.defaultValue === value ? '' : state.defaultValue
        return {
          ...prev,
          [fieldId]: { ...state, checkedValues: newChecked, defaultValue: newDefault },
        }
      } else {
        newChecked.add(value)
        return {
          ...prev,
          [fieldId]: { ...state, checkedValues: newChecked },
        }
      }
    })
  }, [])

  const handleDefaultChange = useCallback((fieldId: string, value: string) => {
    setFieldStates((prev) => {
      const state = prev[fieldId]
      if (!state) return prev
      return {
        ...prev,
        [fieldId]: { ...state, defaultValue: value },
      }
    })
  }, [])

  const handleToggleCollapse = useCallback((fieldId: string) => {
    setCollapsedFields((prev) => {
      const next = new Set(prev)
      if (next.has(fieldId)) {
        next.delete(fieldId)
      } else {
        next.add(fieldId)
      }
      return next
    })
  }, [])

  const handleSelectAll = useCallback((fieldId: string) => {
    setFieldStates((prev) => {
      const state = prev[fieldId]
      if (!state) return prev
      return {
        ...prev,
        [fieldId]: {
          ...state,
          checkedValues: new Set(state.allValues.map((v) => v.value)),
        },
      }
    })
  }, [])

  const handleDeselectAll = useCallback((fieldId: string) => {
    setFieldStates((prev) => {
      const state = prev[fieldId]
      if (!state) return prev
      return {
        ...prev,
        [fieldId]: {
          ...state,
          checkedValues: new Set<string>(),
          defaultValue: '',
        },
      }
    })
  }, [])

  const handleSave = useCallback(async () => {
    setIsSaving(true)
    try {
      const operations: Promise<void>[] = []

      for (const [fieldId, state] of Object.entries(fieldStates)) {
        const allSelected = state.allValues.every((v) => state.checkedValues.has(v.value))
        const noneDeselected = allSelected && state.checkedValues.size === state.allValues.length

        if (noneDeselected && !state.defaultValue) {
          // All values selected with no default — remove override if it existed
          if (state.hasExistingOverride) {
            operations.push(
              apiClient.delete(
                `/control/collections/${collectionId}/record-types/${recordType.id}/picklists/${fieldId}`
              )
            )
          }
        } else if (state.checkedValues.size > 0) {
          // Some values selected (or has a default value) — set/update override
          operations.push(
            apiClient.put(
              `/control/collections/${collectionId}/record-types/${recordType.id}/picklists/${fieldId}`,
              {
                availableValues: Array.from(state.checkedValues),
                defaultValue: state.defaultValue || null,
              }
            )
          )
        }
      }

      await Promise.all(operations)
      showToast(t('recordTypes.overrideSaved'), 'success')
      onSaved()
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : t('errors.generic')
      showToast(message, 'error')
    } finally {
      setIsSaving(false)
    }
  }, [fieldStates, collectionId, recordType.id, apiClient, showToast, t, onSaved])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
      }
    },
    [onClose]
  )

  const isLoading = loadingOverrides || loadingValues

  return (
    <div
      className={styles.modalOverlay}
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
      onKeyDown={handleKeyDown}
      role="presentation"
      data-testid="picklist-override-overlay"
    >
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions */}
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="picklist-override-title"
        onMouseDown={(e) => e.stopPropagation()}
        data-testid="picklist-override-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="picklist-override-title" className={styles.modalTitle}>
            {t('recordTypes.picklistOverrides')} &mdash; {recordType.name}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onClose}
            aria-label={t('common.close')}
            data-testid="picklist-override-close"
          >
            &times;
          </button>
        </div>

        <div className={styles.modalBody}>
          {isLoading ? (
            <div className={styles.loadingContainer}>
              <LoadingSpinner size="medium" label={t('common.loading')} />
            </div>
          ) : picklistFields.length === 0 ? (
            <div className={styles.emptyState} data-testid="no-picklist-fields">
              <p>{t('recordTypes.noPicklistFields')}</p>
            </div>
          ) : (
            <div className={styles.fieldSections}>
              {picklistFields.map((field) => {
                const state = fieldStates[field.id]
                if (!state) return null
                const isCollapsed = collapsedFields.has(field.id)
                const checkedCount = state.checkedValues.size
                const totalCount = state.allValues.length
                const allSelected = checkedCount === totalCount

                return (
                  <div
                    key={field.id}
                    className={styles.fieldSection}
                    data-testid={`field-section-${field.id}`}
                  >
                    <button
                      type="button"
                      className={styles.fieldSectionHeader}
                      onClick={() => handleToggleCollapse(field.id)}
                      aria-expanded={!isCollapsed}
                      data-testid={`field-header-${field.id}`}
                    >
                      <span className={styles.collapseIcon}>
                        {isCollapsed ? '\u25B6' : '\u25BC'}
                      </span>
                      <span className={styles.fieldSectionName}>{state.fieldName}</span>
                      <span className={styles.fieldSectionBadge}>
                        {allSelected
                          ? t('recordTypes.allValuesAvailable')
                          : t('recordTypes.restrictedValues', {
                              count: checkedCount,
                              total: totalCount,
                            })}
                      </span>
                    </button>

                    {!isCollapsed && (
                      <div className={styles.fieldSectionBody}>
                        {state.allValues.length === 0 ? (
                          <p className={styles.noValues}>
                            {t('picklistDependencies.noValuesForField')}
                          </p>
                        ) : (
                          <>
                            <div className={styles.bulkActions}>
                              <button
                                type="button"
                                className={styles.bulkButton}
                                onClick={() => handleSelectAll(field.id)}
                                disabled={isSaving || allSelected}
                                data-testid={`select-all-${field.id}`}
                              >
                                {t('common.selectAll')}
                              </button>
                              <button
                                type="button"
                                className={styles.bulkButton}
                                onClick={() => handleDeselectAll(field.id)}
                                disabled={isSaving || checkedCount === 0}
                                data-testid={`deselect-all-${field.id}`}
                              >
                                {t('common.deselectAll')}
                              </button>
                            </div>
                            <div className={styles.valuesList}>
                              {state.allValues.map((val) => (
                                <label
                                  key={val.value}
                                  className={styles.valueItem}
                                  data-testid={`value-item-${field.id}-${val.value}`}
                                >
                                  <input
                                    type="checkbox"
                                    checked={state.checkedValues.has(val.value)}
                                    onChange={() => handleToggleValue(field.id, val.value)}
                                    disabled={isSaving}
                                    data-testid={`value-checkbox-${field.id}-${val.value}`}
                                  />
                                  <span className={styles.valueLabel}>
                                    {val.label || val.value}
                                  </span>
                                  {val.color && (
                                    <span
                                      className={styles.valueColor}
                                      style={{ backgroundColor: val.color }}
                                      aria-label={val.color}
                                    />
                                  )}
                                </label>
                              ))}
                            </div>

                            <div className={styles.defaultValueGroup}>
                              <label
                                htmlFor={`default-value-${field.id}`}
                                className={styles.defaultValueLabel}
                              >
                                {t('recordTypes.defaultValue')}
                              </label>
                              <select
                                id={`default-value-${field.id}`}
                                className={styles.select}
                                value={state.defaultValue}
                                onChange={(e) => handleDefaultChange(field.id, e.target.value)}
                                disabled={isSaving}
                                data-testid={`default-select-${field.id}`}
                              >
                                <option value="">{t('recordTypes.noDefault')}</option>
                                {state.allValues
                                  .filter((v) => state.checkedValues.has(v.value))
                                  .map((v) => (
                                    <option key={v.value} value={v.value}>
                                      {v.label || v.value}
                                    </option>
                                  ))}
                              </select>
                            </div>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>

        <div className={styles.modalFooter}>
          <button
            type="button"
            className={styles.cancelButton}
            onClick={onClose}
            disabled={isSaving}
            data-testid="picklist-override-cancel"
          >
            {t('common.cancel')}
          </button>
          <button
            type="button"
            className={styles.saveButton}
            onClick={handleSave}
            disabled={isSaving || isLoading || picklistFields.length === 0}
            data-testid="picklist-override-save"
          >
            {isSaving ? (
              <>
                <LoadingSpinner size="small" />
                <span>{t('common.saving')}</span>
              </>
            ) : (
              t('common.save')
            )}
          </button>
        </div>
      </div>
    </div>
  )
}

export default RecordTypePicklistEditor
