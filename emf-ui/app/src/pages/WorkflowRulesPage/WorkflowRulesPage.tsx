import React, { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useI18n } from '../../context/I18nContext'
import { useApi } from '../../context/ApiContext'
import {
  useToast,
  ConfirmDialog,
  LoadingSpinner,
  ErrorMessage,
  ExecutionLogModal,
} from '../../components'
import type { LogColumn } from '../../components'

import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

interface WorkflowActionType {
  id: string
  key: string
  name: string
  description: string | null
  category: string
  configSchema: string | null
  icon: string | null
  active: boolean
  builtIn: boolean
  handlerAvailable: boolean
}

interface ActionFormData {
  actionType: string
  executionOrder: number
  config: string
  active: boolean
  retryCount: number
  retryDelaySeconds: number
  retryBackoff: string
}

interface ActionDto {
  id: string
  actionType: string
  executionOrder: number
  config: string
  active: boolean
  retryCount: number
  retryDelaySeconds: number
  retryBackoff: string
}

type TriggerType =
  | 'ON_CREATE'
  | 'ON_UPDATE'
  | 'ON_CREATE_OR_UPDATE'
  | 'ON_DELETE'
  | 'SCHEDULED'
  | 'MANUAL'
  | 'BEFORE_CREATE'
  | 'BEFORE_UPDATE'

const TRIGGER_TYPE_LABELS: Record<TriggerType, string> = {
  ON_CREATE: 'On Create',
  ON_UPDATE: 'On Update',
  ON_CREATE_OR_UPDATE: 'On Create or Update',
  ON_DELETE: 'On Delete',
  SCHEDULED: 'Scheduled',
  MANUAL: 'Manual',
  BEFORE_CREATE: 'Before Create',
  BEFORE_UPDATE: 'Before Update',
}

/** Trigger types that support trigger field filtering */
const TRIGGER_FIELDS_TYPES: TriggerType[] = ['ON_UPDATE', 'ON_CREATE_OR_UPDATE', 'BEFORE_UPDATE']

/** Trigger types that run synchronously (before save) */
const BEFORE_SAVE_TYPES: TriggerType[] = ['BEFORE_CREATE', 'BEFORE_UPDATE']

/** Action type categories for grouping in the dropdown */
const ACTION_TYPE_CATEGORY_LABELS: Record<string, string> = {
  DATA: 'Data',
  COMMUNICATION: 'Communication',
  INTEGRATION: 'Integration',
  FLOW_CONTROL: 'Flow Control',
}

/** Action type category ordering */
const ACTION_TYPE_CATEGORY_ORDER = ['DATA', 'COMMUNICATION', 'INTEGRATION', 'FLOW_CONTROL']

interface WorkflowRule {
  id: string
  name: string
  description: string | null
  collectionId: string | null
  triggerType: TriggerType
  active: boolean
  filterFormula: string | null
  executionOrder: number
  errorHandling: string | null
  triggerFields: string[] | null
  cronExpression: string | null
  timezone: string | null
  lastScheduledRun: string | null
  executionMode: string | null
  actions: ActionDto[]
  createdAt: string
  updatedAt: string
}

interface WorkflowRuleFormData {
  name: string
  description: string
  collectionId: string
  triggerType: TriggerType
  active: boolean
  filterFormula: string
  executionOrder: number
  errorHandling: string
  triggerFields: string[]
  cronExpression: string
  timezone: string
  executionMode: string
  actions: ActionFormData[]
}

interface FormErrors {
  name?: string
  description?: string
  collectionId?: string
  executionOrder?: string
}

interface WorkflowExecutionLog {
  [key: string]: unknown
  id: string
  recordId: string
  triggerType: string
  status: string
  actionsExecuted: number
  errorMessage: string | null
  executedAt: string
  durationMs: number | null
}

interface WorkflowActionLog {
  id: string
  executionLogId: string
  actionId: string | null
  actionType: string
  status: string
  errorMessage: string | null
  inputSnapshot: string | null
  outputSnapshot: string | null
  durationMs: number | null
  executedAt: string
  attemptNumber: number
}

export interface WorkflowRulesPageProps {
  testId?: string
}

function validateForm(data: WorkflowRuleFormData): FormErrors {
  const errors: FormErrors = {}
  if (!data.name.trim()) {
    errors.name = 'Name is required'
  } else if (data.name.length > 100) {
    errors.name = 'Name must be 100 characters or fewer'
  }
  if (data.description && data.description.length > 500) {
    errors.description = 'Description must be 500 characters or fewer'
  }
  if (data.collectionId && data.collectionId.length > 100) {
    errors.collectionId = 'Collection ID must be 100 characters or fewer'
  }
  if (data.executionOrder < 0) {
    errors.executionOrder = 'Execution order must be 0 or greater'
  }
  return errors
}

interface WorkflowRuleFormProps {
  workflowRule?: WorkflowRule
  onSubmit: (data: WorkflowRuleFormData) => void
  onCancel: () => void
  isSubmitting: boolean
}

function WorkflowRuleForm({
  workflowRule,
  onSubmit,
  onCancel,
  isSubmitting,
}: WorkflowRuleFormProps): React.ReactElement {
  const { apiClient } = useApi()
  const isEditing = !!workflowRule
  const [formData, setFormData] = useState<WorkflowRuleFormData>({
    name: workflowRule?.name ?? '',
    description: workflowRule?.description ?? '',
    collectionId: workflowRule?.collectionId ?? '',
    triggerType: workflowRule?.triggerType ?? 'ON_CREATE',
    active: workflowRule?.active ?? true,
    filterFormula: workflowRule?.filterFormula ?? '',
    executionOrder: workflowRule?.executionOrder ?? 0,
    errorHandling: workflowRule?.errorHandling ?? 'STOP_ON_ERROR',
    triggerFields: workflowRule?.triggerFields ?? [],
    cronExpression: workflowRule?.cronExpression ?? '',
    timezone: workflowRule?.timezone ?? '',
    executionMode: workflowRule?.executionMode ?? 'SEQUENTIAL',
    actions:
      workflowRule?.actions?.map((a) => ({
        actionType: a.actionType,
        executionOrder: a.executionOrder,
        config: a.config ?? '{}',
        active: a.active,
        retryCount: a.retryCount ?? 0,
        retryDelaySeconds: a.retryDelaySeconds ?? 60,
        retryBackoff: a.retryBackoff ?? 'FIXED',
      })) ?? [],
  })
  const [triggerFieldInput, setTriggerFieldInput] = useState('')
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const nameInputRef = useRef<HTMLInputElement>(null)

  const { data: actionTypes } = useQuery({
    queryKey: ['workflow-action-types-active'],
    queryFn: () =>
      apiClient.get<WorkflowActionType[]>('/control/workflow-action-types?activeOnly=true'),
  })

  useEffect(() => {
    nameInputRef.current?.focus()
  }, [])

  const handleChange = useCallback(
    (field: keyof WorkflowRuleFormData, value: string | boolean | number) => {
      setFormData((prev) => ({ ...prev, [field]: value }))
      if (errors[field as keyof FormErrors]) {
        setErrors((prev) => ({ ...prev, [field]: undefined }))
      }
    },
    [errors]
  )

  const handleBlur = useCallback(
    (field: keyof FormErrors) => {
      setTouched((prev) => ({ ...prev, [field]: true }))
      const validationErrors = validateForm(formData)
      if (validationErrors[field]) {
        setErrors((prev) => ({ ...prev, [field]: validationErrors[field] }))
      }
    },
    [formData]
  )

  const handleAddAction = useCallback(() => {
    const defaultType = actionTypes && actionTypes.length > 0 ? actionTypes[0].key : 'FIELD_UPDATE'
    setFormData((prev) => ({
      ...prev,
      actions: [
        ...prev.actions,
        {
          actionType: defaultType,
          executionOrder: prev.actions.length,
          config: '{}',
          active: true,
          retryCount: 0,
          retryDelaySeconds: 60,
          retryBackoff: 'FIXED',
        },
      ],
    }))
  }, [actionTypes])

  const handleRemoveAction = useCallback((index: number) => {
    setFormData((prev) => ({
      ...prev,
      actions: prev.actions.filter((_, i) => i !== index),
    }))
  }, [])

  const handleActionChange = useCallback(
    (index: number, field: keyof ActionFormData, value: string | number | boolean) => {
      setFormData((prev) => ({
        ...prev,
        actions: prev.actions.map((action, i) =>
          i === index ? { ...action, [field]: value } : action
        ),
      }))
    },
    []
  )

  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault()
      const validationErrors = validateForm(formData)
      setErrors(validationErrors)
      setTouched({ name: true, description: true, collectionId: true, executionOrder: true })
      if (Object.keys(validationErrors).length === 0) {
        onSubmit(formData)
      }
    },
    [formData, onSubmit]
  )

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCancel()
      }
    },
    [onCancel]
  )

  const title = isEditing ? 'Edit Workflow Rule' : 'Create Workflow Rule'

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onCancel()}
      onKeyDown={handleKeyDown}
      data-testid="workflow-rule-form-overlay"
      role="presentation"
    >
      <div
        className="w-full max-w-[700px] max-h-[90vh] overflow-y-auto rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="workflow-rule-form-title"
        data-testid="workflow-rule-form-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <h2 id="workflow-rule-form-title" className="text-lg font-semibold text-foreground">
            {title}
          </h2>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            onClick={onCancel}
            aria-label="Close"
            data-testid="workflow-rule-form-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
            <div className="flex flex-col gap-2">
              <label htmlFor="workflow-rule-name" className="text-sm font-medium text-foreground">
                Name
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                ref={nameInputRef}
                id="workflow-rule-name"
                type="text"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.name && errors.name && 'border-destructive'
                )}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                onBlur={() => handleBlur('name')}
                placeholder="Enter workflow rule name"
                aria-required="true"
                aria-invalid={touched.name && !!errors.name}
                disabled={isSubmitting}
                data-testid="workflow-rule-name-input"
              />
              {touched.name && errors.name && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.name}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-description"
                className="text-sm font-medium text-foreground"
              >
                Description
              </label>
              <textarea
                id="workflow-rule-description"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.description && errors.description && 'border-destructive'
                )}
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                onBlur={() => handleBlur('description')}
                placeholder="Enter workflow rule description"
                disabled={isSubmitting}
                rows={3}
                data-testid="workflow-rule-description-input"
              />
              {touched.description && errors.description && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.description}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-collection-id"
                className="text-sm font-medium text-foreground"
              >
                Collection ID
              </label>
              <input
                id="workflow-rule-collection-id"
                type="text"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.collectionId && errors.collectionId && 'border-destructive'
                )}
                value={formData.collectionId}
                onChange={(e) => handleChange('collectionId', e.target.value)}
                onBlur={() => handleBlur('collectionId')}
                placeholder="Enter collection ID"
                disabled={isSubmitting}
                data-testid="workflow-rule-collection-id-input"
              />
              {touched.collectionId && errors.collectionId && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.collectionId}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-trigger-type"
                className="text-sm font-medium text-foreground"
              >
                Trigger Type
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <select
                id="workflow-rule-trigger-type"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                )}
                value={formData.triggerType}
                onChange={(e) => {
                  const newType = e.target.value as TriggerType
                  handleChange('triggerType', newType)
                  // Clear trigger fields when switching to a type that doesn't support them
                  if (!TRIGGER_FIELDS_TYPES.includes(newType)) {
                    setFormData((prev) => ({ ...prev, triggerFields: [] }))
                  }
                  // Clear cron when switching away from SCHEDULED
                  if (newType !== 'SCHEDULED') {
                    setFormData((prev) => ({ ...prev, cronExpression: '', timezone: '' }))
                  }
                }}
                disabled={isSubmitting}
                data-testid="workflow-rule-trigger-type-input"
              >
                {(Object.keys(TRIGGER_TYPE_LABELS) as TriggerType[]).map((type) => (
                  <option key={type} value={type}>
                    {TRIGGER_TYPE_LABELS[type]}
                  </option>
                ))}
              </select>
              {BEFORE_SAVE_TYPES.includes(formData.triggerType) && (
                <p className="mt-1 text-xs text-amber-600 dark:text-amber-400">
                  Before-save triggers run synchronously and only support Field Update actions.
                </p>
              )}
            </div>

            {/* Trigger Fields — shown for ON_UPDATE, ON_CREATE_OR_UPDATE, BEFORE_UPDATE */}
            {TRIGGER_FIELDS_TYPES.includes(formData.triggerType) && (
              <div className="flex flex-col gap-2">
                <label
                  htmlFor="trigger-field-input"
                  className="text-sm font-medium text-foreground"
                >
                  Trigger Fields
                  <span className="ml-2 text-xs font-normal text-muted-foreground">
                    (only fire when these fields change)
                  </span>
                </label>
                <div className="flex flex-wrap gap-1.5">
                  {formData.triggerFields.map((field) => (
                    <span
                      key={field}
                      className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary"
                    >
                      {field}
                      <button
                        type="button"
                        className="ml-0.5 text-primary/60 hover:text-primary"
                        onClick={() =>
                          setFormData((prev) => ({
                            ...prev,
                            triggerFields: prev.triggerFields.filter((f) => f !== field),
                          }))
                        }
                        aria-label={`Remove ${field}`}
                        disabled={isSubmitting}
                      >
                        &times;
                      </button>
                    </span>
                  ))}
                </div>
                <div className="flex gap-2">
                  <input
                    type="text"
                    className={cn(
                      'flex-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground transition-colors',
                      'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20'
                    )}
                    value={triggerFieldInput}
                    onChange={(e) => setTriggerFieldInput(e.target.value)}
                    onKeyDown={(e) => {
                      if ((e.key === 'Enter' || e.key === ',') && triggerFieldInput.trim()) {
                        e.preventDefault()
                        const field = triggerFieldInput.trim().replace(/,/g, '')
                        if (field && !formData.triggerFields.includes(field)) {
                          setFormData((prev) => ({
                            ...prev,
                            triggerFields: [...prev.triggerFields, field],
                          }))
                        }
                        setTriggerFieldInput('')
                      }
                    }}
                    placeholder="Type field name and press Enter"
                    disabled={isSubmitting}
                    id="trigger-field-input"
                    data-testid="trigger-field-input"
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      const field = triggerFieldInput.trim().replace(/,/g, '')
                      if (field && !formData.triggerFields.includes(field)) {
                        setFormData((prev) => ({
                          ...prev,
                          triggerFields: [...prev.triggerFields, field],
                        }))
                      }
                      setTriggerFieldInput('')
                    }}
                    disabled={isSubmitting || !triggerFieldInput.trim()}
                    data-testid="add-trigger-field-button"
                  >
                    Add
                  </Button>
                </div>
                {formData.triggerFields.length === 0 && (
                  <p className="text-xs text-muted-foreground">
                    No trigger fields — rule fires on any field change.
                  </p>
                )}
              </div>
            )}

            {/* Cron Expression & Timezone — shown for SCHEDULED */}
            {formData.triggerType === 'SCHEDULED' && (
              <>
                <div className="flex flex-col gap-2">
                  <label
                    htmlFor="workflow-rule-cron"
                    className="text-sm font-medium text-foreground"
                  >
                    Cron Expression
                    <span className="ml-1 text-destructive" aria-hidden="true">
                      *
                    </span>
                  </label>
                  <input
                    id="workflow-rule-cron"
                    type="text"
                    className={cn(
                      'rounded-md border border-border bg-background px-3 py-2.5 font-mono text-sm text-foreground transition-colors',
                      'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                      'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                    )}
                    value={formData.cronExpression}
                    onChange={(e) => handleChange('cronExpression', e.target.value)}
                    placeholder="0 0 * * * * (sec min hour day month weekday)"
                    disabled={isSubmitting}
                    data-testid="workflow-rule-cron-input"
                  />
                  <p className="text-xs text-muted-foreground">
                    Spring 6-field cron: seconds minutes hours day-of-month month day-of-week
                  </p>
                </div>

                <div className="flex flex-col gap-2">
                  <label
                    htmlFor="workflow-rule-timezone"
                    className="text-sm font-medium text-foreground"
                  >
                    Timezone
                  </label>
                  <input
                    id="workflow-rule-timezone"
                    type="text"
                    className={cn(
                      'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                      'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                      'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                    )}
                    value={formData.timezone}
                    onChange={(e) => handleChange('timezone', e.target.value)}
                    placeholder="UTC (default) or America/Chicago, etc."
                    disabled={isSubmitting}
                    data-testid="workflow-rule-timezone-input"
                  />
                </div>
              </>
            )}

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-error-handling"
                className="text-sm font-medium text-foreground"
              >
                Error Handling
              </label>
              <select
                id="workflow-rule-error-handling"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                )}
                value={formData.errorHandling}
                onChange={(e) => handleChange('errorHandling', e.target.value)}
                disabled={isSubmitting}
                data-testid="workflow-rule-error-handling-input"
              >
                <option value="STOP_ON_ERROR">Stop on Error</option>
                <option value="CONTINUE_ON_ERROR">Continue on Error</option>
              </select>
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-execution-mode"
                className="text-sm font-medium text-foreground"
              >
                Execution Mode
              </label>
              <select
                id="workflow-rule-execution-mode"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                )}
                value={formData.executionMode}
                onChange={(e) => handleChange('executionMode', e.target.value)}
                disabled={isSubmitting}
                data-testid="workflow-rule-execution-mode-input"
              >
                <option value="SEQUENTIAL">Sequential</option>
                <option value="PARALLEL">Parallel</option>
              </select>
              <p className="text-xs text-muted-foreground">
                {formData.executionMode === 'PARALLEL'
                  ? 'Actions run concurrently for better performance.'
                  : 'Actions run one after another in order.'}
              </p>
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-filter-formula"
                className="text-sm font-medium text-foreground"
              >
                Filter Formula
              </label>
              <textarea
                id="workflow-rule-filter-formula"
                className={cn(
                  'min-h-[80px] resize-y rounded-md border border-border bg-background px-3 py-2.5 font-[inherit] text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground'
                )}
                value={formData.filterFormula}
                onChange={(e) => handleChange('filterFormula', e.target.value)}
                placeholder="Enter filter formula"
                disabled={isSubmitting}
                rows={3}
                data-testid="workflow-rule-filter-formula-input"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label
                htmlFor="workflow-rule-execution-order"
                className="text-sm font-medium text-foreground"
              >
                Execution Order
                <span className="ml-1 text-destructive" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                id="workflow-rule-execution-order"
                type="number"
                className={cn(
                  'rounded-md border border-border bg-background px-3 py-2.5 text-sm text-foreground transition-colors',
                  'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20',
                  'disabled:cursor-not-allowed disabled:bg-muted disabled:text-muted-foreground',
                  touched.executionOrder && errors.executionOrder && 'border-destructive'
                )}
                value={formData.executionOrder}
                onChange={(e) => handleChange('executionOrder', parseInt(e.target.value, 10) || 0)}
                onBlur={() => handleBlur('executionOrder')}
                min={0}
                disabled={isSubmitting}
                data-testid="workflow-rule-execution-order-input"
              />
              {touched.executionOrder && errors.executionOrder && (
                <span className="text-xs text-destructive" role="alert">
                  {errors.executionOrder}
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="workflow-rule-active"
                type="checkbox"
                className="h-4 w-4 accent-primary"
                checked={formData.active}
                onChange={(e) => handleChange('active', e.target.checked)}
                disabled={isSubmitting}
                data-testid="workflow-rule-active-input"
              />
              <label htmlFor="workflow-rule-active" className="text-sm font-medium text-foreground">
                Active
              </label>
            </div>

            {/* Actions Section */}
            <div className="flex flex-col gap-3 border-t border-border pt-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-foreground">Actions</h3>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleAddAction}
                  disabled={isSubmitting}
                  data-testid="add-action-button"
                >
                  Add Action
                </Button>
              </div>

              {formData.actions.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  No actions configured. Add an action to define what happens when this rule
                  triggers.
                </p>
              ) : (
                <div className="flex flex-col gap-3">
                  {formData.actions.map((action, index) => (
                    <div
                      key={index}
                      className="rounded-md border border-border bg-muted/30 p-4"
                      data-testid={`action-item-${index}`}
                    >
                      <div className="mb-3 flex items-center justify-between">
                        <span className="text-xs font-semibold uppercase text-muted-foreground">
                          Action {index + 1}
                        </span>
                        <div className="flex items-center gap-2">
                          <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
                            <input
                              type="checkbox"
                              className="h-3.5 w-3.5 accent-primary"
                              checked={action.active}
                              onChange={(e) =>
                                handleActionChange(index, 'active', e.target.checked)
                              }
                              disabled={isSubmitting}
                              data-testid={`action-active-${index}`}
                            />
                            Active
                          </label>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            className="h-7 border-destructive/30 px-2 text-xs text-destructive hover:bg-destructive/10"
                            onClick={() => handleRemoveAction(index)}
                            disabled={isSubmitting}
                            data-testid={`remove-action-${index}`}
                          >
                            Remove
                          </Button>
                        </div>
                      </div>
                      <div className="flex flex-col gap-2">
                        <div className="flex gap-3">
                          <div className="flex-1">
                            <label
                              htmlFor={`action-type-select-${index}`}
                              className="mb-1 block text-xs font-medium text-muted-foreground"
                            >
                              Action Type
                            </label>
                            <select
                              id={`action-type-select-${index}`}
                              className={cn(
                                'w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground transition-colors',
                                'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20'
                              )}
                              value={action.actionType}
                              onChange={(e) =>
                                handleActionChange(index, 'actionType', e.target.value)
                              }
                              disabled={isSubmitting}
                              data-testid={`action-type-${index}`}
                            >
                              {actionTypes && actionTypes.length > 0 ? (
                                ACTION_TYPE_CATEGORY_ORDER.filter((cat) =>
                                  actionTypes.some((at) => at.category === cat)
                                ).map((cat) => (
                                  <optgroup
                                    key={cat}
                                    label={ACTION_TYPE_CATEGORY_LABELS[cat] ?? cat}
                                  >
                                    {actionTypes
                                      .filter((at) => at.category === cat)
                                      .map((at) => (
                                        <option key={at.key} value={at.key}>
                                          {at.name}
                                        </option>
                                      ))}
                                  </optgroup>
                                ))
                              ) : (
                                <>
                                  <option value="FIELD_UPDATE">Field Update</option>
                                  <option value="EMAIL_ALERT">Email Alert</option>
                                  <option value="CREATE_RECORD">Create Record</option>
                                  <option value="INVOKE_SCRIPT">Invoke Script</option>
                                  <option value="OUTBOUND_MESSAGE">Outbound Message</option>
                                  <option value="CREATE_TASK">Create Task</option>
                                  <option value="PUBLISH_EVENT">Publish Event</option>
                                </>
                              )}
                            </select>
                          </div>
                          <div className="w-24">
                            <label
                              htmlFor={`action-order-input-${index}`}
                              className="mb-1 block text-xs font-medium text-muted-foreground"
                            >
                              Order
                            </label>
                            <input
                              id={`action-order-input-${index}`}
                              type="number"
                              className={cn(
                                'w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground transition-colors',
                                'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20'
                              )}
                              value={action.executionOrder}
                              onChange={(e) =>
                                handleActionChange(
                                  index,
                                  'executionOrder',
                                  parseInt(e.target.value, 10) || 0
                                )
                              }
                              min={0}
                              disabled={isSubmitting}
                              data-testid={`action-order-${index}`}
                            />
                          </div>
                        </div>
                        <div>
                          <label
                            htmlFor={`action-config-input-${index}`}
                            className="mb-1 block text-xs font-medium text-muted-foreground"
                          >
                            Configuration (JSON)
                          </label>
                          <textarea
                            id={`action-config-input-${index}`}
                            className={cn(
                              'min-h-[60px] w-full resize-y rounded-md border border-border bg-background px-3 py-2 font-mono text-xs text-foreground transition-colors',
                              'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20'
                            )}
                            value={action.config}
                            onChange={(e) => handleActionChange(index, 'config', e.target.value)}
                            placeholder='{"key": "value"}'
                            disabled={isSubmitting}
                            rows={3}
                            data-testid={`action-config-${index}`}
                          />
                        </div>
                        {/* Retry Configuration */}
                        <div className="flex gap-3">
                          <div className="w-24">
                            <label
                              htmlFor={`action-retry-count-${index}`}
                              className="mb-1 block text-xs font-medium text-muted-foreground"
                            >
                              Retries
                            </label>
                            <input
                              id={`action-retry-count-${index}`}
                              type="number"
                              className={cn(
                                'w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground transition-colors',
                                'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20'
                              )}
                              value={action.retryCount}
                              onChange={(e) =>
                                handleActionChange(
                                  index,
                                  'retryCount',
                                  parseInt(e.target.value, 10) || 0
                                )
                              }
                              min={0}
                              max={10}
                              disabled={isSubmitting}
                              data-testid={`action-retry-count-${index}`}
                            />
                          </div>
                          <div className="w-28">
                            <label
                              htmlFor={`action-retry-delay-${index}`}
                              className="mb-1 block text-xs font-medium text-muted-foreground"
                            >
                              Delay (sec)
                            </label>
                            <input
                              id={`action-retry-delay-${index}`}
                              type="number"
                              className={cn(
                                'w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground transition-colors',
                                'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20'
                              )}
                              value={action.retryDelaySeconds}
                              onChange={(e) =>
                                handleActionChange(
                                  index,
                                  'retryDelaySeconds',
                                  parseInt(e.target.value, 10) || 60
                                )
                              }
                              min={1}
                              disabled={isSubmitting || action.retryCount === 0}
                              data-testid={`action-retry-delay-${index}`}
                            />
                          </div>
                          <div className="flex-1">
                            <label
                              htmlFor={`action-retry-backoff-${index}`}
                              className="mb-1 block text-xs font-medium text-muted-foreground"
                            >
                              Backoff
                            </label>
                            <select
                              id={`action-retry-backoff-${index}`}
                              className={cn(
                                'w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground transition-colors',
                                'focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20'
                              )}
                              value={action.retryBackoff}
                              onChange={(e) =>
                                handleActionChange(index, 'retryBackoff', e.target.value)
                              }
                              disabled={isSubmitting || action.retryCount === 0}
                              data-testid={`action-retry-backoff-${index}`}
                            >
                              <option value="FIXED">Fixed</option>
                              <option value="EXPONENTIAL">Exponential</option>
                            </select>
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="mt-2 flex justify-end gap-3 border-t border-border pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={onCancel}
                disabled={isSubmitting}
                data-testid="workflow-rule-form-cancel"
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting} data-testid="workflow-rule-form-submit">
                {isSubmitting ? 'Saving...' : 'Save'}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}

// ---- Action Logs Detail Modal ----

interface ActionLogDetailModalProps {
  executionLogId: string
  executionStatus: string
  onClose: () => void
}

function ActionLogDetailModal({
  executionLogId,
  executionStatus,
  onClose,
}: ActionLogDetailModalProps): React.ReactElement {
  const { apiClient } = useApi()
  const { formatDate } = useI18n()

  const {
    data: actionLogs,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['workflow-action-logs', executionLogId],
    queryFn: () =>
      apiClient.get<WorkflowActionLog[]>(`/control/workflow-rules/logs/${executionLogId}/actions`),
  })

  const [expandedRow, setExpandedRow] = useState<string | null>(null)

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={(e) => e.target === e.currentTarget && onClose()}
      role="presentation"
      data-testid="action-log-detail-overlay"
    >
      <div
        className="w-full max-w-[900px] max-h-[80vh] overflow-y-auto rounded-lg bg-background shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="action-log-detail-title"
        data-testid="action-log-detail-modal"
      >
        <div className="flex items-center justify-between border-b border-border p-6">
          <div>
            <h2 id="action-log-detail-title" className="text-lg font-semibold text-foreground">
              Action Execution Details
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Execution: {executionLogId.substring(0, 8)}... &mdash;{' '}
              <span
                className={cn(
                  'font-semibold',
                  executionStatus === 'SUCCESS' ? 'text-emerald-600' : 'text-destructive'
                )}
              >
                {executionStatus}
              </span>
            </p>
          </div>
          <button
            type="button"
            className="rounded p-2 text-2xl leading-none text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            onClick={onClose}
            aria-label="Close"
            data-testid="action-log-detail-close"
          >
            &times;
          </button>
        </div>
        <div className="p-6">
          {isLoading ? (
            <div className="flex items-center justify-center p-8">
              <LoadingSpinner size="medium" label="Loading action logs..." />
            </div>
          ) : error ? (
            <ErrorMessage error={error instanceof Error ? error : new Error('An error occurred')} />
          ) : !actionLogs || actionLogs.length === 0 ? (
            <p className="text-center text-sm text-muted-foreground">
              No action logs found for this execution.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table
                className="w-full border-collapse text-[0.8125rem]"
                role="grid"
                aria-label="Action Execution Logs"
                data-testid="action-logs-table"
              >
                <thead>
                  <tr>
                    <th className="px-3 py-2 text-left text-xs font-semibold uppercase text-muted-foreground border-b whitespace-nowrap">
                      Action Type
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-semibold uppercase text-muted-foreground border-b whitespace-nowrap">
                      Status
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-semibold uppercase text-muted-foreground border-b whitespace-nowrap">
                      Attempt
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-semibold uppercase text-muted-foreground border-b whitespace-nowrap">
                      Duration
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-semibold uppercase text-muted-foreground border-b whitespace-nowrap">
                      Error
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-semibold uppercase text-muted-foreground border-b whitespace-nowrap">
                      Executed At
                    </th>
                    <th className="px-3 py-2 text-left text-xs font-semibold uppercase text-muted-foreground border-b whitespace-nowrap">
                      Details
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {actionLogs.map((log) => (
                    <React.Fragment key={log.id}>
                      <tr className="hover:bg-muted/50 transition-colors">
                        <td className="px-3 py-2 border-b whitespace-nowrap">
                          <span className="inline-block rounded bg-muted px-2 py-0.5 text-xs font-semibold text-primary">
                            {log.actionType}
                          </span>
                        </td>
                        <td className="px-3 py-2 border-b whitespace-nowrap">
                          <span
                            className={cn(
                              'inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold',
                              log.status === 'SUCCESS'
                                ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                                : log.status === 'SKIPPED'
                                  ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                                  : 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300'
                            )}
                          >
                            {log.status}
                          </span>
                        </td>
                        <td className="px-3 py-2 border-b whitespace-nowrap text-muted-foreground">
                          {log.attemptNumber > 1 ? (
                            <span className="inline-block rounded bg-amber-100 px-1.5 py-0.5 text-xs font-semibold text-amber-800 dark:bg-amber-950 dark:text-amber-300">
                              #{log.attemptNumber}
                            </span>
                          ) : (
                            <span className="text-xs text-muted-foreground">1</span>
                          )}
                        </td>
                        <td className="px-3 py-2 border-b whitespace-nowrap text-muted-foreground">
                          {log.durationMs != null ? `${log.durationMs}ms` : '-'}
                        </td>
                        <td className="px-3 py-2 border-b max-w-[200px] truncate text-muted-foreground">
                          {log.errorMessage || '-'}
                        </td>
                        <td className="px-3 py-2 border-b whitespace-nowrap text-muted-foreground">
                          {log.executedAt
                            ? formatDate(new Date(log.executedAt), {
                                hour: '2-digit',
                                minute: '2-digit',
                                second: '2-digit',
                              })
                            : '-'}
                        </td>
                        <td className="px-3 py-2 border-b whitespace-nowrap">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            className="h-6 px-2 text-xs"
                            onClick={() => setExpandedRow(expandedRow === log.id ? null : log.id)}
                            data-testid={`expand-action-log-${log.id}`}
                          >
                            {expandedRow === log.id ? 'Hide' : 'Show'}
                          </Button>
                        </td>
                      </tr>
                      {expandedRow === log.id && (
                        <tr>
                          <td colSpan={7} className="border-b bg-muted/20 px-3 py-3">
                            <div className="flex flex-col gap-2">
                              {log.inputSnapshot && (
                                <div>
                                  <span className="text-xs font-semibold text-muted-foreground">
                                    Input:
                                  </span>
                                  <pre className="mt-1 overflow-x-auto rounded bg-muted p-2 text-xs text-foreground">
                                    {formatJson(log.inputSnapshot)}
                                  </pre>
                                </div>
                              )}
                              {log.outputSnapshot && (
                                <div>
                                  <span className="text-xs font-semibold text-muted-foreground">
                                    Output:
                                  </span>
                                  <pre className="mt-1 overflow-x-auto rounded bg-muted p-2 text-xs text-foreground">
                                    {formatJson(log.outputSnapshot)}
                                  </pre>
                                </div>
                              )}
                              {log.errorMessage && (
                                <div>
                                  <span className="text-xs font-semibold text-muted-foreground">
                                    Error Details:
                                  </span>
                                  <pre className="mt-1 overflow-x-auto rounded bg-red-50 p-2 text-xs text-red-800 dark:bg-red-950 dark:text-red-300">
                                    {log.errorMessage}
                                  </pre>
                                </div>
                              )}
                              {!log.inputSnapshot && !log.outputSnapshot && !log.errorMessage && (
                                <p className="text-xs text-muted-foreground">
                                  No additional details available.
                                </p>
                              )}
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function formatJson(str: string): string {
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

// ---- Main Page ----

export function WorkflowRulesPage({
  testId = 'workflow-rules-page',
}: WorkflowRulesPageProps): React.ReactElement {
  const queryClient = useQueryClient()
  const { formatDate } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingWorkflowRule, setEditingWorkflowRule] = useState<WorkflowRule | undefined>(
    undefined
  )
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [workflowRuleToDelete, setWorkflowRuleToDelete] = useState<WorkflowRule | null>(null)
  const [logsItemId, setLogsItemId] = useState<string | null>(null)
  const [logsItemName, setLogsItemName] = useState('')
  const [actionLogDetail, setActionLogDetail] = useState<{
    executionLogId: string
    status: string
  } | null>(null)

  const {
    data: logs,
    isLoading: logsLoading,
    error: logsError,
  } = useQuery({
    queryKey: ['workflow-rule-logs', logsItemId],
    queryFn: () =>
      apiClient.get<WorkflowExecutionLog[]>(`/control/workflow-rules/${logsItemId}/logs`),
    enabled: !!logsItemId,
  })

  const logColumns: LogColumn<WorkflowExecutionLog>[] = [
    { key: 'status', header: 'Status' },
    { key: 'triggerType', header: 'Trigger Type' },
    { key: 'recordId', header: 'Record ID' },
    { key: 'actionsExecuted', header: 'Actions' },
    {
      key: 'durationMs',
      header: 'Duration',
      render: (v) => (v != null ? `${v}ms` : '-'),
    },
    { key: 'errorMessage', header: 'Error' },
    {
      key: 'executedAt',
      header: 'Executed At',
      render: (v) =>
        v
          ? formatDate(new Date(v as string), {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
              hour: '2-digit',
              minute: '2-digit',
            })
          : '-',
    },
    {
      key: 'id',
      header: 'Actions Detail',
      render: (_v, row) => (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="h-6 px-2 text-xs"
          onClick={(e) => {
            e.stopPropagation()
            setActionLogDetail({
              executionLogId: row.id as string,
              status: row.status as string,
            })
          }}
          data-testid={`view-action-logs-${row.id}`}
        >
          View Actions
        </Button>
      ),
    },
  ]

  const {
    data: workflowRules,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['workflow-rules'],
    queryFn: () => apiClient.get<WorkflowRule[]>(`/control/workflow-rules`),
  })

  const workflowRuleList: WorkflowRule[] = workflowRules ?? []

  const createMutation = useMutation({
    mutationFn: (data: WorkflowRuleFormData) =>
      apiClient.post<WorkflowRule>(`/control/workflow-rules?userId=system`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow-rules'] })
      showToast('Workflow rule created successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: WorkflowRuleFormData }) =>
      apiClient.put<WorkflowRule>(`/control/workflow-rules/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow-rules'] })
      showToast('Workflow rule updated successfully', 'success')
      handleCloseForm()
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/control/workflow-rules/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow-rules'] })
      showToast('Workflow rule deleted successfully', 'success')
      setDeleteDialogOpen(false)
      setWorkflowRuleToDelete(null)
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred', 'error')
    },
  })

  const executeMutation = useMutation({
    mutationFn: (id: string) =>
      apiClient.post<{ executionLogIds: string[] }>(`/control/workflow-rules/${id}/execute`, {
        recordIds: [],
      }),
    onSuccess: (data) => {
      const count = data?.executionLogIds?.length ?? 0
      showToast(`Manual execution complete: ${count} execution log(s) created`, 'success')
      queryClient.invalidateQueries({ queryKey: ['workflow-rules'] })
    },
    onError: (err: Error) => {
      showToast(err.message || 'An error occurred during execution', 'error')
    },
  })

  const handleCreate = useCallback(() => {
    setEditingWorkflowRule(undefined)
    setIsFormOpen(true)
  }, [])

  const handleEdit = useCallback((workflowRule: WorkflowRule) => {
    setEditingWorkflowRule(workflowRule)
    setIsFormOpen(true)
  }, [])

  const handleCloseForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingWorkflowRule(undefined)
  }, [])

  const handleFormSubmit = useCallback(
    (data: WorkflowRuleFormData) => {
      if (editingWorkflowRule) {
        updateMutation.mutate({ id: editingWorkflowRule.id, data })
      } else {
        createMutation.mutate(data)
      }
    },
    [editingWorkflowRule, createMutation, updateMutation]
  )

  const handleDeleteClick = useCallback((workflowRule: WorkflowRule) => {
    setWorkflowRuleToDelete(workflowRule)
    setDeleteDialogOpen(true)
  }, [])

  const handleDeleteConfirm = useCallback(() => {
    if (workflowRuleToDelete) {
      deleteMutation.mutate(workflowRuleToDelete.id)
    }
  }, [workflowRuleToDelete, deleteMutation])

  const handleDeleteCancel = useCallback(() => {
    setDeleteDialogOpen(false)
    setWorkflowRuleToDelete(null)
  }, [])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <div className="flex min-h-[400px] items-center justify-center">
          <LoadingSpinner size="large" label="Loading workflow rules..." />
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
        <ErrorMessage
          error={error instanceof Error ? error : new Error('An error occurred')}
          onRetry={() => refetch()}
        />
      </div>
    )
  }

  const isSubmitting = createMutation.isPending || updateMutation.isPending

  return (
    <div className="mx-auto max-w-[1400px] space-y-6 p-6 lg:p-8" data-testid={testId}>
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">Workflow Rules</h1>
        <Button
          type="button"
          onClick={handleCreate}
          aria-label="Create Workflow Rule"
          data-testid="add-workflow-rule-button"
        >
          Create Workflow Rule
        </Button>
      </header>

      {workflowRuleList.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-card py-16 text-center text-muted-foreground"
          data-testid="empty-state"
        >
          <p>No workflow rules found.</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table
            className="w-full border-collapse"
            role="grid"
            aria-label="Workflow Rules"
            data-testid="workflow-rules-table"
          >
            <thead>
              <tr role="row" className="bg-muted">
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Name
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Collection ID
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Trigger Type
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Actions
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Mode
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Error Handling
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Active
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  Created
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="border-b border-border px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-muted-foreground"
                >
                  &nbsp;
                </th>
              </tr>
            </thead>
            <tbody>
              {workflowRuleList.map((workflowRule, index) => (
                <tr
                  key={workflowRule.id}
                  role="row"
                  className="border-b border-border transition-colors last:border-b-0 hover:bg-muted/50"
                  data-testid={`workflow-rule-row-${index}`}
                >
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {workflowRule.name}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span className="max-w-[200px] truncate">
                      {workflowRule.collectionId || '-'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold tracking-wider',
                        BEFORE_SAVE_TYPES.includes(workflowRule.triggerType)
                          ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                          : workflowRule.triggerType === 'SCHEDULED'
                            ? 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                            : workflowRule.triggerType === 'MANUAL'
                              ? 'bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-300'
                              : 'bg-muted text-primary'
                      )}
                    >
                      {TRIGGER_TYPE_LABELS[workflowRule.triggerType] ?? workflowRule.triggerType}
                    </span>
                    {BEFORE_SAVE_TYPES.includes(workflowRule.triggerType) && (
                      <span className="ml-1.5 inline-block rounded bg-amber-200/60 px-1.5 py-0.5 text-[10px] font-medium text-amber-700 dark:bg-amber-900/40 dark:text-amber-400">
                        Sync
                      </span>
                    )}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <div className="flex flex-wrap items-center gap-1">
                      <span className="text-muted-foreground">
                        {workflowRule.actions?.length ?? 0}
                      </span>
                      {workflowRule.actions &&
                        workflowRule.actions.length > 0 &&
                        workflowRule.actions.length <= 3 &&
                        workflowRule.actions.map((a, ai) => (
                          <span
                            key={ai}
                            className="inline-block rounded bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground"
                          >
                            {a.actionType}
                          </span>
                        ))}
                    </div>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-2.5 py-0.5 text-xs font-medium',
                        workflowRule.executionMode === 'PARALLEL'
                          ? 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {workflowRule.executionMode === 'PARALLEL' ? 'Parallel' : 'Sequential'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-2.5 py-0.5 text-xs font-medium',
                        workflowRule.errorHandling === 'CONTINUE_ON_ERROR'
                          ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {workflowRule.errorHandling === 'CONTINUE_ON_ERROR' ? 'Continue' : 'Stop'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    <span
                      className={cn(
                        'inline-block rounded-full px-3 py-1 text-xs font-semibold',
                        workflowRule.active
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                          : 'bg-muted text-muted-foreground'
                      )}
                    >
                      {workflowRule.active ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-sm text-foreground">
                    {formatDate(new Date(workflowRule.createdAt), {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </td>
                  <td role="gridcell" className="px-4 py-3 text-right text-sm">
                    <div className="flex justify-end gap-2">
                      {workflowRule.triggerType === 'MANUAL' && (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="border-violet-300 text-violet-700 hover:bg-violet-50 dark:border-violet-700 dark:text-violet-400 dark:hover:bg-violet-950"
                          onClick={() => executeMutation.mutate(workflowRule.id)}
                          disabled={executeMutation.isPending}
                          aria-label={`Execute ${workflowRule.name}`}
                          data-testid={`execute-button-${index}`}
                        >
                          {executeMutation.isPending ? 'Running...' : 'Execute'}
                        </Button>
                      )}
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setLogsItemId(workflowRule.id)
                          setLogsItemName(workflowRule.name)
                        }}
                        aria-label={`View logs for ${workflowRule.name}`}
                        data-testid={`logs-button-${index}`}
                      >
                        Logs
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => handleEdit(workflowRule)}
                        aria-label={`Edit ${workflowRule.name}`}
                        data-testid={`edit-button-${index}`}
                      >
                        Edit
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="border-destructive/30 text-destructive hover:bg-destructive/10"
                        onClick={() => handleDeleteClick(workflowRule)}
                        aria-label={`Delete ${workflowRule.name}`}
                        data-testid={`delete-button-${index}`}
                      >
                        Delete
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {logsItemId && (
        <ExecutionLogModal<WorkflowExecutionLog>
          title="Workflow Execution Logs"
          subtitle={logsItemName}
          columns={logColumns}
          data={logs ?? []}
          isLoading={logsLoading}
          error={logsError instanceof Error ? logsError : null}
          onClose={() => setLogsItemId(null)}
          emptyMessage="No execution logs found."
        />
      )}

      {actionLogDetail && (
        <ActionLogDetailModal
          executionLogId={actionLogDetail.executionLogId}
          executionStatus={actionLogDetail.status}
          onClose={() => setActionLogDetail(null)}
        />
      )}

      {isFormOpen && (
        <WorkflowRuleForm
          workflowRule={editingWorkflowRule}
          onSubmit={handleFormSubmit}
          onCancel={handleCloseForm}
          isSubmitting={isSubmitting}
        />
      )}

      <ConfirmDialog
        open={deleteDialogOpen}
        title="Delete Workflow Rule"
        message="Are you sure you want to delete this workflow rule? This action cannot be undone."
        confirmLabel="Delete"
        cancelLabel="Cancel"
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
        variant="danger"
      />
    </div>
  )
}

export default WorkflowRulesPage
