import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import { Plus, Trash2 } from 'lucide-react'

interface FieldMapping {
  field: string
  value?: string
  sourceField?: string
}

interface CreateRecordParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

const DEFAULT_MAPPING: FieldMapping = { field: '', value: '' }

export function CreateRecordParams({ parameters, onUpdate }: CreateRecordParamsProps) {
  const params = parameters || {}
  const targetCollectionName = (params.targetCollectionName as string) || ''
  const fieldMappings = (params.fieldMappings as FieldMapping[]) || []

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  const updateMapping = (index: number, key: string, value: string) => {
    const updated = [...fieldMappings]
    updated[index] = { ...updated[index], [key]: value }
    if (key === 'value') {
      delete updated[index].sourceField
    } else if (key === 'sourceField') {
      delete updated[index].value
    }
    update('fieldMappings', updated)
  }

  const setMappingMode = (index: number, mode: 'literal' | 'source') => {
    const updated = [...fieldMappings]
    if (mode === 'literal') {
      delete updated[index].sourceField
      updated[index].value = updated[index].value || ''
    } else {
      delete updated[index].value
      updated[index].sourceField = updated[index].sourceField || ''
    }
    update('fieldMappings', updated)
  }

  const addMapping = () => update('fieldMappings', [...fieldMappings, { ...DEFAULT_MAPPING }])
  const removeMapping = (index: number) =>
    update(
      'fieldMappings',
      fieldMappings.filter((_, i) => i !== index)
    )

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        Create Record Config
      </span>

      <div>
        <Label className="text-[10px]">Target Collection Name</Label>
        <Input
          value={targetCollectionName}
          onChange={(e) => update('targetCollectionName', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="e.g. orders"
        />
      </div>

      {/* Field Mappings */}
      <Accordion type="single" collapsible defaultValue="fieldMappings">
        <AccordionItem value="fieldMappings" className="border-none">
          <AccordionTrigger className="py-1.5 text-[10px] font-medium text-muted-foreground hover:no-underline">
            Field Mappings ({fieldMappings.length})
          </AccordionTrigger>
          <AccordionContent className="flex flex-col gap-2 pb-0">
            {fieldMappings.map((mapping, i) => {
              const isSourceMode = mapping.sourceField !== undefined
              return (
                <div
                  key={i}
                  className="flex flex-col gap-1.5 rounded border border-border bg-background p-1.5"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] text-muted-foreground">Mapping #{i + 1}</span>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-5 w-5"
                      onClick={() => removeMapping(i)}
                    >
                      <Trash2 className="h-3 w-3" />
                    </Button>
                  </div>
                  <div>
                    <Label className="text-[9px]">Field Name</Label>
                    <Input
                      value={mapping.field}
                      onChange={(e) => updateMapping(i, 'field', e.target.value)}
                      className="mt-0.5 h-6 text-[11px]"
                      placeholder="field_name"
                    />
                  </div>
                  <div className="flex gap-2">
                    <label className="flex items-center gap-1 text-[9px]">
                      <input
                        type="radio"
                        checked={!isSourceMode}
                        onChange={() => setMappingMode(i, 'literal')}
                        className="h-3 w-3"
                      />
                      Literal
                    </label>
                    <label className="flex items-center gap-1 text-[9px]">
                      <input
                        type="radio"
                        checked={isSourceMode}
                        onChange={() => setMappingMode(i, 'source')}
                        className="h-3 w-3"
                      />
                      From state path
                    </label>
                  </div>
                  {isSourceMode ? (
                    <div>
                      <Label className="text-[9px]">Source Field</Label>
                      <Input
                        value={mapping.sourceField || ''}
                        onChange={(e) => updateMapping(i, 'sourceField', e.target.value)}
                        className="mt-0.5 h-6 text-[11px]"
                        placeholder="record.data.customer"
                      />
                    </div>
                  ) : (
                    <div>
                      <Label className="text-[9px]">Value</Label>
                      <Input
                        value={mapping.value || ''}
                        onChange={(e) => updateMapping(i, 'value', e.target.value)}
                        className="mt-0.5 h-6 text-[11px]"
                        placeholder="literal value or ${$.path}"
                      />
                    </div>
                  )}
                </div>
              )
            })}
            <Button variant="outline" size="sm" className="h-6 text-[10px]" onClick={addMapping}>
              <Plus className="mr-1 h-3 w-3" />
              Add Field Mapping
            </Button>
          </AccordionContent>
        </AccordionItem>
      </Accordion>
    </div>
  )
}
