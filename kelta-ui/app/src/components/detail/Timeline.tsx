/**
 * Timeline
 *
 * Generic vertical event feed: each row has a 28×28 round icon dot, a 1px
 * vertical connector to the next row, a 13/500 title with right-aligned
 * tabular timestamp, and a 13px muted body. Matches the design handoff at
 * `design_handoff_kelta_detail_layout/`.
 *
 * For the activity-feed specific case (CREATED/UPDATED/APPROVAL_* events),
 * see [ActivityTimeline](../ActivityTimeline/ActivityTimeline.tsx).
 */

import React from 'react'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { cn } from '@/lib/utils'

export type TimelineTone = 'default' | 'brand' | 'success' | 'warning' | 'danger'

export interface TimelineEvent {
  /** Pre-formatted timestamp text (e.g. "2h ago" or "Mar 28") */
  at: string
  icon?: React.ReactNode
  tone?: TimelineTone
  title: string
  body?: string
}

export interface TimelineConfig {
  title: string
  events: TimelineEvent[]
}

const TONE_CLASS: Record<TimelineTone, string> = {
  default: 'bg-muted text-foreground',
  brand: 'bg-blue-400/15 text-blue-300',
  success: 'bg-emerald-500/10 text-emerald-400',
  warning: 'bg-amber-500/10 text-amber-400',
  danger: 'bg-red-500/10 text-red-400',
}

export function Timeline({
  config,
  className,
}: {
  config: TimelineConfig
  className?: string
}): React.ReactElement | null {
  const { title, events } = config
  if (events.length === 0) return null

  return (
    <Card
      data-component="Timeline"
      className={cn('overflow-hidden rounded-xl border border-border bg-card', className)}
    >
      <CardHeader className="px-5 py-4">
        <span className="text-sm font-semibold text-foreground">{title}</span>
      </CardHeader>
      <CardContent className="border-t border-border px-5 py-4">
        <ol className="relative">
          {events.map((event, idx) => {
            const isLast = idx === events.length - 1
            return (
              <li
                key={`${event.title}-${idx}`}
                className="relative grid grid-cols-[28px_1fr] gap-3 pb-4 last:pb-0"
              >
                <span
                  className={cn(
                    'relative z-10 inline-flex h-7 w-7 items-center justify-center rounded-full',
                    TONE_CLASS[event.tone || 'default']
                  )}
                  aria-hidden="true"
                >
                  {event.icon}
                </span>
                {!isLast && (
                  <span
                    className="absolute left-[13.5px] top-7 bottom-0 w-px bg-border"
                    aria-hidden="true"
                  />
                )}
                <div className="min-w-0 space-y-0.5">
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="text-[13px] font-medium text-foreground">{event.title}</span>
                    <span className="text-[11px] text-muted-foreground tabular-nums">
                      {event.at}
                    </span>
                  </div>
                  {event.body && (
                    <p className="text-[13px] text-muted-foreground">{event.body}</p>
                  )}
                </div>
              </li>
            )
          })}
        </ol>
      </CardContent>
    </Card>
  )
}
