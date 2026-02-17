/**
 * ObjectFormPage
 *
 * Create or edit a record using a schema-driven form.
 * Renders input fields for each field in the collection schema,
 * with type-appropriate form controls.
 *
 * Features:
 * - Schema-driven form field generation
 * - Create and edit modes
 * - JSON:API request wrapping via useRecordMutation
 * - Cancel/Save actions
 * - Loading states
 */

import React, { useState, useCallback, useMemo } from 'react'
import { useNavigate, useParams, Link } from 'react-router-dom'
import { Save, X, Loader2, AlertCircle } from 'lucide-react'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { Separator } from '@/components/ui/separator'
import { useCollectionSchema } from '@/hooks/useCollectionSchema'
import { useRecord } from '@/hooks/useRecord'
import { useRecordMutation } from '@/hooks/useRecordMutation'
import type { FieldDefinition, FieldType } from '@/hooks/useCollectionSchema'

/** System fields excluded from forms */
const SYSTEM_FIELDS = new Set([
  'id',
  'createdAt',
  'updatedAt',
  'createdBy',
  'updatedBy',
  'created_at',
  'updated_at',
  'created_by',
  'updated_by',
])

/** Read-only field types that should not be editable */
const READ_ONLY_TYPES: Set<FieldType> = new Set(['auto_number', 'formula', 'rollup_summary'])

/**
 * Get the HTML input type for a field type.
 */
function getInputType(fieldType: FieldType): string {
  switch (fieldType) {
    case 'number':
    case 'currency':
    case 'percent':
      return 'number'
    case 'date':
      return 'date'
    case 'datetime':
      return 'datetime-local'
    case 'email':
      return 'email'
    case 'phone':
      return 'tel'
    case 'url':
      return 'url'
    default:
      return 'text'
  }
}

/**
 * Check if a field should use a textarea instead of an input.
 */
function isTextareaField(fieldType: FieldType): boolean {
  return fieldType === 'rich_text' || fieldType === 'json'
}

interface FormFieldProps {
  field: FieldDefinition
  value: unknown
  onChange: (name: string, value: unknown) => void
}

/**
 * Renders the appropriate form control for a field type.
 */
function FormField({ field, value, onChange }: FormFieldProps): React.ReactElement {
  const isReadOnly = READ_ONLY_TYPES.has(field.type)
  const fieldId = `field-${field.name}`

  // Boolean fields use a checkbox
  if (field.type === 'boolean') {
    return (
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <Checkbox
            id={fieldId}
            checked={Boolean(value)}
            onCheckedChange={(checked) => onChange(field.name, checked)}
            disabled={isReadOnly}
          />
          <Label htmlFor={fieldId} className="text-sm font-medium">
            {field.displayName || field.name}
            {field.required && <span className="ml-1 text-destructive">*</span>}
          </Label>
        </div>
      </div>
    )
  }

  // Textarea fields (rich_text, json)
  if (isTextareaField(field.type)) {
    return (
      <div className="space-y-2">
        <Label htmlFor={fieldId} className="text-sm font-medium">
          {field.displayName || field.name}
          {field.required && <span className="ml-1 text-destructive">*</span>}
        </Label>
        <Textarea
          id={fieldId}
          value={value != null ? String(value) : ''}
          onChange={(e) => onChange(field.name, e.target.value)}
          disabled={isReadOnly}
          rows={field.type === 'json' ? 6 : 4}
          className={field.type === 'json' ? 'font-mono text-sm' : ''}
          placeholder={field.type === 'json' ? '{}' : ''}
        />
      </div>
    )
  }

  // Encrypted fields show masked value
  if (field.type === 'encrypted') {
    return (
      <div className="space-y-2">
        <Label htmlFor={fieldId} className="text-sm font-medium">
          {field.displayName || field.name}
          {field.required && <span className="ml-1 text-destructive">*</span>}
        </Label>
        <Input
          id={fieldId}
          type="password"
          value={value != null ? String(value) : ''}
          onChange={(e) => onChange(field.name, e.target.value)}
        />
      </div>
    )
  }

  // Default: text input with appropriate type
  const inputType = getInputType(field.type)
  const step = field.type === 'currency' ? '0.01' : field.type === 'percent' ? '0.01' : undefined

  return (
    <div className="space-y-2">
      <Label htmlFor={fieldId} className="text-sm font-medium">
        {field.displayName || field.name}
        {field.required && <span className="ml-1 text-destructive">*</span>}
      </Label>
      <Input
        id={fieldId}
        type={inputType}
        value={value != null ? String(value) : ''}
        onChange={(e) => {
          const newValue =
            inputType === 'number'
              ? e.target.value === ''
                ? ''
                : Number(e.target.value)
              : e.target.value
          onChange(field.name, newValue)
        }}
        disabled={isReadOnly}
        step={step}
        required={field.required}
      />
    </div>
  )
}

/**
 * Compute initial form data from record (edit) or field defaults (create).
 */
function computeInitialFormData(
  isNew: boolean,
  record: Record<string, unknown> | undefined,
  fields: FieldDefinition[]
): Record<string, unknown> {
  if (!isNew && record) {
    return { ...record }
  }
  const defaults: Record<string, unknown> = {}
  for (const field of fields) {
    if (field.type === 'boolean') {
      defaults[field.name] = false
    }
  }
  return defaults
}

interface ObjectFormBodyProps {
  isNew: boolean
  initialData: Record<string, unknown>
  fields: FieldDefinition[]
  collectionName: string
  collectionLabel: string
  recordId?: string
  basePath: string
}

/**
 * Inner form body — receives initialData so useState initializer runs
 * with the correct value on mount. Parent uses `key` to force remount
 * when the data source changes.
 */
function ObjectFormBody({
  isNew,
  initialData,
  fields,
  collectionName,
  collectionLabel,
  recordId,
  basePath,
}: ObjectFormBodyProps): React.ReactElement {
  const navigate = useNavigate()
  const [formData, setFormData] = useState<Record<string, unknown>>(initialData)

  // Editable fields (exclude system and read-only)
  const editableFields = useMemo(() => {
    return fields.filter((f) => !SYSTEM_FIELDS.has(f.name) && !READ_ONLY_TYPES.has(f.type))
  }, [fields])

  // Mutations
  const mutations = useRecordMutation({
    collectionName,
    onSuccess: () => {
      if (isNew) {
        navigate(`${basePath}/o/${collectionName}`)
      } else {
        navigate(`${basePath}/o/${collectionName}/${recordId}`)
      }
    },
  })

  const pageTitle = isNew ? `New ${collectionLabel}` : `Edit ${collectionLabel}`

  // Handle field change
  const handleFieldChange = useCallback((name: string, value: unknown) => {
    setFormData((prev) => ({ ...prev, [name]: value }))
  }, [])

  // Handle save
  const handleSave = useCallback(() => {
    const attributes: Record<string, unknown> = {}
    for (const field of editableFields) {
      const value = formData[field.name]
      if (value !== undefined && value !== '') {
        attributes[field.name] = value
      }
    }

    if (isNew) {
      mutations.create.mutate(attributes)
    } else {
      mutations.update.mutate({ id: recordId!, data: attributes })
    }
  }, [editableFields, formData, isNew, mutations, recordId])

  // Handle cancel
  const handleCancel = useCallback(() => {
    if (recordId) {
      navigate(`${basePath}/o/${collectionName}/${recordId}`)
    } else {
      navigate(`${basePath}/o/${collectionName}`)
    }
  }, [navigate, basePath, collectionName, recordId])

  const isSaving = mutations.create.isPending || mutations.update.isPending

  return (
    <div className="space-y-4 p-6">
      {/* Breadcrumb */}
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link to={`${basePath}/home`}>Home</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link to={`${basePath}/o/${collectionName}`}>{collectionLabel}</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{isNew ? 'New' : 'Edit'}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* Form header */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold tracking-tight text-foreground">{pageTitle}</h1>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="outline" onClick={handleCancel} disabled={isSaving}>
            <X className="mr-1.5 h-3.5 w-3.5" />
            Cancel
          </Button>
          <Button size="sm" onClick={handleSave} disabled={isSaving}>
            {isSaving ? (
              <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
            ) : (
              <Save className="mr-1.5 h-3.5 w-3.5" />
            )}
            {isSaving ? 'Saving...' : 'Save'}
          </Button>
        </div>
      </div>

      <Separator />

      {/* Form Fields */}
      <Card>
        <CardHeader className="py-3">
          <CardTitle className="text-sm font-medium">{collectionLabel} Information</CardTitle>
        </CardHeader>
        <CardContent>
          {editableFields.length === 0 ? (
            <p className="text-sm text-muted-foreground">No editable fields in this collection.</p>
          ) : (
            <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
              {editableFields.map((field) => (
                <FormField
                  key={field.name}
                  field={field}
                  value={formData[field.name]}
                  onChange={handleFieldChange}
                />
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

export function ObjectFormPage(): React.ReactElement {
  const {
    tenantSlug,
    collection: collectionName,
    id: recordId,
  } = useParams<{
    tenantSlug: string
    collection: string
    id?: string
  }>()
  const basePath = `/${tenantSlug}/app`
  const isNew = !recordId
  const navigate = useNavigate()

  // Fetch collection schema
  const {
    schema,
    fields,
    isLoading: schemaLoading,
    error: schemaError,
  } = useCollectionSchema(collectionName)

  // Fetch existing record for edit mode
  const { record, isLoading: recordLoading } = useRecord({
    collectionName,
    recordId,
    enabled: !isNew,
  })

  const isLoading = schemaLoading || (!isNew && recordLoading)

  // Collection label
  const collectionLabel =
    schema?.displayName ||
    (collectionName ? collectionName.charAt(0).toUpperCase() + collectionName.slice(1) : 'Object')

  // Compute initial data and a key that changes when the data source changes.
  // The key forces ObjectFormBody to remount, running useState with fresh initialData.
  const initialData = useMemo(
    () => computeInitialFormData(isNew, record, fields),
    [isNew, record, fields]
  )
  const formKey = isNew ? `new:${fields.length}` : `edit:${recordId}:${record?.id ?? 'loading'}`

  // Handle cancel (needed for error state)
  const handleCancel = useCallback(() => {
    if (recordId) {
      navigate(`${basePath}/o/${collectionName}/${recordId}`)
    } else {
      navigate(`${basePath}/o/${collectionName}`)
    }
  }, [navigate, basePath, collectionName, recordId])

  // Loading state
  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  // Error state
  if (schemaError) {
    return (
      <div className="space-y-4 p-6">
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Error</AlertTitle>
          <AlertDescription>
            {schemaError.message || 'Failed to load collection schema.'}
          </AlertDescription>
        </Alert>
        <Button variant="outline" onClick={handleCancel}>
          Go back
        </Button>
      </div>
    )
  }

  return (
    <ObjectFormBody
      key={formKey}
      isNew={isNew}
      initialData={initialData}
      fields={fields}
      collectionName={collectionName || ''}
      collectionLabel={collectionLabel}
      recordId={recordId}
      basePath={basePath}
    />
  )
}
