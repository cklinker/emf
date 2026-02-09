import React, { useEffect, useCallback } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useI18n } from '../../context/I18nContext'
import { LoadingSpinner } from '../LoadingSpinner'
import type { RecordType } from '../../types/collections'
import styles from './RecordTypeEditor.module.css'

const recordTypeSchema = z.object({
  name: z.string().min(1, 'Name is required').max(100, 'Name must be 100 characters or less'),
  description: z
    .string()
    .max(500, 'Description must be 500 characters or less')
    .optional()
    .or(z.literal('')),
  isDefault: z.boolean(),
})

type RecordTypeFormData = z.infer<typeof recordTypeSchema>

export interface RecordTypeEditorProps {
  collectionId: string
  recordType?: RecordType
  onSave: (data: RecordTypeFormData) => Promise<void>
  onCancel: () => void
  isSubmitting?: boolean
}

export function RecordTypeEditor({
  recordType,
  onSave,
  onCancel,
  isSubmitting = false,
}: RecordTypeEditorProps): React.ReactElement {
  const { t } = useI18n()
  const isEditMode = !!recordType

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<RecordTypeFormData>({
    resolver: zodResolver(recordTypeSchema),
    defaultValues: {
      name: recordType?.name ?? '',
      description: recordType?.description ?? '',
      isDefault: recordType?.isDefault ?? false,
    },
    mode: 'onBlur',
  })

  useEffect(() => {
    if (recordType) {
      reset({
        name: recordType.name,
        description: recordType.description ?? '',
        isDefault: recordType.isDefault,
      })
    }
  }, [recordType, reset])

  const handleFormSubmit = useCallback(
    async (data: RecordTypeFormData) => {
      await onSave({
        name: data.name,
        description: data.description || undefined,
        isDefault: data.isDefault,
      })
    },
    [onSave]
  )

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(handleFormSubmit)}
      data-testid="record-type-editor"
      noValidate
    >
      <h3 className={styles.formTitle}>
        {isEditMode ? t('recordTypes.editRecordType') : t('recordTypes.addRecordType')}
      </h3>

      <div className={styles.fieldGroup}>
        <label htmlFor="rt-name" className={styles.label}>
          {t('common.name')}
          <span className={styles.required} aria-hidden="true">
            *
          </span>
        </label>
        <input
          id="rt-name"
          type="text"
          className={`${styles.input} ${errors.name ? styles.inputError : ''}`}
          placeholder="e.g., Standard"
          disabled={isEditMode || isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.name}
          data-testid="rt-name-input"
          {...register('name')}
        />
        {errors.name && (
          <span className={styles.errorMessage} role="alert">
            {errors.name.message}
          </span>
        )}
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="rt-description" className={styles.label}>
          {t('collections.description')}
          <span className={styles.optional}>({t('common.optional')})</span>
        </label>
        <textarea
          id="rt-description"
          className={`${styles.textarea} ${errors.description ? styles.inputError : ''}`}
          placeholder="Describe this record type..."
          disabled={isSubmitting}
          rows={3}
          data-testid="rt-description-input"
          {...register('description')}
        />
        {errors.description && (
          <span className={styles.errorMessage} role="alert">
            {errors.description.message}
          </span>
        )}
      </div>

      <div className={styles.checkboxGroup}>
        <input
          id="rt-is-default"
          type="checkbox"
          className={styles.checkbox}
          disabled={isSubmitting}
          data-testid="rt-is-default-checkbox"
          {...register('isDefault')}
        />
        <label htmlFor="rt-is-default" className={styles.checkboxLabel}>
          {t('recordTypes.isDefaultLabel')}
        </label>
      </div>
      <span className={styles.hint}>{t('recordTypes.isDefaultHint')}</span>

      <div className={styles.actions}>
        <button
          type="button"
          className={styles.cancelButton}
          onClick={onCancel}
          disabled={isSubmitting}
          data-testid="rt-editor-cancel"
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          className={styles.submitButton}
          disabled={isSubmitting || (!isDirty && isEditMode)}
          data-testid="rt-editor-submit"
        >
          {isSubmitting ? (
            <>
              <LoadingSpinner size="small" />
              <span>{t('common.saving')}</span>
            </>
          ) : isEditMode ? (
            t('common.save')
          ) : (
            t('common.create')
          )}
        </button>
      </div>
    </form>
  )
}

export default RecordTypeEditor
