/**
 * MetadataCard
 *
 * Compact list of label / value rows separated by 1px dashed dividers.
 * Optionally renders values in monospace (useful for IDs). Matches the
 * design handoff at `design_handoff_kelta_detail_layout/`.
 */

import React from 'react'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { cn } from '@/lib/utils'

export interface MetadataRow {
  label: string
  /** Pre-formatted display value */
  value: React.ReactNode
  /** Render the value in monospace */
  mono?: boolean
}

export interface MetadataCardConfig {
  title: string
  rows: MetadataRow[]
}

export function MetadataCard({
  config,
  className,
}: {
  config: MetadataCardConfig
  className?: string
}): React.ReactElement | null {
  const { title, rows } = config
  if (rows.length === 0) return null

  return (
    <Card
      data-component="MetadataCard"
      className={cn('overflow-hidden rounded-xl border border-border bg-card', className)}
    >
      <CardHeader className="px-5 py-4">
        <span className="text-sm font-semibold text-foreground">{title}</span>
      </CardHeader>
      <CardContent className="border-t border-border px-5 py-2">
        <dl className="divide-y divide-dashed divide-border">
          {rows.map((row, idx) => (
            <div
              key={`${row.label}-${idx}`}
              className="flex items-baseline justify-between gap-3 py-2.5"
            >
              <dt className="text-[12px] text-muted-foreground">{row.label}</dt>
              <dd
                className={cn(
                  'truncate text-[13px] text-foreground',
                  row.mono && 'font-mono text-[12px]'
                )}
              >
                {row.value}
              </dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  )
}
