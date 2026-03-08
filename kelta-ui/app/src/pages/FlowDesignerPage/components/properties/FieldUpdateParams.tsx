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

interface FieldUpdateEntry {
  field: string
  value?: string
  sourceField?: string
}

interface FieldUpdateParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

const DEFAULT_UPDATE: FieldUpdateEntry = { field: '', value: '' }

export function FieldUpdateParams({ parameters, onUpdate }: FieldUpdateParamsProps) {
  const params = parameters || {}
  const updates = (params.updates as FieldUpdateEntry[]) || []

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  const updateEntry = (index: number, key: string, value: string) => {
    const updated = [...updates]
    updated[index] = { ...updated[index], [key]: value }
    if (key === 'value') {
      delete updated[index].sourceField
    } else if (key === 'sourceField') {
      delete updated[index].value
    }
    update('updates', updated)
  }

  const setEntryMode = (index: number, mode: 'literal' | 'source') => {
    const updated = [...updates]
    if (mode === 'literal') {
      delete updated[index].sourceField
      updated[index].value = updated[index].value || ''
    } else {
      delete updated[index].value
      updated[index].sourceField = updated[index].sourceField || ''
    }
    update('updates', updated)
  }

  const addEntry = () => update('updates', [...updates, { ...DEFAULT_UPDATE }])
  const removeEntry = (index: number) =>
    update(
      'updates',
      updates.filter((_, i) => i !== index)
    )

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        Field Update Config
      </span>

      {/* Updates */}
      <Accordion type="single" collapsible defaultValue="updates">
        <AccordionItem value="updates" className="border-none">
          <AccordionTrigger className="py-1.5 text-[10px] font-medium text-muted-foreground hover:no-underline">
            Updates ({updates.length})
          </AccordionTrigger>
          <AccordionContent className="flex flex-col gap-2 pb-0">
            {updates.map((entry, i) => {
              const isSourceMode = entry.sourceField !== undefined
              return (
                <div
                  key={i}
                  className="flex flex-col gap-1.5 rounded border border-border bg-background p-1.5"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] text-muted-foreground">Update #{i + 1}</span>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-5 w-5"
                      onClick={() => removeEntry(i)}
                    >
                      <Trash2 className="h-3 w-3" />
                    </Button>
                  </div>
                  <div>
                    <Label className="text-[9px]">Field Name</Label>
                    <Input
                      value={entry.field}
                      onChange={(e) => updateEntry(i, 'field', e.target.value)}
                      className="mt-0.5 h-6 text-[11px]"
                      placeholder="field_name"
                    />
                  </div>
                  <div className="flex gap-2">
                    <label className="flex items-center gap-1 text-[9px]">
                      <input
                        type="radio"
                        checked={!isSourceMode}
                        onChange={() => setEntryMode(i, 'literal')}
                        className="h-3 w-3"
                      />
                      Literal
                    </label>
                    <label className="flex items-center gap-1 text-[9px]">
                      <input
                        type="radio"
                        checked={isSourceMode}
                        onChange={() => setEntryMode(i, 'source')}
                        className="h-3 w-3"
                      />
                      From state path
                    </label>
                  </div>
                  {isSourceMode ? (
                    <div>
                      <Label className="text-[9px]">Source Field</Label>
                      <Input
                        value={entry.sourceField || ''}
                        onChange={(e) => updateEntry(i, 'sourceField', e.target.value)}
                        className="mt-0.5 h-6 text-[11px]"
                        placeholder="userId"
                      />
                    </div>
                  ) : (
                    <div>
                      <Label className="text-[9px]">Value</Label>
                      <Input
                        value={entry.value || ''}
                        onChange={(e) => updateEntry(i, 'value', e.target.value)}
                        className="mt-0.5 h-6 text-[11px]"
                        placeholder="literal value or ${$.path}"
                      />
                    </div>
                  )}
                </div>
              )
            })}
            <Button variant="outline" size="sm" className="h-6 text-[10px]" onClick={addEntry}>
              <Plus className="mr-1 h-3 w-3" />
              Add Update
            </Button>
          </AccordionContent>
        </AccordionItem>
      </Accordion>
    </div>
  )
}
