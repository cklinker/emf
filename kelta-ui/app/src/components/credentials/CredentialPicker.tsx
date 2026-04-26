import React, { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import type { CredentialRecord } from '@kelta/sdk'

import { useApi } from '../../context/ApiContext'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { FieldLabel, StatusBadge } from '@/components/kelta'

export interface CredentialPickerProps {
  /** Currently selected credential id (or empty string for none). */
  value: string
  onChange: (credentialId: string, credential: CredentialRecord | null) => void
  /**
   * Optional list of credential type keys to filter by — e.g.
   * `['oauth2_client_credentials','oauth2_authorization_code','bearer_token']`
   * for a step that needs an OAuth-style auth.
   */
  compatibleTypes?: string[]
  /** Field label rendered above the select. */
  label?: string
  placeholder?: string
  /** When true, "None" is offered as a valid choice. */
  allowNone?: boolean
  className?: string
}

/**
 * Reusable picker for tenant-managed credentials. Renders the credential's
 * display name, type badge, and connection status. Filters out inactive
 * credentials by default. Used in flow step properties panels (PR 4) and any
 * other surface that needs to attach a credential to a configuration.
 */
export function CredentialPicker({
  value,
  onChange,
  compatibleTypes,
  label = 'Credential',
  placeholder = 'Select a credential…',
  allowNone = true,
  className,
}: CredentialPickerProps): React.ReactElement {
  const { keltaClient } = useApi()
  const { data, isLoading, error } = useQuery({
    queryKey: ['credentials'],
    queryFn: () => keltaClient.admin.credentials.list(),
  })

  const candidates = useMemo(() => {
    const list = ((data ?? []) as unknown as CredentialRecord[]).filter((c) => c.active !== false)
    if (!compatibleTypes || compatibleTypes.length === 0) return list
    return list.filter((c) => compatibleTypes.includes(c.type))
  }, [data, compatibleTypes])

  return (
    <div className={className ?? 'space-y-1'}>
      <FieldLabel>{label}</FieldLabel>
      <Select
        value={value || (allowNone ? '__none' : '')}
        onValueChange={(v) => {
          if (v === '__none') {
            onChange('', null)
            return
          }
          const credential = candidates.find((c) => c.id === v) ?? null
          onChange(v, credential)
        }}
        disabled={isLoading || Boolean(error)}
      >
        <SelectTrigger>
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          {allowNone && <SelectItem value="__none">None</SelectItem>}
          {candidates.map((c) => (
            <SelectItem key={c.id} value={c.id}>
              <div className="flex items-center gap-2">
                <span>{c.displayName || c.name}</span>
                <span className="text-xs text-muted-foreground font-mono">{c.type}</span>
                {c.lastTestStatus === 'OK' && <StatusBadge variant="active" label="OK" />}
                {c.lastTestStatus === 'FAILED' && <StatusBadge variant="failed" label="Fail" />}
              </div>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {error && <p className="text-xs text-red-600">Failed to load credentials</p>}
      {!isLoading && candidates.length === 0 && !error && (
        <p className="text-xs text-muted-foreground">No matching credentials yet.</p>
      )}
    </div>
  )
}
