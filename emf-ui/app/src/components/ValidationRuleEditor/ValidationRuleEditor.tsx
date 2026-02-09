import React, { useEffect, useCallback } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useI18n } from '../../context/I18nContext'
import { LoadingSpinner } from '../LoadingSpinner'
import type { CollectionValidationRule } from '../../types/collections'
import styles from './ValidationRuleEditor.module.css'

const validationRuleSchema = z.object({
  name: z.string().min(1, 'Name is required').max(100, 'Name must be 100 characters or less'),
  description: z
    .string()
    .max(500, 'Description must be 500 characters or less')
    .optional()
    .or(z.literal('')),
  errorConditionFormula: z.string().min(1, 'Formula is required'),
  errorMessage: z
    .string()
    .min(1, 'Error message is required')
    .max(500, 'Error message must be 500 characters or less'),
  errorField: z
    .string()
    .max(100, 'Error field must be 100 characters or less')
    .optional()
    .or(z.literal('')),
  evaluateOn: z.enum(['CREATE', 'UPDATE', 'CREATE_AND_UPDATE']),
})

type ValidationRuleFormData = z.infer<typeof validationRuleSchema>

export interface ValidationRuleEditorProps {
  collectionId: string
  rule?: CollectionValidationRule
  onSave: (data: Omit<ValidationRuleFormData, 'id'>) => Promise<void>
  onCancel: () => void
  isSubmitting?: boolean
}

export function ValidationRuleEditor({
  rule,
  onSave,
  onCancel,
  isSubmitting = false,
}: ValidationRuleEditorProps): React.ReactElement {
  const { t } = useI18n()
  const isEditMode = !!rule

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ValidationRuleFormData>({
    resolver: zodResolver(validationRuleSchema),
    defaultValues: {
      name: rule?.name ?? '',
      description: rule?.description ?? '',
      errorConditionFormula: rule?.errorConditionFormula ?? '',
      errorMessage: rule?.errorMessage ?? '',
      errorField: rule?.errorField ?? '',
      evaluateOn: rule?.evaluateOn ?? 'CREATE_AND_UPDATE',
    },
    mode: 'onBlur',
  })

  useEffect(() => {
    if (rule) {
      reset({
        name: rule.name,
        description: rule.description ?? '',
        errorConditionFormula: rule.errorConditionFormula,
        errorMessage: rule.errorMessage,
        errorField: rule.errorField ?? '',
        evaluateOn: rule.evaluateOn,
      })
    }
  }, [rule, reset])

  const handleFormSubmit = useCallback(
    async (data: ValidationRuleFormData) => {
      await onSave({
        name: data.name,
        description: data.description || undefined,
        errorConditionFormula: data.errorConditionFormula,
        errorMessage: data.errorMessage,
        errorField: data.errorField || undefined,
        evaluateOn: data.evaluateOn,
      })
    },
    [onSave]
  )

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(handleFormSubmit)}
      data-testid="validation-rule-editor"
      noValidate
    >
      <h3 className={styles.formTitle}>
        {isEditMode ? t('validationRules.editRule') : t('validationRules.addRule')}
      </h3>

      <div className={styles.fieldGroup}>
        <label htmlFor="rule-name" className={styles.label}>
          {t('common.name')}
          <span className={styles.required} aria-hidden="true">
            *
          </span>
        </label>
        <input
          id="rule-name"
          type="text"
          className={`${styles.input} ${errors.name ? styles.inputError : ''}`}
          placeholder="e.g., require_amount_positive"
          disabled={isEditMode || isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.name}
          data-testid="rule-name-input"
          {...register('name')}
        />
        {errors.name && (
          <span className={styles.errorMessage} role="alert">
            {errors.name.message}
          </span>
        )}
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="rule-description" className={styles.label}>
          {t('collections.description')}
          <span className={styles.optional}>({t('common.optional')})</span>
        </label>
        <textarea
          id="rule-description"
          className={`${styles.textarea} ${errors.description ? styles.inputError : ''}`}
          placeholder="Describe what this validation rule checks..."
          disabled={isSubmitting}
          rows={2}
          data-testid="rule-description-input"
          {...register('description')}
        />
        {errors.description && (
          <span className={styles.errorMessage} role="alert">
            {errors.description.message}
          </span>
        )}
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="rule-formula" className={styles.label}>
          {t('validationRules.formula')}
          <span className={styles.required} aria-hidden="true">
            *
          </span>
        </label>
        <textarea
          id="rule-formula"
          className={`${styles.textarea} ${styles.formulaInput} ${errors.errorConditionFormula ? styles.inputError : ''}`}
          placeholder={t('validationRules.formulaPlaceholder')}
          disabled={isSubmitting}
          rows={4}
          aria-required="true"
          aria-invalid={!!errors.errorConditionFormula}
          data-testid="rule-formula-input"
          {...register('errorConditionFormula')}
        />
        {errors.errorConditionFormula && (
          <span className={styles.errorMessage} role="alert">
            {errors.errorConditionFormula.message}
          </span>
        )}
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="rule-error-message" className={styles.label}>
          {t('validationRules.errorMessage')}
          <span className={styles.required} aria-hidden="true">
            *
          </span>
        </label>
        <input
          id="rule-error-message"
          type="text"
          className={`${styles.input} ${errors.errorMessage ? styles.inputError : ''}`}
          placeholder={t('validationRules.errorMessagePlaceholder')}
          disabled={isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.errorMessage}
          data-testid="rule-error-message-input"
          {...register('errorMessage')}
        />
        {errors.errorMessage && (
          <span className={styles.errorMessage} role="alert">
            {errors.errorMessage.message}
          </span>
        )}
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="rule-error-field" className={styles.label}>
          {t('validationRules.errorFieldLabel')}
          <span className={styles.optional}>({t('common.optional')})</span>
        </label>
        <input
          id="rule-error-field"
          type="text"
          className={`${styles.input} ${errors.errorField ? styles.inputError : ''}`}
          placeholder="e.g., amount"
          disabled={isSubmitting}
          aria-describedby="error-field-hint"
          data-testid="rule-error-field-input"
          {...register('errorField')}
        />
        <span id="error-field-hint" className={styles.hint}>
          {t('validationRules.errorFieldHint')}
        </span>
        {errors.errorField && (
          <span className={styles.errorMessage} role="alert">
            {errors.errorField.message}
          </span>
        )}
      </div>

      <div className={styles.fieldGroup}>
        <label htmlFor="rule-evaluate-on" className={styles.label}>
          {t('validationRules.evaluateOn')}
          <span className={styles.required} aria-hidden="true">
            *
          </span>
        </label>
        <select
          id="rule-evaluate-on"
          className={styles.select}
          disabled={isSubmitting}
          data-testid="rule-evaluate-on-select"
          {...register('evaluateOn')}
        >
          <option value="CREATE">{t('validationRules.evaluateOnCreate')}</option>
          <option value="UPDATE">{t('validationRules.evaluateOnUpdate')}</option>
          <option value="CREATE_AND_UPDATE">{t('validationRules.evaluateOnBoth')}</option>
        </select>
      </div>

      <div className={styles.actions}>
        <button
          type="button"
          className={styles.cancelButton}
          onClick={onCancel}
          disabled={isSubmitting}
          data-testid="rule-editor-cancel"
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          className={styles.submitButton}
          disabled={isSubmitting || (!isDirty && isEditMode)}
          data-testid="rule-editor-submit"
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

export default ValidationRuleEditor
