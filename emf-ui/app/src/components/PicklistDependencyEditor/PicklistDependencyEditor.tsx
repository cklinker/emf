import React, { useState, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import { LoadingSpinner } from '../LoadingSpinner'
import type { FieldDefinition, PicklistValue, PicklistDependency } from '../../types/collections'
import styles from './PicklistDependencyEditor.module.css'

export interface PicklistDependencyEditorProps {
  collectionId: string
  fields: FieldDefinition[]
  dependency?: PicklistDependency
  onSave: (data: {
    controllingFieldId: string
    dependentFieldId: string
    mapping: Record<string, string[]>
  }) => Promise<void>
  onCancel: () => void
  isSubmitting: boolean
}

export function PicklistDependencyEditor({
  fields,
  dependency,
  onSave,
  onCancel,
  isSubmitting,
}: PicklistDependencyEditorProps): React.ReactElement {
  const { t } = useI18n()
  const { apiClient } = useApi()
  const isEditMode = !!dependency

  const picklistFields = fields.filter((f) => f.type === 'picklist' || f.type === 'multi_picklist')

  const [controllingFieldId, setControllingFieldId] = useState(dependency?.controllingFieldId ?? '')
  const [dependentFieldId, setDependentFieldId] = useState(dependency?.dependentFieldId ?? '')
  const [mapping, setMapping] = useState<Record<string, string[]>>(dependency?.mapping ?? {})

  const { data: controllingValues, isLoading: loadingControlling } = useQuery({
    queryKey: ['field-picklist-values', controllingFieldId],
    queryFn: () =>
      apiClient.get<PicklistValue[]>(`/control/picklists/fields/${controllingFieldId}/values`),
    enabled: !!controllingFieldId,
  })

  const { data: dependentValues, isLoading: loadingDependent } = useQuery({
    queryKey: ['field-picklist-values', dependentFieldId],
    queryFn: () =>
      apiClient.get<PicklistValue[]>(`/control/picklists/fields/${dependentFieldId}/values`),
    enabled: !!dependentFieldId,
  })

  const handleControllingFieldChange = useCallback(
    (newId: string) => {
      setControllingFieldId(newId)
      if (!isEditMode) setMapping({})
    },
    [isEditMode]
  )

  const handleDependentFieldChange = useCallback(
    (newId: string) => {
      setDependentFieldId(newId)
      if (!isEditMode) setMapping({})
    },
    [isEditMode]
  )

  const handleMappingToggle = useCallback((controllingValue: string, dependentValue: string) => {
    setMapping((prev) => {
      const current = prev[controllingValue] ?? []
      const exists = current.includes(dependentValue)
      return {
        ...prev,
        [controllingValue]: exists
          ? current.filter((v) => v !== dependentValue)
          : [...current, dependentValue],
      }
    })
  }, [])

  const handleSave = useCallback(async () => {
    if (!controllingFieldId || !dependentFieldId) return
    await onSave({ controllingFieldId, dependentFieldId, mapping })
  }, [controllingFieldId, dependentFieldId, mapping, onSave])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
      }
    },
    [onCancel]
  )

  const controllingFieldName = picklistFields.find((f) => f.id === controllingFieldId)?.name ?? ''
  const dependentFieldName = picklistFields.find((f) => f.id === dependentFieldId)?.name ?? ''

  const availableDependentFields = picklistFields.filter((f) => f.id !== controllingFieldId)

  const canSave =
    !!controllingFieldId &&
    !!dependentFieldId &&
    !isSubmitting &&
    !loadingControlling &&
    !loadingDependent

  const showMappingMatrix =
    controllingFieldId &&
    dependentFieldId &&
    controllingValues &&
    controllingValues.length > 0 &&
    dependentValues &&
    dependentValues.length > 0

  return (
    <div
      className={styles.modalOverlay}
      onMouseDown={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      role="presentation"
      data-testid="dependency-editor-overlay"
    >
      {/* eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions */}
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="dependency-editor-title"
        onMouseDown={(e) => e.stopPropagation()}
        data-testid="dependency-editor-modal"
      >
        <div className={styles.modalHeader}>
          <h2 id="dependency-editor-title" className={styles.modalTitle}>
            {isEditMode
              ? t('picklistDependencies.editDependency')
              : t('picklistDependencies.addDependency')}
          </h2>
          <button
            type="button"
            className={styles.modalCloseButton}
            onClick={onCancel}
            aria-label={t('common.close')}
            data-testid="dependency-editor-close"
          >
            &times;
          </button>
        </div>

        <div className={styles.modalBody}>
          {picklistFields.length < 2 ? (
            <div className={styles.emptyState}>
              <p>
                {picklistFields.length === 0
                  ? t('picklistDependencies.noPicklistFields')
                  : t('picklistDependencies.needTwoPicklistFields')}
              </p>
            </div>
          ) : (
            <>
              <div className={styles.fieldSelectors}>
                <div className={styles.fieldGroup}>
                  <label htmlFor="controlling-field" className={styles.fieldLabel}>
                    {t('picklistDependencies.controllingField')}
                    <span className={styles.required} aria-hidden="true">
                      *
                    </span>
                  </label>
                  <select
                    id="controlling-field"
                    className={styles.select}
                    value={controllingFieldId}
                    onChange={(e) => handleControllingFieldChange(e.target.value)}
                    disabled={isEditMode || isSubmitting}
                    data-testid="controlling-field-select"
                  >
                    <option value="">{t('picklistDependencies.selectControllingField')}</option>
                    {picklistFields.map((f) => (
                      <option key={f.id} value={f.id}>
                        {f.displayName || f.name} ({f.type})
                      </option>
                    ))}
                  </select>
                </div>

                <div className={styles.fieldGroup}>
                  <label htmlFor="dependent-field" className={styles.fieldLabel}>
                    {t('picklistDependencies.dependentField')}
                    <span className={styles.required} aria-hidden="true">
                      *
                    </span>
                  </label>
                  <select
                    id="dependent-field"
                    className={styles.select}
                    value={dependentFieldId}
                    onChange={(e) => handleDependentFieldChange(e.target.value)}
                    disabled={isEditMode || isSubmitting || !controllingFieldId}
                    data-testid="dependent-field-select"
                  >
                    <option value="">{t('picklistDependencies.selectDependentField')}</option>
                    {availableDependentFields.map((f) => (
                      <option key={f.id} value={f.id}>
                        {f.displayName || f.name} ({f.type})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {(loadingControlling || loadingDependent) && (
                <div className={styles.loadingContainer}>
                  <LoadingSpinner size="medium" label={t('common.loading')} />
                </div>
              )}

              {controllingFieldId &&
                dependentFieldId &&
                !loadingControlling &&
                !loadingDependent &&
                (!controllingValues || controllingValues.length === 0) && (
                  <div className={styles.emptyState}>
                    <p>
                      {t('picklistDependencies.noValuesForField')} ({controllingFieldName})
                    </p>
                  </div>
                )}

              {controllingFieldId &&
                dependentFieldId &&
                !loadingControlling &&
                !loadingDependent &&
                controllingValues &&
                controllingValues.length > 0 &&
                (!dependentValues || dependentValues.length === 0) && (
                  <div className={styles.emptyState}>
                    <p>
                      {t('picklistDependencies.noValuesForField')} ({dependentFieldName})
                    </p>
                  </div>
                )}

              {showMappingMatrix && (
                <div className={styles.mappingSection}>
                  <h3 className={styles.mappingSectionTitle}>
                    {t('picklistDependencies.mapping')}
                  </h3>
                  <p className={styles.mappingHint}>{t('picklistDependencies.mappingHint')}</p>
                  <table className={styles.mappingTable} data-testid="mapping-matrix">
                    <thead>
                      <tr>
                        <th>{controllingFieldName}</th>
                        {dependentValues!.map((dv) => (
                          <th key={dv.value}>{dv.label || dv.value}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {controllingValues!.map((cv) => (
                        <tr key={cv.value}>
                          <td>{cv.label || cv.value}</td>
                          {dependentValues!.map((dv) => (
                            <td key={dv.value}>
                              <input
                                type="checkbox"
                                checked={mapping[cv.value]?.includes(dv.value) ?? false}
                                onChange={() => handleMappingToggle(cv.value, dv.value)}
                                disabled={isSubmitting}
                                aria-label={`${cv.label || cv.value} → ${dv.label || dv.value}`}
                                data-testid={`mapping-${cv.value}-${dv.value}`}
                              />
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </div>

        <div className={styles.modalFooter}>
          <button
            type="button"
            className={styles.cancelButton}
            onClick={onCancel}
            disabled={isSubmitting}
            data-testid="dependency-editor-cancel"
          >
            {t('common.cancel')}
          </button>
          <button
            type="button"
            className={styles.saveButton}
            onClick={handleSave}
            disabled={!canSave}
            data-testid="dependency-editor-save"
          >
            {isSubmitting ? (
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

export default PicklistDependencyEditor
