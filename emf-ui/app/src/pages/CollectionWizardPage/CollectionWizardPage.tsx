/**
 * CollectionWizardPage Component
 *
 * A multi-step wizard for creating new collections.
 * Steps: Basics -> Fields -> Authorization -> Review & Create
 *
 * Replaces the simple CollectionFormPage for the /collections/new route,
 * providing a guided experience with template selection, field configuration,
 * and optional authorization setup.
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Check,
  ChevronRight,
  ChevronLeft,
  Plus,
  Trash2,
  Users,
  Building2,
  CheckSquare,
  FileText,
  Wand2,
} from 'lucide-react'
import { useI18n } from '../../context/I18nContext'
import { getTenantSlug } from '../../context/TenantContext'

import { useApi } from '../../context/ApiContext'
import { useToast } from '../../components/Toast'
import { LoadingSpinner } from '../../components'
import { cn } from '@/lib/utils'

/**
 * Props for CollectionWizardPage component
 */
export interface CollectionWizardPageProps {
  /** Optional test ID for testing */
  testId?: string
}

/**
 * Basics step data
 */
interface BasicsData {
  name: string
  displayName: string
  description: string
  storageMode: 'JSONB' | 'PHYSICAL_TABLE'
  active: boolean
}

/**
 * Field data for a single field
 */
interface FieldData {
  id: string
  name: string
  displayName: string
  type: string
  required: boolean
}

/**
 * Authorization step data
 */
interface AuthData {
  readPolicyId: string
  createPolicyId: string
  updatePolicyId: string
  deletePolicyId: string
}

/**
 * Step definitions
 */
const STEPS = [
  { number: 1, label: 'Basics' },
  { number: 2, label: 'Fields' },
  { number: 3, label: 'Authorization' },
  { number: 4, label: 'Review' },
] as const

/**
 * Available field types in the wizard
 */
const FIELD_TYPES = ['STRING', 'INTEGER', 'LONG', 'DOUBLE', 'BOOLEAN', 'DATE', 'DATETIME'] as const

/**
 * Template definitions
 */
const TEMPLATES: Record<
  string,
  { name: string; icon: 'contact' | 'account' | 'task' | 'custom'; fields: Omit<FieldData, 'id'>[] }
> = {
  contact: {
    name: 'Contact',
    icon: 'contact',
    fields: [
      { name: 'name', displayName: 'Name', type: 'STRING', required: true },
      { name: 'email', displayName: 'Email', type: 'STRING', required: false },
      { name: 'phone', displayName: 'Phone', type: 'STRING', required: false },
      { name: 'company', displayName: 'Company', type: 'STRING', required: false },
    ],
  },
  account: {
    name: 'Account',
    icon: 'account',
    fields: [
      { name: 'name', displayName: 'Name', type: 'STRING', required: true },
      { name: 'industry', displayName: 'Industry', type: 'STRING', required: false },
      { name: 'revenue', displayName: 'Revenue', type: 'DOUBLE', required: false },
      { name: 'website', displayName: 'Website', type: 'STRING', required: false },
    ],
  },
  task: {
    name: 'Task',
    icon: 'task',
    fields: [
      { name: 'title', displayName: 'Title', type: 'STRING', required: true },
      { name: 'status', displayName: 'Status', type: 'STRING', required: false },
      { name: 'priority', displayName: 'Priority', type: 'STRING', required: false },
      { name: 'due_date', displayName: 'Due Date', type: 'DATE', required: false },
    ],
  },
  custom: {
    name: 'Custom',
    icon: 'custom',
    fields: [],
  },
}

/**
 * Generate a unique field ID
 */
function generateFieldId(): string {
  return `field-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`
}

/**
 * Convert display name to collection/field name format
 * Lowercase, spaces to underscores, strip special chars
 */
function toFieldName(displayName: string): string {
  return displayName
    .toLowerCase()
    .replace(/\s+/g, '_')
    .replace(/[^a-z0-9_]/g, '')
    .replace(/^[^a-z]/, '')
    .replace(/_+/g, '_')
    .replace(/_$/, '')
}

/**
 * Get the template icon component
 */
function getTemplateIcon(icon: string): React.ReactNode {
  switch (icon) {
    case 'contact':
      return <Users size={20} />
    case 'account':
      return <Building2 size={20} />
    case 'task':
      return <CheckSquare size={20} />
    case 'custom':
      return <Wand2 size={20} />
    default:
      return <FileText size={20} />
  }
}

/**
 * Validation errors for basics step
 */
interface BasicsErrors {
  name?: string
  displayName?: string
}

/**
 * CollectionWizardPage Component
 *
 * A 4-step wizard for creating new collections:
 * 1. Basics - collection name, service, storage mode
 * 2. Fields - template selection and field configuration
 * 3. Authorization - optional policy assignment
 * 4. Review - summary and create
 */
export function CollectionWizardPage({
  testId = 'collection-wizard-page',
}: CollectionWizardPageProps): React.ReactElement {
  const navigate = useNavigate()
  const { t } = useI18n()
  const { apiClient } = useApi()
  const { showToast } = useToast()

  // Wizard state
  const [currentStep, setCurrentStep] = useState(1)
  const [basics, setBasics] = useState<BasicsData>({
    name: '',
    displayName: '',
    description: '',
    storageMode: 'JSONB',
    active: true,
  })
  const [fields, setFields] = useState<FieldData[]>([])
  const [selectedTemplate, setSelectedTemplate] = useState<string | null>(null)
  const [authorization, setAuthorization] = useState<AuthData>({
    readPolicyId: '',
    createPolicyId: '',
    updatePolicyId: '',
    deletePolicyId: '',
  })
  const [isCreating, setIsCreating] = useState(false)
  const [basicsErrors, setBasicsErrors] = useState<BasicsErrors>({})

  // Fetch policies
  const { data: policiesData } = useQuery({
    queryKey: ['policies'],
    queryFn: () =>
      apiClient.get<Array<{ id: string; name: string; description?: string }>>(
        '/control/policies?size=100'
      ),
  })

  const policies = useMemo(() => {
    if (!policiesData) return []
    // Handle both array and paginated response
    if (Array.isArray(policiesData)) return policiesData
    const pd = policiesData as unknown as {
      content?: Array<{ id: string; name: string; description?: string }>
    }
    if (pd.content) return pd.content
    return []
  }, [policiesData])

  // Basics field handlers
  const handleDisplayNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const displayName = e.target.value
    const generatedName = toFieldName(displayName)
    setBasics((prev) => ({
      ...prev,
      displayName,
      name: generatedName,
    }))
    setBasicsErrors((prev) => ({ ...prev, displayName: undefined, name: undefined }))
  }, [])

  const handleNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setBasics((prev) => ({ ...prev, name: e.target.value }))
    setBasicsErrors((prev) => ({ ...prev, name: undefined }))
  }, [])

  const handleDescriptionChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setBasics((prev) => ({ ...prev, description: e.target.value }))
  }, [])

  const handleStorageModeChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setBasics((prev) => ({
      ...prev,
      storageMode: e.target.value as 'JSONB' | 'PHYSICAL_TABLE',
    }))
  }, [])

  const handleActiveChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setBasics((prev) => ({ ...prev, active: e.target.checked }))
  }, [])

  // Template selection handler
  const handleTemplateSelect = useCallback((templateKey: string) => {
    setSelectedTemplate(templateKey)
    const template = TEMPLATES[templateKey]
    if (template) {
      const templateFields = template.fields.map((f) => ({
        ...f,
        id: generateFieldId(),
      }))
      setFields(templateFields)
    }
  }, [])

  // Field handlers
  const handleAddField = useCallback(() => {
    setFields((prev) => [
      ...prev,
      {
        id: generateFieldId(),
        name: '',
        displayName: '',
        type: 'STRING',
        required: false,
      },
    ])
  }, [])

  const handleRemoveField = useCallback((fieldId: string) => {
    setFields((prev) => prev.filter((f) => f.id !== fieldId))
  }, [])

  const handleFieldChange = useCallback(
    (fieldId: string, key: keyof FieldData, value: string | boolean) => {
      setFields((prev) =>
        prev.map((f) => {
          if (f.id !== fieldId) return f
          if (key === 'displayName' && typeof value === 'string') {
            return { ...f, displayName: value, name: toFieldName(value) }
          }
          return { ...f, [key]: value }
        })
      )
    },
    []
  )

  // Authorization handlers
  const handleAuthChange = useCallback((key: keyof AuthData, value: string) => {
    setAuthorization((prev) => ({ ...prev, [key]: value }))
  }, [])

  const handleSkipAuth = useCallback(() => {
    setAuthorization({
      readPolicyId: '',
      createPolicyId: '',
      updatePolicyId: '',
      deletePolicyId: '',
    })
    setCurrentStep(4)
  }, [])

  // Validation
  const validateBasics = useCallback((): boolean => {
    const errors: BasicsErrors = {}
    if (!basics.name) {
      errors.name = t('collectionForm.validation.nameRequired')
    } else if (!/^[a-z][a-z0-9_]*$/.test(basics.name)) {
      errors.name = t('collectionForm.validation.nameFormat')
    } else if (basics.name.length > 50) {
      errors.name = t('collectionForm.validation.nameTooLong')
    }
    if (!basics.displayName) {
      errors.displayName = t('collectionForm.validation.displayNameRequired')
    } else if (basics.displayName.length > 100) {
      errors.displayName = t('collectionForm.validation.displayNameTooLong')
    }
    setBasicsErrors(errors)
    return Object.keys(errors).length === 0
  }, [basics, t])

  // Step navigation
  const handleNext = useCallback(() => {
    if (currentStep === 1) {
      if (!validateBasics()) return
    }
    setCurrentStep((prev) => Math.min(prev + 1, 4))
  }, [currentStep, validateBasics])

  const handleBack = useCallback(() => {
    setCurrentStep((prev) => Math.max(prev - 1, 1))
  }, [])

  const handleCancel = useCallback(() => {
    navigate(`/${getTenantSlug()}/collections`)
  }, [navigate])

  // Create collection
  const handleCreate = useCallback(async () => {
    setIsCreating(true)
    try {
      // Step 1: Create the collection
      const created = await apiClient.post<{
        id: string
        name: string
        description: string
        active: boolean
        currentVersion: number
        createdAt: string
        updatedAt: string
      }>('/control/collections', {
        name: basics.name,
        description: basics.description || '',
      })

      const collectionId = created.id

      // Step 2: Create fields
      for (const field of fields) {
        if (field.name && field.displayName) {
          await apiClient.post(`/control/collections/${collectionId}/fields`, {
            name: field.name,
            displayName: field.displayName,
            type: field.type,
            required: field.required,
            unique: false,
          })
        }
      }

      // Step 3: Create route policies if authorization is configured
      const hasAuth =
        authorization.readPolicyId ||
        authorization.createPolicyId ||
        authorization.updatePolicyId ||
        authorization.deletePolicyId

      if (hasAuth) {
        const collectionPath = basics.name

        if (authorization.readPolicyId) {
          await apiClient.post('/control/authorization/route-policies', {
            method: 'GET',
            pathPattern: `/gateway/${collectionPath}/**`,
            policyId: authorization.readPolicyId,
          })
        }

        if (authorization.createPolicyId) {
          await apiClient.post('/control/authorization/route-policies', {
            method: 'POST',
            pathPattern: `/gateway/${collectionPath}`,
            policyId: authorization.createPolicyId,
          })
        }

        if (authorization.updatePolicyId) {
          await apiClient.post('/control/authorization/route-policies', {
            method: 'PUT',
            pathPattern: `/gateway/${collectionPath}/**`,
            policyId: authorization.updatePolicyId,
          })
        }

        if (authorization.deletePolicyId) {
          await apiClient.post('/control/authorization/route-policies', {
            method: 'DELETE',
            pathPattern: `/gateway/${collectionPath}/**`,
            policyId: authorization.deletePolicyId,
          })
        }
      }

      showToast(t('success.created', { item: 'Collection' }), 'success')
      navigate(`/${getTenantSlug()}/collections/${collectionId}`)
    } catch (error) {
      console.error('Failed to create collection:', error)
      showToast(error instanceof Error ? error.message : t('errors.generic'), 'error')
    } finally {
      setIsCreating(false)
    }
  }, [apiClient, basics, fields, authorization, navigate, showToast, t])

  // Check if authorization is configured
  const hasAuthConfigured = useMemo(
    () =>
      Boolean(
        authorization.readPolicyId ||
        authorization.createPolicyId ||
        authorization.updatePolicyId ||
        authorization.deletePolicyId
      ),
    [authorization]
  )

  // Get policy name by ID
  const getPolicyName = useCallback(
    (policyId: string): string => {
      if (!policyId) return t('common.none')
      const policy = policies.find((p) => p.id === policyId)
      return policy ? policy.name : policyId
    },
    [policies, t]
  )

  // Render step indicator
  const renderStepIndicator = useCallback(() => {
    return (
      <div
        className="flex items-center justify-center mb-8 px-4 max-sm:flex-wrap max-sm:gap-2"
        data-testid="wizard-step-indicator"
      >
        {STEPS.map((step, index) => {
          const isCompleted = step.number < currentStep
          const isCurrent = step.number === currentStep
          const isFuture = step.number > currentStep

          return (
            <React.Fragment key={step.number}>
              <div
                className="flex items-center gap-2 cursor-default"
                data-testid={`wizard-step-${step.number}`}
              >
                <div
                  className={cn(
                    'flex items-center justify-center w-8 h-8 rounded-full text-sm font-semibold shrink-0 transition-all duration-200 motion-reduce:transition-none',
                    isCompleted && 'border-2 border-green-600 bg-green-600 text-white',
                    isCurrent && 'border-2 border-primary bg-primary text-white',
                    isFuture &&
                      'border-2 border-border text-muted-foreground bg-transparent dark:border-gray-600 dark:text-gray-400'
                  )}
                  data-testid={`wizard-step-circle-${step.number}`}
                >
                  {isCompleted ? <Check size={14} /> : step.number}
                </div>
                <span
                  className={cn(
                    'text-sm font-medium text-muted-foreground whitespace-nowrap dark:text-gray-400 max-sm:hidden',
                    isCompleted && 'text-green-600',
                    isCurrent && 'text-primary font-semibold'
                  )}
                >
                  {step.label}
                </span>
              </div>
              {index < STEPS.length - 1 && (
                <div
                  className={cn(
                    'flex-[0_0_3rem] h-0.5 mx-2 bg-border dark:bg-gray-600 max-sm:hidden',
                    !isFuture && 'bg-green-600'
                  )}
                />
              )}
            </React.Fragment>
          )
        })}
      </div>
    )
  }, [currentStep])

  // Render Step 1: Basics
  const renderBasicsStep = useCallback(() => {
    return (
      <div data-testid="wizard-step-basics">
        <h2 className="text-xl font-semibold text-foreground m-0 mb-6 dark:text-gray-100">
          {t('collections.createCollection')}
        </h2>
        <div className="flex flex-col gap-5 max-w-[600px]">
          {/* Display Name */}
          <div className="flex flex-col gap-1">
            <label
              htmlFor="wizard-display-name"
              className="flex items-center gap-1 text-sm font-medium text-foreground dark:text-gray-100"
            >
              {t('collections.displayName')}
              <span className="text-destructive font-semibold" aria-hidden="true">
                *
              </span>
            </label>
            <input
              id="wizard-display-name"
              type="text"
              className={cn(
                'px-3 py-2 text-base leading-normal text-foreground bg-background border border-border rounded-md transition-all duration-150 motion-reduce:transition-none dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600',
                'focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/25',
                'disabled:bg-muted disabled:text-muted-foreground disabled:cursor-not-allowed dark:disabled:bg-gray-800 dark:disabled:text-gray-500',
                'placeholder:text-muted-foreground dark:placeholder:text-gray-500',
                basicsErrors.displayName &&
                  'border-destructive focus:border-destructive focus:ring-destructive/25'
              )}
              value={basics.displayName}
              onChange={handleDisplayNameChange}
              placeholder={t('collectionForm.displayNamePlaceholder')}
              aria-required="true"
              aria-invalid={!!basicsErrors.displayName}
              data-testid="wizard-display-name-input"
            />
            {basicsErrors.displayName && (
              <span
                className="flex items-center gap-1 text-sm text-destructive mt-1"
                role="alert"
                data-testid="wizard-display-name-error"
              >
                {basicsErrors.displayName}
              </span>
            )}
          </div>

          {/* Collection Name (auto-generated) */}
          <div className="flex flex-col gap-1">
            <label
              htmlFor="wizard-name"
              className="flex items-center gap-1 text-sm font-medium text-foreground dark:text-gray-100"
            >
              {t('collections.collectionName')}
              <span className="text-destructive font-semibold" aria-hidden="true">
                *
              </span>
            </label>
            <input
              id="wizard-name"
              type="text"
              className={cn(
                'px-3 py-2 text-base leading-normal text-foreground bg-background border border-border rounded-md transition-all duration-150 motion-reduce:transition-none dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600',
                'focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/25',
                'placeholder:text-muted-foreground dark:placeholder:text-gray-500',
                basicsErrors.name &&
                  'border-destructive focus:border-destructive focus:ring-destructive/25'
              )}
              value={basics.name}
              onChange={handleNameChange}
              placeholder={t('collectionForm.namePlaceholder')}
              aria-required="true"
              aria-invalid={!!basicsErrors.name}
              data-testid="wizard-name-input"
            />
            {basicsErrors.name && (
              <span
                className="flex items-center gap-1 text-sm text-destructive mt-1"
                role="alert"
                data-testid="wizard-name-error"
              >
                {basicsErrors.name}
              </span>
            )}
            <span className="text-xs text-muted-foreground mt-1 dark:text-gray-400">
              {t('collectionForm.nameHint')}
            </span>
          </div>

          {/* Description */}
          <div className="flex flex-col gap-1">
            <label
              htmlFor="wizard-description"
              className="flex items-center gap-1 text-sm font-medium text-foreground dark:text-gray-100"
            >
              {t('collections.description')}
              <span className="text-xs font-normal text-muted-foreground ml-1 dark:text-gray-400">
                ({t('common.optional')})
              </span>
            </label>
            <textarea
              id="wizard-description"
              className="px-3 py-2 text-base leading-normal text-foreground bg-background border border-border rounded-md resize-y min-h-[80px] transition-all duration-150 motion-reduce:transition-none focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/25 placeholder:text-muted-foreground dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600 dark:placeholder:text-gray-500"
              value={basics.description}
              onChange={handleDescriptionChange}
              placeholder={t('collectionForm.descriptionPlaceholder')}
              rows={3}
              data-testid="wizard-description-input"
            />
          </div>

          {/* Storage Mode */}
          <div className="flex flex-col gap-1">
            <label
              htmlFor="wizard-storage-mode"
              className="flex items-center gap-1 text-sm font-medium text-foreground dark:text-gray-100"
            >
              {t('collections.storageMode')}
            </label>
            <select
              id="wizard-storage-mode"
              className="px-3 py-2 text-base leading-normal text-foreground bg-background border border-border rounded-md appearance-none bg-[url('data:image/svg+xml,%3csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20fill=%27none%27%20viewBox=%270%200%2020%2020%27%3e%3cpath%20stroke=%27%236b7280%27%20stroke-linecap=%27round%27%20stroke-linejoin=%27round%27%20stroke-width=%271.5%27%20d=%27M6%208l4%204%204-4%27/%3e%3c/svg%3e')] bg-[position:right_0.5rem_center] bg-no-repeat bg-[length:1.5em_1.5em] pr-10 cursor-pointer transition-all duration-150 motion-reduce:transition-none focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/25 disabled:cursor-not-allowed dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600"
              value={basics.storageMode}
              onChange={handleStorageModeChange}
              data-testid="wizard-storage-mode-select"
            >
              <option value="JSONB">{t('collections.storageModes.jsonb')}</option>
              <option value="PHYSICAL_TABLE">{t('collections.storageModes.physicalTable')}</option>
            </select>
            <span className="text-xs text-muted-foreground mt-1 dark:text-gray-400">
              {t('collectionForm.storageModeHint')}
            </span>
          </div>

          {/* Active Toggle */}
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <input
                id="wizard-active"
                type="checkbox"
                className="w-[1.125rem] h-[1.125rem] accent-primary cursor-pointer"
                checked={basics.active}
                onChange={handleActiveChange}
                data-testid="wizard-active-checkbox"
              />
              <label
                htmlFor="wizard-active"
                className="text-base text-foreground cursor-pointer dark:text-gray-100"
              >
                {t('collections.active')}
              </label>
            </div>
            <span className="text-xs text-muted-foreground mt-1 dark:text-gray-400">
              {t('collectionForm.activeHint')}
            </span>
          </div>
        </div>
      </div>
    )
  }, [
    basics,
    basicsErrors,
    t,
    handleDisplayNameChange,
    handleNameChange,
    handleDescriptionChange,
    handleStorageModeChange,
    handleActiveChange,
  ])

  // Render Step 2: Fields
  const renderFieldsStep = useCallback(() => {
    return (
      <div data-testid="wizard-step-fields">
        <h2 className="text-xl font-semibold text-foreground m-0 mb-6 dark:text-gray-100">
          {t('collections.fields')}
        </h2>

        {/* Template Selection */}
        <div className="mb-6">
          <p className="text-sm font-medium text-muted-foreground m-0 mb-3 uppercase tracking-wider dark:text-gray-400">
            Choose a template
          </p>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(180px,1fr))] gap-3 max-sm:grid-cols-2">
            {Object.entries(TEMPLATES).map(([key, template]) => (
              <button
                key={key}
                type="button"
                className={cn(
                  'flex flex-col items-center gap-2 p-4 border-2 border-border rounded-md bg-card cursor-pointer transition-all duration-150 motion-reduce:transition-none dark:border-gray-600 dark:bg-gray-800',
                  'hover:border-primary hover:bg-primary/5 dark:hover:border-primary dark:hover:bg-primary/10',
                  selectedTemplate === key &&
                    'border-primary bg-primary/[0.08] dark:border-primary dark:bg-primary/15'
                )}
                onClick={() => handleTemplateSelect(key)}
                data-testid={`wizard-template-${key}`}
              >
                <div
                  className={cn(
                    'flex items-center justify-center w-10 h-10 rounded-md bg-muted text-muted-foreground dark:bg-gray-700 dark:text-gray-400',
                    selectedTemplate === key && 'bg-primary text-white'
                  )}
                >
                  {getTemplateIcon(template.icon)}
                </div>
                <span className="text-sm font-medium text-foreground dark:text-gray-100">
                  {template.name}
                </span>
                <span className="text-xs text-muted-foreground dark:text-gray-400">
                  {template.fields.length === 0 ? 'No fields' : `${template.fields.length} fields`}
                </span>
              </button>
            ))}
          </div>
        </div>

        {/* Fields Table */}
        <div className="mt-6">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-base font-semibold text-foreground m-0 dark:text-gray-100">
              {t('collections.fields')}
            </h3>
            <button
              type="button"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-primary bg-transparent border border-dashed border-primary rounded-md cursor-pointer transition-colors duration-150 motion-reduce:transition-none hover:bg-primary/[0.08] dark:text-blue-400 dark:border-blue-400 dark:hover:bg-primary/15"
              onClick={handleAddField}
              data-testid="wizard-add-field-button"
            >
              <Plus size={14} />
              {t('collections.addField')}
            </button>
          </div>

          {fields.length === 0 ? (
            <div
              className="p-8 text-center text-muted-foreground text-sm dark:text-gray-400"
              data-testid="wizard-no-fields"
            >
              {t('fieldsPanel.noFields')}. {t('fieldsPanel.addFieldHint')}
            </div>
          ) : (
            <table
              className="w-full border-collapse border border-border rounded-md overflow-hidden dark:border-gray-700"
              data-testid="wizard-fields-table"
            >
              <thead>
                <tr>
                  <th className="px-3 py-2.5 text-xs font-semibold text-left uppercase tracking-wider text-muted-foreground bg-muted border-b border-border dark:text-gray-400 dark:bg-gray-700 dark:border-gray-600">
                    {t('collections.fieldName')}
                  </th>
                  <th className="px-3 py-2.5 text-xs font-semibold text-left uppercase tracking-wider text-muted-foreground bg-muted border-b border-border dark:text-gray-400 dark:bg-gray-700 dark:border-gray-600">
                    {t('collections.displayName')}
                  </th>
                  <th className="px-3 py-2.5 text-xs font-semibold text-left uppercase tracking-wider text-muted-foreground bg-muted border-b border-border dark:text-gray-400 dark:bg-gray-700 dark:border-gray-600">
                    {t('collections.fieldType')}
                  </th>
                  <th className="px-3 py-2.5 text-xs font-semibold text-left uppercase tracking-wider text-muted-foreground bg-muted border-b border-border dark:text-gray-400 dark:bg-gray-700 dark:border-gray-600">
                    {t('fields.validation.required')}
                  </th>
                  <th className="px-3 py-2.5 text-xs font-semibold text-left uppercase tracking-wider text-muted-foreground bg-muted border-b border-border dark:text-gray-400 dark:bg-gray-700 dark:border-gray-600"></th>
                </tr>
              </thead>
              <tbody>
                {fields.map((field) => (
                  <tr
                    key={field.id}
                    className="last:[&>td]:border-b-0"
                    data-testid={`wizard-field-row-${field.id}`}
                  >
                    <td className="px-3 py-2 text-sm text-foreground border-b border-border align-middle dark:text-gray-100 dark:border-gray-600">
                      <input
                        type="text"
                        className="px-2 py-1.5 text-sm w-full min-w-[120px] text-foreground bg-background border border-border rounded-md transition-colors duration-150 motion-reduce:transition-none focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/25 dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600"
                        value={field.name}
                        onChange={(e) => handleFieldChange(field.id, 'name', e.target.value)}
                        placeholder={t('fieldEditor.namePlaceholder')}
                        data-testid={`wizard-field-name-${field.id}`}
                      />
                    </td>
                    <td className="px-3 py-2 text-sm text-foreground border-b border-border align-middle dark:text-gray-100 dark:border-gray-600">
                      <input
                        type="text"
                        className="px-2 py-1.5 text-sm w-full min-w-[120px] text-foreground bg-background border border-border rounded-md transition-colors duration-150 motion-reduce:transition-none focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/25 dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600"
                        value={field.displayName}
                        onChange={(e) => handleFieldChange(field.id, 'displayName', e.target.value)}
                        placeholder={t('fieldEditor.displayNamePlaceholder')}
                        data-testid={`wizard-field-display-name-${field.id}`}
                      />
                    </td>
                    <td className="px-3 py-2 text-sm text-foreground border-b border-border align-middle dark:text-gray-100 dark:border-gray-600">
                      <select
                        className="px-2 py-1.5 text-sm min-w-[100px] text-foreground bg-background border border-border rounded-md appearance-none bg-[url('data:image/svg+xml,%3csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20fill=%27none%27%20viewBox=%270%200%2020%2020%27%3e%3cpath%20stroke=%27%236b7280%27%20stroke-linecap=%27round%27%20stroke-linejoin=%27round%27%20stroke-width=%271.5%27%20d=%27M6%208l4%204%204-4%27/%3e%3c/svg%3e')] bg-[position:right_0.25rem_center] bg-no-repeat bg-[length:1.25em_1.25em] pr-7 cursor-pointer transition-colors duration-150 motion-reduce:transition-none focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/25 dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600"
                        value={field.type}
                        onChange={(e) => handleFieldChange(field.id, 'type', e.target.value)}
                        data-testid={`wizard-field-type-${field.id}`}
                      >
                        {FIELD_TYPES.map((ft) => (
                          <option key={ft} value={ft}>
                            {ft}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="px-3 py-2 text-sm text-foreground border-b border-border align-middle dark:text-gray-100 dark:border-gray-600">
                      <input
                        type="checkbox"
                        className="w-4 h-4 accent-primary cursor-pointer"
                        checked={field.required}
                        onChange={(e) => handleFieldChange(field.id, 'required', e.target.checked)}
                        aria-label={`${field.displayName || 'Field'} required`}
                        data-testid={`wizard-field-required-${field.id}`}
                      />
                    </td>
                    <td className="px-3 py-2 text-sm text-foreground border-b border-border align-middle dark:text-gray-100 dark:border-gray-600">
                      <button
                        type="button"
                        className="flex items-center justify-center p-1 border-none bg-transparent text-muted-foreground cursor-pointer rounded transition-colors duration-150 motion-reduce:transition-none hover:text-destructive hover:bg-destructive/10 dark:text-gray-400"
                        onClick={() => handleRemoveField(field.id)}
                        aria-label={`Remove field ${field.displayName || field.name}`}
                        data-testid={`wizard-field-remove-${field.id}`}
                      >
                        <Trash2 size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    )
  }, [
    fields,
    selectedTemplate,
    t,
    handleTemplateSelect,
    handleAddField,
    handleRemoveField,
    handleFieldChange,
  ])

  // Render Step 3: Authorization
  const renderAuthorizationStep = useCallback(() => {
    return (
      <div data-testid="wizard-step-authorization">
        <h2 className="text-xl font-semibold text-foreground m-0 mb-6 dark:text-gray-100">
          {t('authorization.title')}
        </h2>
        <h3 className="text-base font-medium text-foreground m-0 mb-2 dark:text-gray-100">
          Who can access this collection?
        </h3>
        <p className="text-sm text-muted-foreground m-0 mb-6 dark:text-gray-400">
          Configure policies for each CRUD operation. This is optional and can be configured later.
        </p>

        <button
          type="button"
          className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-primary bg-primary/[0.08] border border-primary rounded-md cursor-pointer mb-6 transition-colors duration-150 motion-reduce:transition-none hover:bg-primary/15 dark:text-blue-400 dark:border-blue-400 dark:bg-primary/15 dark:hover:bg-primary/25"
          onClick={handleSkipAuth}
          data-testid="wizard-skip-auth-button"
        >
          <ChevronRight size={16} />
          Skip — Configure Later
        </button>

        <div className="flex flex-col gap-4 max-w-[500px]">
          {/* Read Policy */}
          <div className="flex flex-col gap-1">
            <label
              htmlFor="wizard-auth-read"
              className="text-sm font-medium text-foreground dark:text-gray-100"
            >
              {t('authorization.operations.read')} Policy
            </label>
            <select
              id="wizard-auth-read"
              className="px-3 py-2 text-base leading-normal text-foreground bg-background border border-border rounded-md appearance-none bg-[url('data:image/svg+xml,%3csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20fill=%27none%27%20viewBox=%270%200%2020%2020%27%3e%3cpath%20stroke=%27%236b7280%27%20stroke-linecap=%27round%27%20stroke-linejoin=%27round%27%20stroke-width=%271.5%27%20d=%27M6%208l4%204%204-4%27/%3e%3c/svg%3e')] bg-[position:right_0.5rem_center] bg-no-repeat bg-[length:1.5em_1.5em] pr-10 cursor-pointer transition-all duration-150 motion-reduce:transition-none focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/25 dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600"
              value={authorization.readPolicyId}
              onChange={(e) => handleAuthChange('readPolicyId', e.target.value)}
              data-testid="wizard-auth-read-select"
            >
              <option value="">{t('common.none')}</option>
              {policies.map((policy) => (
                <option key={policy.id} value={policy.id}>
                  {policy.name}
                </option>
              ))}
            </select>
          </div>

          {/* Create Policy */}
          <div className="flex flex-col gap-1">
            <label
              htmlFor="wizard-auth-create"
              className="text-sm font-medium text-foreground dark:text-gray-100"
            >
              {t('authorization.operations.create')} Policy
            </label>
            <select
              id="wizard-auth-create"
              className="px-3 py-2 text-base leading-normal text-foreground bg-background border border-border rounded-md appearance-none bg-[url('data:image/svg+xml,%3csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20fill=%27none%27%20viewBox=%270%200%2020%2020%27%3e%3cpath%20stroke=%27%236b7280%27%20stroke-linecap=%27round%27%20stroke-linejoin=%27round%27%20stroke-width=%271.5%27%20d=%27M6%208l4%204%204-4%27/%3e%3c/svg%3e')] bg-[position:right_0.5rem_center] bg-no-repeat bg-[length:1.5em_1.5em] pr-10 cursor-pointer transition-all duration-150 motion-reduce:transition-none focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/25 dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600"
              value={authorization.createPolicyId}
              onChange={(e) => handleAuthChange('createPolicyId', e.target.value)}
              data-testid="wizard-auth-create-select"
            >
              <option value="">{t('common.none')}</option>
              {policies.map((policy) => (
                <option key={policy.id} value={policy.id}>
                  {policy.name}
                </option>
              ))}
            </select>
          </div>

          {/* Update Policy */}
          <div className="flex flex-col gap-1">
            <label
              htmlFor="wizard-auth-update"
              className="text-sm font-medium text-foreground dark:text-gray-100"
            >
              {t('authorization.operations.update')} Policy
            </label>
            <select
              id="wizard-auth-update"
              className="px-3 py-2 text-base leading-normal text-foreground bg-background border border-border rounded-md appearance-none bg-[url('data:image/svg+xml,%3csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20fill=%27none%27%20viewBox=%270%200%2020%2020%27%3e%3cpath%20stroke=%27%236b7280%27%20stroke-linecap=%27round%27%20stroke-linejoin=%27round%27%20stroke-width=%271.5%27%20d=%27M6%208l4%204%204-4%27/%3e%3c/svg%3e')] bg-[position:right_0.5rem_center] bg-no-repeat bg-[length:1.5em_1.5em] pr-10 cursor-pointer transition-all duration-150 motion-reduce:transition-none focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/25 dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600"
              value={authorization.updatePolicyId}
              onChange={(e) => handleAuthChange('updatePolicyId', e.target.value)}
              data-testid="wizard-auth-update-select"
            >
              <option value="">{t('common.none')}</option>
              {policies.map((policy) => (
                <option key={policy.id} value={policy.id}>
                  {policy.name}
                </option>
              ))}
            </select>
          </div>

          {/* Delete Policy */}
          <div className="flex flex-col gap-1">
            <label
              htmlFor="wizard-auth-delete"
              className="text-sm font-medium text-foreground dark:text-gray-100"
            >
              {t('authorization.operations.delete')} Policy
            </label>
            <select
              id="wizard-auth-delete"
              className="px-3 py-2 text-base leading-normal text-foreground bg-background border border-border rounded-md appearance-none bg-[url('data:image/svg+xml,%3csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20fill=%27none%27%20viewBox=%270%200%2020%2020%27%3e%3cpath%20stroke=%27%236b7280%27%20stroke-linecap=%27round%27%20stroke-linejoin=%27round%27%20stroke-width=%271.5%27%20d=%27M6%208l4%204%204-4%27/%3e%3c/svg%3e')] bg-[position:right_0.5rem_center] bg-no-repeat bg-[length:1.5em_1.5em] pr-10 cursor-pointer transition-all duration-150 motion-reduce:transition-none focus:outline-none focus:border-primary focus:ring-[3px] focus:ring-primary/25 dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600"
              value={authorization.deletePolicyId}
              onChange={(e) => handleAuthChange('deletePolicyId', e.target.value)}
              data-testid="wizard-auth-delete-select"
            >
              <option value="">{t('common.none')}</option>
              {policies.map((policy) => (
                <option key={policy.id} value={policy.id}>
                  {policy.name}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>
    )
  }, [authorization, policies, t, handleAuthChange, handleSkipAuth])

  // Render Step 4: Review
  const renderReviewStep = useCallback(() => {
    return (
      <div data-testid="wizard-step-review">
        <h2 className="text-xl font-semibold text-foreground m-0 mb-6 dark:text-gray-100">
          Review & Create
        </h2>

        {isCreating ? (
          <div
            className="flex flex-col items-center justify-center gap-4 p-12"
            data-testid="wizard-creating"
          >
            <LoadingSpinner size="large" label="Creating collection..." />
            <span className="text-base text-muted-foreground dark:text-gray-400">
              Creating collection...
            </span>
          </div>
        ) : (
          <>
            {/* Collection Section */}
            <div className="mb-6 pb-6 border-b border-border dark:border-gray-700 last:border-b-0 last:mb-0 last:pb-0">
              <h3 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground m-0 mb-4 dark:text-gray-400">
                Collection
              </h3>
              <div className="grid grid-cols-[140px_1fr] gap-x-4 gap-y-2 text-sm max-sm:grid-cols-1 max-sm:gap-y-1">
                <span className="font-medium text-muted-foreground dark:text-gray-400">
                  {t('collections.displayName')}
                </span>
                <span
                  className="text-foreground break-words dark:text-gray-100"
                  data-testid="wizard-review-display-name"
                >
                  {basics.displayName}
                </span>

                <span className="font-medium text-muted-foreground max-sm:font-semibold dark:text-gray-400">
                  {t('collections.collectionName')}
                </span>
                <span
                  className="text-foreground break-words dark:text-gray-100"
                  data-testid="wizard-review-name"
                >
                  {basics.name}
                </span>

                <span className="font-medium text-muted-foreground max-sm:font-semibold dark:text-gray-400">
                  {t('collections.description')}
                </span>
                <span
                  className="text-foreground break-words dark:text-gray-100"
                  data-testid="wizard-review-description"
                >
                  {basics.description || t('common.none')}
                </span>

                <span className="font-medium text-muted-foreground max-sm:font-semibold dark:text-gray-400">
                  {t('collections.storageMode')}
                </span>
                <span
                  className="text-foreground break-words dark:text-gray-100"
                  data-testid="wizard-review-storage-mode"
                >
                  {basics.storageMode === 'JSONB'
                    ? t('collections.storageModes.jsonb')
                    : t('collections.storageModes.physicalTable')}
                </span>

                <span className="font-medium text-muted-foreground max-sm:font-semibold dark:text-gray-400">
                  {t('collections.status')}
                </span>
                <span
                  className="text-foreground break-words dark:text-gray-100"
                  data-testid="wizard-review-status"
                >
                  {basics.active ? t('collections.active') : t('collections.inactive')}
                </span>
              </div>
            </div>

            {/* Fields Section */}
            <div className="mb-6 pb-6 border-b border-border dark:border-gray-700 last:border-b-0 last:mb-0 last:pb-0">
              <h3 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground m-0 mb-4 dark:text-gray-400">
                {t('collections.fields')}
              </h3>
              {fields.length === 0 ? (
                <span
                  className="text-sm text-muted-foreground italic dark:text-gray-400"
                  data-testid="wizard-review-no-fields"
                >
                  No fields configured. Fields can be added after creation.
                </span>
              ) : (
                <table
                  className="w-full border-collapse border border-border rounded-md overflow-hidden mt-2 dark:border-gray-700"
                  data-testid="wizard-review-fields-table"
                >
                  <thead>
                    <tr>
                      <th className="px-3 py-2 text-xs font-semibold text-left uppercase tracking-wider text-muted-foreground bg-muted border-b border-border dark:text-gray-400 dark:bg-gray-700 dark:border-gray-600">
                        {t('collections.fieldName')}
                      </th>
                      <th className="px-3 py-2 text-xs font-semibold text-left uppercase tracking-wider text-muted-foreground bg-muted border-b border-border dark:text-gray-400 dark:bg-gray-700 dark:border-gray-600">
                        {t('collections.fieldType')}
                      </th>
                      <th className="px-3 py-2 text-xs font-semibold text-left uppercase tracking-wider text-muted-foreground bg-muted border-b border-border dark:text-gray-400 dark:bg-gray-700 dark:border-gray-600">
                        {t('fields.validation.required')}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {fields
                      .filter((f) => f.name && f.displayName)
                      .map((field) => (
                        <tr key={field.id} className="last:[&>td]:border-b-0">
                          <td
                            className="px-3 py-2 text-sm text-foreground border-b border-border dark:text-gray-100 dark:border-gray-600"
                            data-testid={`wizard-review-field-name-${field.id}`}
                          >
                            {field.displayName}
                            <br />
                            <small className="text-muted-foreground dark:text-gray-400">
                              {field.name}
                            </small>
                          </td>
                          <td
                            className="px-3 py-2 text-sm text-foreground border-b border-border dark:text-gray-100 dark:border-gray-600"
                            data-testid={`wizard-review-field-type-${field.id}`}
                          >
                            {field.type}
                          </td>
                          <td
                            className="px-3 py-2 text-sm text-foreground border-b border-border dark:text-gray-100 dark:border-gray-600"
                            data-testid={`wizard-review-field-required-${field.id}`}
                          >
                            {field.required ? (
                              <span className="inline-flex items-center px-1.5 py-0.5 text-[0.6875rem] font-medium rounded-full bg-primary/10 text-primary">
                                {t('fields.validation.required')}
                              </span>
                            ) : (
                              '\u2014'
                            )}
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              )}
            </div>

            {/* Authorization Section */}
            <div className="mb-6 pb-6 border-b border-border dark:border-gray-700 last:border-b-0 last:mb-0 last:pb-0">
              <h3 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground m-0 mb-4 dark:text-gray-400">
                {t('authorization.title')}
              </h3>
              {!hasAuthConfigured ? (
                <span
                  className="text-sm text-muted-foreground italic dark:text-gray-400"
                  data-testid="wizard-review-no-auth"
                >
                  Not configured — will use defaults
                </span>
              ) : (
                <div
                  className="grid grid-cols-[140px_1fr] gap-x-4 gap-y-2 text-sm max-sm:grid-cols-1 max-sm:gap-y-1"
                  data-testid="wizard-review-auth"
                >
                  <span className="font-medium text-muted-foreground dark:text-gray-400">
                    {t('authorization.operations.read')}
                  </span>
                  <span className="text-foreground dark:text-gray-100">
                    {getPolicyName(authorization.readPolicyId)}
                  </span>

                  <span className="font-medium text-muted-foreground dark:text-gray-400">
                    {t('authorization.operations.create')}
                  </span>
                  <span className="text-foreground dark:text-gray-100">
                    {getPolicyName(authorization.createPolicyId)}
                  </span>

                  <span className="font-medium text-muted-foreground dark:text-gray-400">
                    {t('authorization.operations.update')}
                  </span>
                  <span className="text-foreground dark:text-gray-100">
                    {getPolicyName(authorization.updatePolicyId)}
                  </span>

                  <span className="font-medium text-muted-foreground dark:text-gray-400">
                    {t('authorization.operations.delete')}
                  </span>
                  <span className="text-foreground dark:text-gray-100">
                    {getPolicyName(authorization.deletePolicyId)}
                  </span>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    )
  }, [basics, fields, authorization, hasAuthConfigured, isCreating, t, getPolicyName])

  // Render current step content
  const renderCurrentStep = useCallback(() => {
    switch (currentStep) {
      case 1:
        return renderBasicsStep()
      case 2:
        return renderFieldsStep()
      case 3:
        return renderAuthorizationStep()
      case 4:
        return renderReviewStep()
      default:
        return null
    }
  }, [currentStep, renderBasicsStep, renderFieldsStep, renderAuthorizationStep, renderReviewStep])

  return (
    <div className="flex flex-col h-full p-8 overflow-y-auto max-sm:p-4" data-testid={testId}>
      <header className="mb-6">
        <h1 className="text-[1.75rem] font-semibold text-foreground m-0 dark:text-gray-100">
          {t('collections.createCollection')}
        </h1>
      </header>

      {renderStepIndicator()}

      <div className="bg-card border border-border rounded-lg shadow-sm p-8 flex-1 min-h-0 overflow-y-auto dark:bg-gray-800 dark:border-gray-700 dark:shadow-md">
        {renderCurrentStep()}
      </div>

      {/* Navigation */}
      <div
        className="flex justify-between items-center mt-6 pt-6 border-t border-border dark:border-gray-700 max-sm:flex-col-reverse max-sm:gap-3"
        data-testid="wizard-navigation"
      >
        <div className="flex gap-3 max-sm:w-full">
          <button
            type="button"
            className="inline-flex items-center justify-center gap-2 px-6 py-2 text-base font-medium leading-normal text-foreground bg-muted border border-border rounded-md cursor-pointer transition-all duration-150 motion-reduce:transition-none hover:enabled:bg-accent focus:outline-none focus:ring-[3px] focus:ring-primary/25 disabled:opacity-50 disabled:cursor-not-allowed dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600 dark:hover:enabled:bg-gray-600 max-sm:w-full"
            onClick={handleCancel}
            disabled={isCreating}
            data-testid="wizard-cancel-button"
          >
            {t('common.cancel')}
          </button>
        </div>
        <div className="flex gap-3 max-sm:w-full">
          {currentStep > 1 && (
            <button
              type="button"
              className="inline-flex items-center justify-center gap-2 px-6 py-2 text-base font-medium leading-normal text-foreground bg-muted border border-border rounded-md cursor-pointer transition-all duration-150 motion-reduce:transition-none hover:enabled:bg-accent focus:outline-none focus:ring-[3px] focus:ring-primary/25 disabled:opacity-50 disabled:cursor-not-allowed dark:text-gray-100 dark:bg-gray-700 dark:border-gray-600 dark:hover:enabled:bg-gray-600 max-sm:w-full"
              onClick={handleBack}
              disabled={isCreating}
              data-testid="wizard-back-button"
            >
              <ChevronLeft size={16} />
              {t('common.back')}
            </button>
          )}
          {currentStep < 4 && (
            <button
              type="button"
              className="inline-flex items-center justify-center gap-2 px-6 py-2 text-base font-medium leading-normal text-white bg-primary border border-transparent rounded-md cursor-pointer transition-all duration-150 motion-reduce:transition-none hover:enabled:bg-primary/90 focus:outline-none focus:ring-[3px] focus:ring-primary/25 disabled:opacity-50 disabled:cursor-not-allowed max-sm:w-full"
              onClick={handleNext}
              disabled={isCreating}
              data-testid="wizard-next-button"
            >
              {t('common.next')}
              <ChevronRight size={16} />
            </button>
          )}
          {currentStep === 4 && (
            <button
              type="button"
              className="inline-flex items-center justify-center gap-2 px-6 py-2 text-base font-medium leading-normal text-white bg-green-600 border border-transparent rounded-md cursor-pointer transition-all duration-150 motion-reduce:transition-none hover:enabled:bg-green-700 focus:outline-none focus:ring-[3px] focus:ring-primary/25 disabled:opacity-50 disabled:cursor-not-allowed max-sm:w-full"
              onClick={handleCreate}
              disabled={isCreating}
              data-testid="wizard-create-button"
            >
              {isCreating ? (
                <>
                  <LoadingSpinner size="small" />
                  Creating...
                </>
              ) : (
                <>
                  <Check size={16} />
                  Create Collection
                </>
              )}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

export default CollectionWizardPage
