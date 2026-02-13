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
import { getTenantId } from '../../hooks'
import { useApi } from '../../context/ApiContext'
import { useToast } from '../../components/Toast'
import { LoadingSpinner } from '../../components'
import styles from './CollectionWizardPage.module.css'

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
  serviceId: string
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
  serviceId?: string
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
    serviceId: '',
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

  // Fetch services
  const { data: servicesData, isLoading: servicesLoading } = useQuery({
    queryKey: ['services'],
    queryFn: () =>
      apiClient.get<{ content: Array<{ id: string; name: string }> }>('/control/services?size=100'),
  })

  // Fetch policies
  const { data: policiesData } = useQuery({
    queryKey: ['policies'],
    queryFn: () =>
      apiClient.get<Array<{ id: string; name: string; description?: string }>>(
        '/control/policies?size=100'
      ),
  })

  const services = useMemo(() => servicesData?.content ?? [], [servicesData])
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

  const handleServiceChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setBasics((prev) => ({ ...prev, serviceId: e.target.value }))
    setBasicsErrors((prev) => ({ ...prev, serviceId: undefined }))
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
    if (!basics.serviceId) {
      errors.serviceId = t('collectionForm.validation.serviceRequired')
    }
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
        serviceId: basics.serviceId,
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
            tenantId: getTenantId(),
          })
        }

        if (authorization.createPolicyId) {
          await apiClient.post('/control/authorization/route-policies', {
            method: 'POST',
            pathPattern: `/gateway/${collectionPath}`,
            policyId: authorization.createPolicyId,
            tenantId: getTenantId(),
          })
        }

        if (authorization.updatePolicyId) {
          await apiClient.post('/control/authorization/route-policies', {
            method: 'PUT',
            pathPattern: `/gateway/${collectionPath}/**`,
            policyId: authorization.updatePolicyId,
            tenantId: getTenantId(),
          })
        }

        if (authorization.deletePolicyId) {
          await apiClient.post('/control/authorization/route-policies', {
            method: 'DELETE',
            pathPattern: `/gateway/${collectionPath}/**`,
            policyId: authorization.deletePolicyId,
            tenantId: getTenantId(),
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

  // Get service name by ID
  const getServiceName = useCallback(
    (serviceId: string): string => {
      const service = services.find((s) => s.id === serviceId)
      return service ? service.name : serviceId
    },
    [services]
  )

  // Render step indicator
  const renderStepIndicator = useCallback(() => {
    return (
      <div className={styles.stepIndicator} data-testid="wizard-step-indicator">
        {STEPS.map((step, index) => {
          const isCompleted = step.number < currentStep
          const isCurrent = step.number === currentStep
          const isFuture = step.number > currentStep

          return (
            <React.Fragment key={step.number}>
              <div className={styles.stepItem} data-testid={`wizard-step-${step.number}`}>
                <div
                  className={`${styles.stepCircle} ${
                    isCompleted
                      ? styles.stepCircleCompleted
                      : isCurrent
                        ? styles.stepCircleCurrent
                        : styles.stepCircleFuture
                  }`}
                  data-testid={`wizard-step-circle-${step.number}`}
                >
                  {isCompleted ? <Check size={14} /> : step.number}
                </div>
                <span
                  className={`${styles.stepLabel} ${
                    isCompleted
                      ? styles.stepLabelCompleted
                      : isCurrent
                        ? styles.stepLabelCurrent
                        : ''
                  }`}
                >
                  {step.label}
                </span>
              </div>
              {index < STEPS.length - 1 && (
                <div className={`${styles.stepLine} ${isFuture ? '' : styles.stepLineCompleted}`} />
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
        <h2 className={styles.stepTitle}>{t('collections.createCollection')}</h2>
        <div className={styles.formGrid}>
          {/* Service */}
          <div className={styles.fieldGroup}>
            <label htmlFor="wizard-service" className={styles.label}>
              {t('collections.service')}
              <span className={styles.required} aria-hidden="true">
                *
              </span>
            </label>
            <select
              id="wizard-service"
              className={`${styles.select} ${basicsErrors.serviceId ? styles.inputError : ''}`}
              value={basics.serviceId}
              onChange={handleServiceChange}
              disabled={servicesLoading}
              aria-required="true"
              aria-invalid={!!basicsErrors.serviceId}
              data-testid="wizard-service-select"
            >
              <option value="">
                {servicesLoading ? t('common.loading') : t('collections.selectService')}
              </option>
              {services.map((service) => (
                <option key={service.id} value={service.id}>
                  {service.name}
                </option>
              ))}
            </select>
            {basicsErrors.serviceId && (
              <span className={styles.errorText} role="alert" data-testid="wizard-service-error">
                {basicsErrors.serviceId}
              </span>
            )}
          </div>

          {/* Display Name */}
          <div className={styles.fieldGroup}>
            <label htmlFor="wizard-display-name" className={styles.label}>
              {t('collections.displayName')}
              <span className={styles.required} aria-hidden="true">
                *
              </span>
            </label>
            <input
              id="wizard-display-name"
              type="text"
              className={`${styles.input} ${basicsErrors.displayName ? styles.inputError : ''}`}
              value={basics.displayName}
              onChange={handleDisplayNameChange}
              placeholder={t('collectionForm.displayNamePlaceholder')}
              aria-required="true"
              aria-invalid={!!basicsErrors.displayName}
              data-testid="wizard-display-name-input"
            />
            {basicsErrors.displayName && (
              <span
                className={styles.errorText}
                role="alert"
                data-testid="wizard-display-name-error"
              >
                {basicsErrors.displayName}
              </span>
            )}
          </div>

          {/* Collection Name (auto-generated) */}
          <div className={styles.fieldGroup}>
            <label htmlFor="wizard-name" className={styles.label}>
              {t('collections.collectionName')}
              <span className={styles.required} aria-hidden="true">
                *
              </span>
            </label>
            <input
              id="wizard-name"
              type="text"
              className={`${styles.input} ${basicsErrors.name ? styles.inputError : ''}`}
              value={basics.name}
              onChange={handleNameChange}
              placeholder={t('collectionForm.namePlaceholder')}
              aria-required="true"
              aria-invalid={!!basicsErrors.name}
              data-testid="wizard-name-input"
            />
            {basicsErrors.name && (
              <span className={styles.errorText} role="alert" data-testid="wizard-name-error">
                {basicsErrors.name}
              </span>
            )}
            <span className={styles.hint}>{t('collectionForm.nameHint')}</span>
          </div>

          {/* Description */}
          <div className={styles.fieldGroup}>
            <label htmlFor="wizard-description" className={styles.label}>
              {t('collections.description')}
              <span className={styles.optional}>({t('common.optional')})</span>
            </label>
            <textarea
              id="wizard-description"
              className={styles.textarea}
              value={basics.description}
              onChange={handleDescriptionChange}
              placeholder={t('collectionForm.descriptionPlaceholder')}
              rows={3}
              data-testid="wizard-description-input"
            />
          </div>

          {/* Storage Mode */}
          <div className={styles.fieldGroup}>
            <label htmlFor="wizard-storage-mode" className={styles.label}>
              {t('collections.storageMode')}
            </label>
            <select
              id="wizard-storage-mode"
              className={styles.select}
              value={basics.storageMode}
              onChange={handleStorageModeChange}
              data-testid="wizard-storage-mode-select"
            >
              <option value="JSONB">{t('collections.storageModes.jsonb')}</option>
              <option value="PHYSICAL_TABLE">{t('collections.storageModes.physicalTable')}</option>
            </select>
            <span className={styles.hint}>{t('collectionForm.storageModeHint')}</span>
          </div>

          {/* Active Toggle */}
          <div className={styles.fieldGroup}>
            <div className={styles.checkboxGroup}>
              <input
                id="wizard-active"
                type="checkbox"
                className={styles.checkbox}
                checked={basics.active}
                onChange={handleActiveChange}
                data-testid="wizard-active-checkbox"
              />
              <label htmlFor="wizard-active" className={styles.checkboxLabel}>
                {t('collections.active')}
              </label>
            </div>
            <span className={styles.hint}>{t('collectionForm.activeHint')}</span>
          </div>
        </div>
      </div>
    )
  }, [
    basics,
    basicsErrors,
    services,
    servicesLoading,
    t,
    handleDisplayNameChange,
    handleNameChange,
    handleServiceChange,
    handleDescriptionChange,
    handleStorageModeChange,
    handleActiveChange,
  ])

  // Render Step 2: Fields
  const renderFieldsStep = useCallback(() => {
    return (
      <div data-testid="wizard-step-fields">
        <h2 className={styles.stepTitle}>{t('collections.fields')}</h2>

        {/* Template Selection */}
        <div className={styles.templateSection}>
          <p className={styles.templateSectionTitle}>Choose a template</p>
          <div className={styles.templateGrid}>
            {Object.entries(TEMPLATES).map(([key, template]) => (
              <button
                key={key}
                type="button"
                className={`${styles.templateCard} ${
                  selectedTemplate === key ? styles.templateCardSelected : ''
                }`}
                onClick={() => handleTemplateSelect(key)}
                data-testid={`wizard-template-${key}`}
              >
                <div className={styles.templateIcon}>{getTemplateIcon(template.icon)}</div>
                <span className={styles.templateName}>{template.name}</span>
                <span className={styles.templateFieldCount}>
                  {template.fields.length === 0 ? 'No fields' : `${template.fields.length} fields`}
                </span>
              </button>
            ))}
          </div>
        </div>

        {/* Fields Table */}
        <div className={styles.fieldsSection}>
          <div className={styles.fieldsSectionHeader}>
            <h3 className={styles.fieldsSectionTitle}>{t('collections.fields')}</h3>
            <button
              type="button"
              className={styles.addFieldButton}
              onClick={handleAddField}
              data-testid="wizard-add-field-button"
            >
              <Plus size={14} />
              {t('collections.addField')}
            </button>
          </div>

          {fields.length === 0 ? (
            <div className={styles.noFieldsMessage} data-testid="wizard-no-fields">
              {t('fieldsPanel.noFields')}. {t('fieldsPanel.addFieldHint')}
            </div>
          ) : (
            <table className={styles.fieldsTable} data-testid="wizard-fields-table">
              <thead>
                <tr>
                  <th>{t('collections.fieldName')}</th>
                  <th>{t('collections.displayName')}</th>
                  <th>{t('collections.fieldType')}</th>
                  <th>{t('fields.validation.required')}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {fields.map((field) => (
                  <tr key={field.id} data-testid={`wizard-field-row-${field.id}`}>
                    <td>
                      <input
                        type="text"
                        className={styles.fieldInput}
                        value={field.name}
                        onChange={(e) => handleFieldChange(field.id, 'name', e.target.value)}
                        placeholder={t('fieldEditor.namePlaceholder')}
                        data-testid={`wizard-field-name-${field.id}`}
                      />
                    </td>
                    <td>
                      <input
                        type="text"
                        className={styles.fieldInput}
                        value={field.displayName}
                        onChange={(e) => handleFieldChange(field.id, 'displayName', e.target.value)}
                        placeholder={t('fieldEditor.displayNamePlaceholder')}
                        data-testid={`wizard-field-display-name-${field.id}`}
                      />
                    </td>
                    <td>
                      <select
                        className={styles.fieldSelect}
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
                    <td>
                      <input
                        type="checkbox"
                        className={styles.fieldCheckbox}
                        checked={field.required}
                        onChange={(e) => handleFieldChange(field.id, 'required', e.target.checked)}
                        aria-label={`${field.displayName || 'Field'} required`}
                        data-testid={`wizard-field-required-${field.id}`}
                      />
                    </td>
                    <td>
                      <button
                        type="button"
                        className={styles.removeFieldButton}
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
        <h2 className={styles.stepTitle}>{t('authorization.title')}</h2>
        <h3 className={styles.authHeader}>Who can access this collection?</h3>
        <p className={styles.authDescription}>
          Configure policies for each CRUD operation. This is optional and can be configured later.
        </p>

        <button
          type="button"
          className={styles.skipButton}
          onClick={handleSkipAuth}
          data-testid="wizard-skip-auth-button"
        >
          <ChevronRight size={16} />
          Skip — Configure Later
        </button>

        <div className={styles.authGrid}>
          {/* Read Policy */}
          <div className={styles.authFieldGroup}>
            <label htmlFor="wizard-auth-read" className={styles.authLabel}>
              {t('authorization.operations.read')} Policy
            </label>
            <select
              id="wizard-auth-read"
              className={styles.select}
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
          <div className={styles.authFieldGroup}>
            <label htmlFor="wizard-auth-create" className={styles.authLabel}>
              {t('authorization.operations.create')} Policy
            </label>
            <select
              id="wizard-auth-create"
              className={styles.select}
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
          <div className={styles.authFieldGroup}>
            <label htmlFor="wizard-auth-update" className={styles.authLabel}>
              {t('authorization.operations.update')} Policy
            </label>
            <select
              id="wizard-auth-update"
              className={styles.select}
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
          <div className={styles.authFieldGroup}>
            <label htmlFor="wizard-auth-delete" className={styles.authLabel}>
              {t('authorization.operations.delete')} Policy
            </label>
            <select
              id="wizard-auth-delete"
              className={styles.select}
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
        <h2 className={styles.stepTitle}>Review & Create</h2>

        {isCreating ? (
          <div className={styles.creatingOverlay} data-testid="wizard-creating">
            <LoadingSpinner size="large" label="Creating collection..." />
            <span className={styles.creatingText}>Creating collection...</span>
          </div>
        ) : (
          <>
            {/* Collection Section */}
            <div className={styles.reviewSection}>
              <h3 className={styles.reviewSectionTitle}>Collection</h3>
              <div className={styles.reviewGrid}>
                <span className={styles.reviewLabel}>{t('collections.displayName')}</span>
                <span className={styles.reviewValue} data-testid="wizard-review-display-name">
                  {basics.displayName}
                </span>

                <span className={styles.reviewLabel}>{t('collections.collectionName')}</span>
                <span className={styles.reviewValue} data-testid="wizard-review-name">
                  {basics.name}
                </span>

                <span className={styles.reviewLabel}>{t('collections.service')}</span>
                <span className={styles.reviewValue} data-testid="wizard-review-service">
                  {getServiceName(basics.serviceId)}
                </span>

                <span className={styles.reviewLabel}>{t('collections.description')}</span>
                <span className={styles.reviewValue} data-testid="wizard-review-description">
                  {basics.description || t('common.none')}
                </span>

                <span className={styles.reviewLabel}>{t('collections.storageMode')}</span>
                <span className={styles.reviewValue} data-testid="wizard-review-storage-mode">
                  {basics.storageMode === 'JSONB'
                    ? t('collections.storageModes.jsonb')
                    : t('collections.storageModes.physicalTable')}
                </span>

                <span className={styles.reviewLabel}>{t('collections.status')}</span>
                <span className={styles.reviewValue} data-testid="wizard-review-status">
                  {basics.active ? t('collections.active') : t('collections.inactive')}
                </span>
              </div>
            </div>

            {/* Fields Section */}
            <div className={styles.reviewSection}>
              <h3 className={styles.reviewSectionTitle}>{t('collections.fields')}</h3>
              {fields.length === 0 ? (
                <span className={styles.reviewNotConfigured} data-testid="wizard-review-no-fields">
                  No fields configured. Fields can be added after creation.
                </span>
              ) : (
                <table
                  className={styles.reviewFieldsTable}
                  data-testid="wizard-review-fields-table"
                >
                  <thead>
                    <tr>
                      <th>{t('collections.fieldName')}</th>
                      <th>{t('collections.fieldType')}</th>
                      <th>{t('fields.validation.required')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {fields
                      .filter((f) => f.name && f.displayName)
                      .map((field) => (
                        <tr key={field.id}>
                          <td data-testid={`wizard-review-field-name-${field.id}`}>
                            {field.displayName}
                            <br />
                            <small style={{ color: 'var(--color-text-secondary, #6b7280)' }}>
                              {field.name}
                            </small>
                          </td>
                          <td data-testid={`wizard-review-field-type-${field.id}`}>{field.type}</td>
                          <td data-testid={`wizard-review-field-required-${field.id}`}>
                            {field.required ? (
                              <span className={styles.requiredBadge}>
                                {t('fields.validation.required')}
                              </span>
                            ) : (
                              '—'
                            )}
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              )}
            </div>

            {/* Authorization Section */}
            <div className={styles.reviewSection}>
              <h3 className={styles.reviewSectionTitle}>{t('authorization.title')}</h3>
              {!hasAuthConfigured ? (
                <span className={styles.reviewNotConfigured} data-testid="wizard-review-no-auth">
                  Not configured — will use defaults
                </span>
              ) : (
                <div className={styles.reviewGrid} data-testid="wizard-review-auth">
                  <span className={styles.reviewLabel}>{t('authorization.operations.read')}</span>
                  <span className={styles.reviewValue}>
                    {getPolicyName(authorization.readPolicyId)}
                  </span>

                  <span className={styles.reviewLabel}>{t('authorization.operations.create')}</span>
                  <span className={styles.reviewValue}>
                    {getPolicyName(authorization.createPolicyId)}
                  </span>

                  <span className={styles.reviewLabel}>{t('authorization.operations.update')}</span>
                  <span className={styles.reviewValue}>
                    {getPolicyName(authorization.updatePolicyId)}
                  </span>

                  <span className={styles.reviewLabel}>{t('authorization.operations.delete')}</span>
                  <span className={styles.reviewValue}>
                    {getPolicyName(authorization.deletePolicyId)}
                  </span>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    )
  }, [
    basics,
    fields,
    authorization,
    hasAuthConfigured,
    isCreating,
    t,
    getServiceName,
    getPolicyName,
  ])

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
    <div className={styles.container} data-testid={testId}>
      <header className={styles.header}>
        <h1 className={styles.title}>{t('collections.createCollection')}</h1>
      </header>

      {renderStepIndicator()}

      <div className={styles.stepContent}>{renderCurrentStep()}</div>

      {/* Navigation */}
      <div className={styles.navigationActions} data-testid="wizard-navigation">
        <div className={styles.navLeft}>
          <button
            type="button"
            className={styles.cancelButton}
            onClick={handleCancel}
            disabled={isCreating}
            data-testid="wizard-cancel-button"
          >
            {t('common.cancel')}
          </button>
        </div>
        <div className={styles.navRight}>
          {currentStep > 1 && (
            <button
              type="button"
              className={styles.backButton}
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
              className={styles.nextButton}
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
              className={styles.createButton}
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
