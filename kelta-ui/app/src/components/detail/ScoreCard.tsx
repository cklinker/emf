/**
 * ScoreCard
 *
 * 0–100 score card with a 6px gradient progress bar, optional delta chip,
 * and a 2×2 grid of sub-metric rows. Matches the design handoff at
 * `design_handoff_kelta_detail_layout/`.
 */

import React from 'react'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { cn } from '@/lib/utils'

export type ScoreTone = 'success' | 'warning' | 'danger'

export interface ScoreMetric {
  label: string
  value: string
  /** Tints the value foreground in success-fg when true */
  ok?: boolean
}

export interface ScoreCardConfig {
  title: string
  /** 0..100 */
  score: number
  statusLabel?: string
  tone?: ScoreTone
  /** e.g. `+4 this month` */
  delta?: string
  metrics?: ScoreMetric[]
}

const TONE_PILL: Record<ScoreTone, string> = {
  success: 'bg-emerald-500/10 text-emerald-400',
  warning: 'bg-amber-500/10 text-amber-400',
  danger: 'bg-red-500/10 text-red-400',
}

export function ScoreCard({
  config,
  className,
}: {
  config: ScoreCardConfig
  className?: string
}): React.ReactElement {
  const { title, score, statusLabel, tone = 'success', delta, metrics = [] } = config
  const clamped = Math.max(0, Math.min(100, score))

  return (
    <Card
      data-component="ScoreCard"
      className={cn('overflow-hidden rounded-xl border border-border bg-card', className)}
    >
      <CardHeader className="flex flex-row items-center justify-between gap-2 px-5 py-4">
        <span className="text-sm font-semibold text-foreground">{title}</span>
        {statusLabel && (
          <span
            className={cn(
              'inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium',
              TONE_PILL[tone]
            )}
          >
            {statusLabel}
          </span>
        )}
      </CardHeader>
      <CardContent className="space-y-3 border-t border-border px-5 py-4">
        <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
          <div
            className="h-full rounded-full bg-gradient-to-r from-emerald-500 to-emerald-300"
            style={{ width: `${clamped}%` }}
            role="progressbar"
            aria-valuenow={clamped}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`${title} score ${clamped} of 100`}
          />
        </div>
        <div className="flex items-baseline justify-between gap-2">
          <span className="text-[13px] text-foreground tabular-nums">
            Score <span className="font-semibold">{clamped}</span> / 100
          </span>
          {delta && <span className="text-[11px] text-emerald-400 tabular-nums">{delta}</span>}
        </div>
        {metrics.length > 0 && (
          <div className="grid grid-cols-2 gap-x-4 gap-y-3 pt-2">
            {metrics.map((m, idx) => (
              <div key={`${m.label}-${idx}`} className="space-y-0.5">
                <div className="kelta-field-label">{m.label}</div>
                <div
                  className={cn(
                    'text-[13px] font-medium tabular-nums',
                    m.ok ? 'text-emerald-400' : 'text-foreground'
                  )}
                >
                  {m.value}
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
