/**
 * Timeline — generic vertical event feed: 28×28 dot + 1px connector + body.
 * Distinct from kelta-ui/app's `ActivityTimeline`, which is bound to a
 * specific event-type schema.
 */

import React from 'react'
import { cn } from './_utils'

export type TimelineTone = 'default' | 'brand' | 'success' | 'warning' | 'danger'

export interface TimelineEvent {
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
    <div
      data-component="Timeline"
      className={cn('overflow-hidden rounded-xl border border-border bg-card', className)}
    >
      <div className="px-5 py-4">
        <span className="text-sm font-semibold text-foreground">{title}</span>
      </div>
      <div className="border-t border-border px-5 py-4">
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
      </div>
    </div>
  )
}
