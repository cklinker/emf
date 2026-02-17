import React, { useEffect, useCallback } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { cn } from '@/lib/utils'
import { useI18n } from '../../context/I18nContext'
import { LoadingSpinner } from '../LoadingSpinner'
import type { CollectionValidationRule } from '../../types/collections'

const inputClasses =
  'px-3 py-2 text-base leading-relaxed text-foreground bg-background border border-border rounded-md transition-[border-color,box-shadow] focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-ring disabled:bg-muted disabled:text-muted-foreground disabled:cursor-not-allowed placeholder:text-muted-foreground'

const inputErrorClasses = 'border-destructive focus:border-destructive focus:ring-destructive/25'

const selectClasses =
  "appearance-none bg-[url(\"data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%236b7280' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e\")] bg-[right_0.5rem_center] bg-no-repeat bg-[length:1.5em_1.5em] pr-10 cursor-pointer"

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
      className="flex flex-col gap-6 max-w-[600px] w-full"
      onSubmit={handleSubmit(handleFormSubmit)}
      data-testid="validation-rule-editor"
      noValidate
    >
      <h3 className="mb-4 text-xl font-semibold text-foreground">
        {isEditMode ? t('validationRules.editRule') : t('validationRules.addRule')}
      </h3>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="rule-name"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('common.name')}
          <span className="text-destructive font-semibold" aria-hidden="true">
            *
          </span>
        </label>
        <input
          id="rule-name"
          type="text"
          className={cn(inputClasses, errors.name && inputErrorClasses)}
          placeholder="e.g., require_amount_positive"
          disabled={isEditMode || isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.name}
          data-testid="rule-name-input"
          {...register('name')}
        />
        {errors.name && (
          <span className="flex items-center gap-1 text-sm text-destructive mt-1" role="alert">
            {errors.name.message}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="rule-description"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('collections.description')}
          <span className="text-xs font-normal text-muted-foreground ml-1">
            ({t('common.optional')})
          </span>
        </label>
        <textarea
          id="rule-description"
          className={cn(inputClasses, errors.description && inputErrorClasses)}
          placeholder="Describe what this validation rule checks..."
          disabled={isSubmitting}
          rows={2}
          data-testid="rule-description-input"
          {...register('description')}
        />
        {errors.description && (
          <span className="flex items-center gap-1 text-sm text-destructive mt-1" role="alert">
            {errors.description.message}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="rule-formula"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('validationRules.formula')}
          <span className="text-destructive font-semibold" aria-hidden="true">
            *
          </span>
        </label>
        <textarea
          id="rule-formula"
          className={cn(
            inputClasses,
            'font-mono text-sm',
            errors.errorConditionFormula && inputErrorClasses
          )}
          placeholder={t('validationRules.formulaPlaceholder')}
          disabled={isSubmitting}
          rows={4}
          aria-required="true"
          aria-invalid={!!errors.errorConditionFormula}
          data-testid="rule-formula-input"
          {...register('errorConditionFormula')}
        />
        {errors.errorConditionFormula && (
          <span className="flex items-center gap-1 text-sm text-destructive mt-1" role="alert">
            {errors.errorConditionFormula.message}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="rule-error-message"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('validationRules.errorMessage')}
          <span className="text-destructive font-semibold" aria-hidden="true">
            *
          </span>
        </label>
        <input
          id="rule-error-message"
          type="text"
          className={cn(inputClasses, errors.errorMessage && inputErrorClasses)}
          placeholder={t('validationRules.errorMessagePlaceholder')}
          disabled={isSubmitting}
          aria-required="true"
          aria-invalid={!!errors.errorMessage}
          data-testid="rule-error-message-input"
          {...register('errorMessage')}
        />
        {errors.errorMessage && (
          <span className="flex items-center gap-1 text-sm text-destructive mt-1" role="alert">
            {errors.errorMessage.message}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="rule-error-field"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('validationRules.errorFieldLabel')}
          <span className="text-xs font-normal text-muted-foreground ml-1">
            ({t('common.optional')})
          </span>
        </label>
        <input
          id="rule-error-field"
          type="text"
          className={cn(inputClasses, errors.errorField && inputErrorClasses)}
          placeholder="e.g., amount"
          disabled={isSubmitting}
          aria-describedby="error-field-hint"
          data-testid="rule-error-field-input"
          {...register('errorField')}
        />
        <span id="error-field-hint" className="text-xs text-muted-foreground mt-1">
          {t('validationRules.errorFieldHint')}
        </span>
        {errors.errorField && (
          <span className="flex items-center gap-1 text-sm text-destructive mt-1" role="alert">
            {errors.errorField.message}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="rule-evaluate-on"
          className="flex items-center gap-1 text-sm font-medium text-foreground"
        >
          {t('validationRules.evaluateOn')}
          <span className="text-destructive font-semibold" aria-hidden="true">
            *
          </span>
        </label>
        <select
          id="rule-evaluate-on"
          className={cn(inputClasses, selectClasses)}
          disabled={isSubmitting}
          data-testid="rule-evaluate-on-select"
          {...register('evaluateOn')}
        >
          <option value="CREATE">{t('validationRules.evaluateOnCreate')}</option>
          <option value="UPDATE">{t('validationRules.evaluateOnUpdate')}</option>
          <option value="CREATE_AND_UPDATE">{t('validationRules.evaluateOnBoth')}</option>
        </select>
      </div>

      <div className="flex justify-end gap-3 mt-6 pt-6 border-t border-border">
        <button
          type="button"
          className="inline-flex items-center justify-center gap-2 px-6 py-2 text-base font-medium leading-relaxed rounded-md cursor-pointer text-foreground bg-muted border border-border hover:bg-accent disabled:opacity-50 disabled:cursor-not-allowed"
          onClick={onCancel}
          disabled={isSubmitting}
          data-testid="rule-editor-cancel"
        >
          {t('common.cancel')}
        </button>
        <button
          type="submit"
          className="inline-flex items-center justify-center gap-2 px-6 py-2 text-base font-medium leading-relaxed rounded-md cursor-pointer text-primary-foreground bg-primary border border-transparent hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
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
