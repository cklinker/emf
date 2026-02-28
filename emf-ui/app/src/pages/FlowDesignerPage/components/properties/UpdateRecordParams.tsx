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

interface FieldUpdate {
  field: string
  value?: string
  sourceField?: string
}

interface UpdateRecordParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

const DEFAULT_UPDATE: FieldUpdate = { field: '', value: '' }

export function UpdateRecordParams({ parameters, onUpdate }: UpdateRecordParamsProps) {
  const params = parameters || {}
  const targetCollectionName = (params.targetCollectionName as string) || ''
  const recordIdField = (params.recordIdField as string) || ''
  const recordId = (params.recordId as string) || ''
  const updates = (params.updates as FieldUpdate[]) || []
  const useRecordIdField = !!recordIdField || !recordId

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  const setRecordIdMode = (mode: 'field' | 'literal') => {
    if (mode === 'field') {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { recordId: _removed, ...rest } = params
      onUpdate({ ...rest, recordIdField: recordIdField || '' })
    } else {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { recordIdField: _removed, ...rest } = params
      onUpdate({ ...rest, recordId: recordId || '' })
    }
  }

  const updateFieldUpdate = (index: number, key: string, value: string) => {
    const updated = [...updates]
    updated[index] = { ...updated[index], [key]: value }
    // Clear the other source when one is set
    if (key === 'value') {
      delete updated[index].sourceField
    } else if (key === 'sourceField') {
      delete updated[index].value
    }
    update('updates', updated)
  }

  const setUpdateMode = (index: number, mode: 'literal' | 'source') => {
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

  const addUpdate = () => update('updates', [...updates, { ...DEFAULT_UPDATE }])
  const removeUpdate = (index: number) =>
    update(
      'updates',
      updates.filter((_, i) => i !== index)
    )

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        Update Record Config
      </span>

      <div>
        <Label className="text-[10px]">Target Collection</Label>
        <Input
          value={targetCollectionName}
          onChange={(e) => update('targetCollectionName', e.target.value)}
          className="mt-0.5 h-7 text-xs"
          placeholder="e.g. customers"
        />
      </div>

      {/* Record ID Source */}
      <div>
        <Label className="text-[10px]">Record ID Source</Label>
        <div className="mt-1 flex gap-2">
          <label className="flex items-center gap-1 text-[10px]">
            <input
              type="radio"
              name="recordIdMode"
              checked={useRecordIdField}
              onChange={() => setRecordIdMode('field')}
              className="h-3 w-3"
            />
            From state path
          </label>
          <label className="flex items-center gap-1 text-[10px]">
            <input
              type="radio"
              name="recordIdMode"
              checked={!useRecordIdField}
              onChange={() => setRecordIdMode('literal')}
              className="h-3 w-3"
            />
            Literal ID
          </label>
        </div>
        {useRecordIdField ? (
          <Input
            value={recordIdField}
            onChange={(e) => update('recordIdField', e.target.value)}
            className="mt-1 h-7 text-xs"
            placeholder="${$.record.data.customer}"
          />
        ) : (
          <Input
            value={recordId}
            onChange={(e) => update('recordId', e.target.value)}
            className="mt-1 h-7 text-xs"
            placeholder="record-uuid"
          />
        )}
      </div>

      {/* Field Updates */}
      <Accordion type="single" collapsible defaultValue="updates">
        <AccordionItem value="updates" className="border-none">
          <AccordionTrigger className="py-1.5 text-[10px] font-medium text-muted-foreground hover:no-underline">
            Field Updates ({updates.length})
          </AccordionTrigger>
          <AccordionContent className="flex flex-col gap-2 pb-0">
            {updates.map((upd, i) => {
              const isSourceMode = upd.sourceField !== undefined
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
                      onClick={() => removeUpdate(i)}
                    >
                      <Trash2 className="h-3 w-3" />
                    </Button>
                  </div>
                  <div>
                    <Label className="text-[9px]">Target Field</Label>
                    <Input
                      value={upd.field}
                      onChange={(e) => updateFieldUpdate(i, 'field', e.target.value)}
                      className="mt-0.5 h-6 text-[11px]"
                      placeholder="field_name"
                    />
                  </div>
                  <div className="flex gap-2">
                    <label className="flex items-center gap-1 text-[9px]">
                      <input
                        type="radio"
                        checked={!isSourceMode}
                        onChange={() => setUpdateMode(i, 'literal')}
                        className="h-3 w-3"
                      />
                      Literal
                    </label>
                    <label className="flex items-center gap-1 text-[9px]">
                      <input
                        type="radio"
                        checked={isSourceMode}
                        onChange={() => setUpdateMode(i, 'source')}
                        className="h-3 w-3"
                      />
                      From state path
                    </label>
                  </div>
                  {isSourceMode ? (
                    <div>
                      <Label className="text-[9px]">Source Field</Label>
                      <Input
                        value={upd.sourceField || ''}
                        onChange={(e) => updateFieldUpdate(i, 'sourceField', e.target.value)}
                        className="mt-0.5 h-6 text-[11px]"
                        placeholder="queryResult.aggregations.total_spent"
                      />
                    </div>
                  ) : (
                    <div>
                      <Label className="text-[9px]">Value</Label>
                      <Input
                        value={upd.value || ''}
                        onChange={(e) => updateFieldUpdate(i, 'value', e.target.value)}
                        className="mt-0.5 h-6 text-[11px]"
                        placeholder="literal value or ${$.path}"
                      />
                    </div>
                  )}
                </div>
              )
            })}
            <Button variant="outline" size="sm" className="h-6 text-[10px]" onClick={addUpdate}>
              <Plus className="mr-1 h-3 w-3" />
              Add Field Update
            </Button>
          </AccordionContent>
        </AccordionItem>
      </Accordion>
    </div>
  )
}
