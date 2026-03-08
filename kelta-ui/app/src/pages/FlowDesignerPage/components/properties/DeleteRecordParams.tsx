import React from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface DeleteRecordParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

export function DeleteRecordParams({ parameters, onUpdate }: DeleteRecordParamsProps) {
  const params = parameters || {}
  const targetCollectionName = (params.targetCollectionName as string) || ''
  const recordIdField = (params.recordIdField as string) || ''
  const recordId = (params.recordId as string) || ''
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

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        Delete Record Config
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

      {/* Record ID Source */}
      <div>
        <Label className="text-[10px]">Record ID Source</Label>
        <div className="mt-1 flex gap-2">
          <label className="flex items-center gap-1 text-[10px]">
            <input
              type="radio"
              name="deleteRecordIdMode"
              checked={useRecordIdField}
              onChange={() => setRecordIdMode('field')}
              className="h-3 w-3"
            />
            From state path
          </label>
          <label className="flex items-center gap-1 text-[10px]">
            <input
              type="radio"
              name="deleteRecordIdMode"
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
            placeholder="${$.record.data.order_id}"
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
    </div>
  )
}
