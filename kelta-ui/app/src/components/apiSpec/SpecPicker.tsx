import React from 'react'
import { useQuery } from '@tanstack/react-query'
import type { ApiSpecSummary } from '@kelta/sdk'

import { useApi } from '../../context/ApiContext'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { FieldLabel } from '@/components/kelta'

export interface SpecPickerProps {
  value: string
  onChange: (specId: string, spec: ApiSpecSummary | null) => void
  label?: string
  placeholder?: string
  className?: string
}

/**
 * Picks an imported OpenAPI spec from the library. Used by PR 4's CALL_API
 * step properties panel and any other surface that needs to attach a request
 * to a spec.
 */
export function SpecPicker({
  value,
  onChange,
  label = 'API Spec',
  placeholder = 'Select an imported spec…',
  className,
}: SpecPickerProps): React.ReactElement {
  const { keltaClient } = useApi()
  const { data, isLoading, error } = useQuery({
    queryKey: ['api-specs'],
    queryFn: () => keltaClient.admin.apiSpecs.list(),
  })

  const specs = (data ?? []) as ApiSpecSummary[]

  return (
    <div className={className ?? 'space-y-1'}>
      <FieldLabel>{label}</FieldLabel>
      <Select
        value={value || ''}
        onValueChange={(v) => {
          const spec = specs.find((s) => s.id === v) ?? null
          onChange(v, spec)
        }}
        disabled={isLoading || Boolean(error)}
      >
        <SelectTrigger>
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          {specs.map((s) => (
            <SelectItem key={s.id} value={s.id}>
              <div className="flex flex-col">
                <span>{s.apiTitle || s.name}</span>
                <span className="text-xs text-muted-foreground font-mono">
                  {s.name} · v{s.apiVersion ?? '—'} · rev {s.revision}
                </span>
              </div>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {error && <p className="text-xs text-red-600">Failed to load specs</p>}
      {!isLoading && specs.length === 0 && !error && (
        <p className="text-xs text-muted-foreground">No specs imported yet.</p>
      )}
    </div>
  )
}
