import React from 'react'
import { FieldLabel } from '@/components/kelta'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'

interface SqlQueryParamsProps {
  parameters?: Record<string, unknown>
  onUpdate: (params: Record<string, unknown>) => void
}

const DEFAULT_MAX_ROWS = 1000
const HARD_MAX_ROWS = 10000

export function SqlQueryParams({ parameters, onUpdate }: SqlQueryParamsProps) {
  const params = parameters || {}
  const sql = (params.sql as string) || ''
  const maxRows = (params.maxRows as number) ?? DEFAULT_MAX_ROWS

  const update = (field: string, value: unknown) => {
    onUpdate({ ...params, [field]: value })
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/30 p-2">
      <span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        SQL Query Config
      </span>

      <div>
        <FieldLabel className="text-[10px]">SQL</FieldLabel>
        <Textarea
          value={sql}
          onChange={(e) => update('sql', e.target.value)}
          className="mt-0.5 min-h-[120px] font-mono text-xs"
          placeholder={"SELECT id, name FROM contact WHERE email = '${$.input.email}'"}
          rows={8}
          spellCheck={false}
        />
        <p className="mt-1 text-[10px] leading-tight text-muted-foreground">
          Runs against the current tenant&apos;s schema. Use{' '}
          <code className="font-mono">{'${$.path}'}</code> to interpolate flow
          variables. SELECT/WITH/RETURNING returns{' '}
          <code className="font-mono">records</code>; other statements return{' '}
          <code className="font-mono">rowsAffected</code>.
        </p>
      </div>

      <div>
        <FieldLabel className="text-[10px]">Max rows</FieldLabel>
        <Input
          type="number"
          min={1}
          max={HARD_MAX_ROWS}
          value={maxRows}
          onChange={(e) => {
            const v = Number(e.target.value)
            if (Number.isFinite(v)) {
              update('maxRows', Math.max(1, Math.min(HARD_MAX_ROWS, v)))
            }
          }}
          className="mt-0.5 h-7 text-xs"
        />
        <p className="mt-1 text-[10px] leading-tight text-muted-foreground">
          Result-set statements only. Capped at {HARD_MAX_ROWS}.
        </p>
      </div>
    </div>
  )
}
