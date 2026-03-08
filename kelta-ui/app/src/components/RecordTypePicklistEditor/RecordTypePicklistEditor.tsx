import React, { useState, useCallback, useMemo, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { cn } from '@/lib/utils'
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

const selectClasses = cn(
  'px-2 py-2 text-[0.8125rem] text-foreground bg-background border border-input rounded-md',
  'transition-colors duration-150 motion-reduce:transition-none',
  'focus:outline-none focus:border-primary focus:ring-2 focus:ring-ring/10',
  'disabled:bg-muted disabled:text-muted-foreground disabled:cursor-not-allowed',
  'flex-1'
)

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
      apiClient.getList<RecordTypePicklistOverride>(
        `/api/record-type-picklist-values?filter[recordTypeId][eq]=${recordType.id}`
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
            const values = await apiClient.getList<PicklistValue>(
              `/api/picklist-values?filter[picklistSourceId][eq]=${field.id}&filter[picklistSourceType][eq]=FIELD`
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
              apiClient.deleteResource(
                `/api/record-type-picklist-values?filter[recordTypeId][eq]=${recordType.id}&filter[fieldId][eq]=${fieldId}`
              )
            )
          }
        } else if (state.checkedValues.size > 0) {
          // Some values selected (or has a default value) — set/update override
          operations.push(
            apiClient.putResource(
              `/api/record-type-picklist-values?filter[recordTypeId][eq]=${recordType.id}&filter[fieldId][eq]=${fieldId}`,
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
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-[1000] p-4"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
      onKeyDown={handleKeyDown}
      role="presentation"
      data-testid="picklist-override-overlay"
    >
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions */}
      <div
        className="bg-background dark:bg-card rounded-lg shadow-xl max-w-[700px] w-full max-h-[90vh] flex flex-col"
        role="dialog"
        aria-modal="true"
        aria-labelledby="picklist-override-title"
        onMouseDown={(e) => e.stopPropagation()}
        data-testid="picklist-override-modal"
      >
        <div className="flex justify-between items-center p-6 border-b border-border shrink-0">
          <h2 id="picklist-override-title" className="text-xl font-semibold m-0 text-foreground">
            {t('recordTypes.picklistOverrides')} &mdash; {recordType.name}
          </h2>
          <button
            type="button"
            className="p-2 text-2xl leading-none text-muted-foreground bg-transparent border-none cursor-pointer rounded transition-colors hover:text-foreground hover:bg-muted"
            onClick={onClose}
            aria-label={t('common.close')}
            data-testid="picklist-override-close"
          >
            &times;
          </button>
        </div>

        <div className="p-6 overflow-y-auto flex-1">
          {isLoading ? (
            <div className="flex justify-center items-center py-12">
              <LoadingSpinner size="medium" label={t('common.loading')} />
            </div>
          ) : picklistFields.length === 0 ? (
            <div
              className="py-8 text-center text-muted-foreground text-sm"
              data-testid="no-picklist-fields"
            >
              <p>{t('recordTypes.noPicklistFields')}</p>
            </div>
          ) : (
            <div className="flex flex-col gap-3">
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
                    className="border border-border rounded-md overflow-hidden"
                    data-testid={`field-section-${field.id}`}
                  >
                    <button
                      type="button"
                      className="flex items-center gap-3 w-full px-4 py-3 bg-muted border-none cursor-pointer text-left text-sm transition-colors duration-150 motion-reduce:transition-none hover:bg-accent"
                      onClick={() => handleToggleCollapse(field.id)}
                      aria-expanded={!isCollapsed}
                      data-testid={`field-header-${field.id}`}
                    >
                      <span className="text-[0.625rem] text-muted-foreground w-3 shrink-0">
                        {isCollapsed ? '\u25B6' : '\u25BC'}
                      </span>
                      <span className="font-semibold text-foreground flex-1">
                        {state.fieldName}
                      </span>
                      <span className="text-xs text-muted-foreground font-normal">
                        {allSelected
                          ? t('recordTypes.allValuesAvailable')
                          : t('recordTypes.restrictedValues', {
                              count: checkedCount,
                              total: totalCount,
                            })}
                      </span>
                    </button>

                    {!isCollapsed && (
                      <div className="p-4 border-t border-border">
                        {state.allValues.length === 0 ? (
                          <p className="text-sm text-muted-foreground text-center p-4">
                            {t('picklistDependencies.noValuesForField')}
                          </p>
                        ) : (
                          <>
                            <div className="flex gap-2 mb-3">
                              <button
                                type="button"
                                className="px-2.5 py-1 text-xs font-medium text-primary bg-transparent border border-primary rounded cursor-pointer transition-colors duration-150 motion-reduce:transition-none hover:bg-primary hover:text-primary-foreground disabled:opacity-40 disabled:cursor-not-allowed"
                                onClick={() => handleSelectAll(field.id)}
                                disabled={isSaving || allSelected}
                                data-testid={`select-all-${field.id}`}
                              >
                                {t('common.selectAll')}
                              </button>
                              <button
                                type="button"
                                className="px-2.5 py-1 text-xs font-medium text-primary bg-transparent border border-primary rounded cursor-pointer transition-colors duration-150 motion-reduce:transition-none hover:bg-primary hover:text-primary-foreground disabled:opacity-40 disabled:cursor-not-allowed"
                                onClick={() => handleDeselectAll(field.id)}
                                disabled={isSaving || checkedCount === 0}
                                data-testid={`deselect-all-${field.id}`}
                              >
                                {t('common.deselectAll')}
                              </button>
                            </div>
                            <div className="flex flex-col gap-1.5 mb-4 max-h-60 overflow-y-auto py-1">
                              {state.allValues.map((val) => (
                                <label
                                  key={val.value}
                                  className="flex items-center gap-2 px-2 py-1.5 rounded cursor-pointer text-sm transition-colors duration-100 motion-reduce:transition-none hover:bg-muted"
                                  data-testid={`value-item-${field.id}-${val.value}`}
                                >
                                  <input
                                    type="checkbox"
                                    className="w-4 h-4 accent-primary cursor-pointer shrink-0"
                                    checked={state.checkedValues.has(val.value)}
                                    onChange={() => handleToggleValue(field.id, val.value)}
                                    disabled={isSaving}
                                    data-testid={`value-checkbox-${field.id}-${val.value}`}
                                  />
                                  <span className="text-foreground flex-1">
                                    {val.label || val.value}
                                  </span>
                                  {val.color && (
                                    <span
                                      className="w-4 h-4 rounded-full border border-border shrink-0"
                                      style={{ backgroundColor: val.color }}
                                      aria-label={val.color}
                                    />
                                  )}
                                </label>
                              ))}
                            </div>

                            <div className="flex items-center gap-3 pt-3 border-t border-border max-md:flex-col max-md:items-start">
                              <label
                                htmlFor={`default-value-${field.id}`}
                                className="text-[0.8125rem] font-medium text-foreground whitespace-nowrap"
                              >
                                {t('recordTypes.defaultValue')}
                              </label>
                              <select
                                id={`default-value-${field.id}`}
                                className={cn(selectClasses, 'max-md:w-full')}
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

        <div className="flex gap-3 justify-end px-6 py-4 border-t border-border shrink-0">
          <button
            type="button"
            className="px-5 py-2.5 text-sm font-medium text-foreground bg-background border border-input rounded-md cursor-pointer transition-colors hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed"
            onClick={onClose}
            disabled={isSaving}
            data-testid="picklist-override-cancel"
          >
            {t('common.cancel')}
          </button>
          <button
            type="button"
            className="inline-flex items-center gap-2 px-5 py-2.5 text-sm font-medium text-primary-foreground bg-primary border-none rounded-md cursor-pointer transition-colors hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
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
