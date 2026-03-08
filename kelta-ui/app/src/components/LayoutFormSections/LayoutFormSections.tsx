/**
 * LayoutFormSections Component
 *
 * Renders form fields organized by a page layout's sections.
 * Each section becomes a collapsible card with the configured
 * column count. Fields are rendered via the parent's renderField
 * callback so all existing form field logic (inputs, validation,
 * picklists, lookups, custom renderers) is reused unchanged.
 *
 * Layout-level overrides applied:
 * - labelOverride → replaces displayName for the form label
 * - readOnlyOnLayout → disables the field input
 * - requiredOnLayout → adds required indicator even if schema says optional
 */

import React, { useState, useMemo } from 'react'
import { ChevronDown } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { cn } from '@/lib/utils'
import type { LayoutSectionDto, LayoutFieldPlacementDto } from '@/hooks/usePageLayout'

export interface LayoutFormFieldDefinition {
  id: string
  name: string
  displayName?: string
  type: string
  required: boolean
  [key: string]: unknown
}

export interface LayoutFormSectionsProps {
  /** Sections from the resolved page layout */
  sections: LayoutSectionDto[]
  /** Full collection schema fields for resolving placements */
  schemaFields: LayoutFormFieldDefinition[]
  /** Render callback for a single form field — reuses the parent's renderField */
  renderField: (field: LayoutFormFieldDefinition, index: number) => React.ReactNode
}

/**
 * Resolves layout placements to schema fields, applying layout overrides.
 */
function resolvePlacements(
  placements: LayoutFieldPlacementDto[],
  fieldsByName: Map<string, LayoutFormFieldDefinition>,
  fieldsById: Map<string, LayoutFormFieldDefinition>
): LayoutFormFieldDefinition[] {
  const sorted = [...placements].sort((a, b) => a.sortOrder - b.sortOrder)

  return sorted
    .map((placement) => {
      const schemaField = fieldsById.get(placement.fieldId) || fieldsByName.get(placement.fieldName)
      if (!schemaField) return null

      return {
        ...schemaField,
        displayName: placement.labelOverride || schemaField.displayName || schemaField.name,
        required: placement.requiredOnLayout || schemaField.required,
      }
    })
    .filter((f): f is LayoutFormFieldDefinition => f !== null)
}

function FormSection({
  section,
  fields,
  renderField,
  startIndex,
}: {
  section: LayoutSectionDto
  fields: LayoutFormFieldDefinition[]
  renderField: (field: LayoutFormFieldDefinition, index: number) => React.ReactNode
  startIndex: number
}) {
  const [isOpen, setIsOpen] = useState(!section.collapsed)
  const columns = (section.columns as 1 | 2 | 3) || 1

  if (fields.length === 0) return null

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <Card>
        <CollapsibleTrigger asChild>
          <CardHeader className="cursor-pointer select-none py-3">
            <CardTitle className="flex items-center gap-2 text-sm font-medium">
              <ChevronDown
                className={cn(
                  'h-4 w-4 text-muted-foreground transition-transform',
                  isOpen && 'rotate-0',
                  !isOpen && '-rotate-90'
                )}
              />
              {section.heading || 'Details'}
              <span className="text-xs font-normal text-muted-foreground">
                ({fields.length} field{fields.length !== 1 ? 's' : ''})
              </span>
            </CardTitle>
          </CardHeader>
        </CollapsibleTrigger>

        <CollapsibleContent>
          <CardContent className="pt-0">
            <div
              className={cn(
                'grid grid-cols-1 gap-6',
                columns === 2 && 'md:grid-cols-2',
                columns >= 3 && 'md:grid-cols-2 lg:grid-cols-3'
              )}
            >
              {fields.map((field, i) => renderField(field, startIndex + i))}
            </div>
          </CardContent>
        </CollapsibleContent>
      </Card>
    </Collapsible>
  )
}

export function LayoutFormSections({
  sections,
  schemaFields,
  renderField,
}: LayoutFormSectionsProps): React.ReactElement {
  const { fieldsByName, fieldsById } = useMemo(() => {
    const byName = new Map<string, LayoutFormFieldDefinition>()
    const byId = new Map<string, LayoutFormFieldDefinition>()
    for (const field of schemaFields) {
      byName.set(field.name, field)
      byId.set(field.id, field)
    }
    return { fieldsByName: byName, fieldsById: byId }
  }, [schemaFields])

  // Pre-compute resolved fields and start indices for each section
  const resolvedSections = useMemo(() => {
    const sorted = [...sections].sort((a, b) => a.sortOrder - b.sortOrder)
    const result: {
      section: LayoutSectionDto
      fields: LayoutFormFieldDefinition[]
      startIndex: number
    }[] = []
    sorted.reduce((acc, section) => {
      const fields = resolvePlacements(section.fields, fieldsByName, fieldsById)
      result.push({ section, fields, startIndex: acc })
      return acc + fields.length
    }, 0)
    return result
  }, [sections, fieldsByName, fieldsById])

  return (
    <div className="flex flex-col gap-4">
      {resolvedSections.map(({ section, fields, startIndex }) => (
        <FormSection
          key={section.id}
          section={section}
          fields={fields}
          renderField={renderField}
          startIndex={startIndex}
        />
      ))}
    </div>
  )
}
