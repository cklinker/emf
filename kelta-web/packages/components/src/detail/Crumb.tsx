/**
 * Crumb — breadcrumb trail with optional N-of-M record stepper.
 *
 * Uses react-router-dom's Link when an internal route is provided; falls
 * back to a plain anchor otherwise.
 */

import React from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'

export interface CrumbItem {
  label: string
  to?: string
}

export interface CrumbPosition {
  index: number
  total: number
  onPrev?: () => void
  onNext?: () => void
}

export interface CrumbProps {
  trail: CrumbItem[]
  position?: CrumbPosition
}

export function Crumb({ trail, position }: CrumbProps): React.ReactElement {
  return (
    <nav
      data-component="Crumb"
      aria-label="Breadcrumb"
      className="flex items-center justify-between gap-4"
    >
      <ol className="flex min-w-0 items-center gap-1.5 text-[13px]">
        {trail.map((item, idx) => {
          const isLast = idx === trail.length - 1
          return (
            <li key={`${item.label}-${idx}`} className="flex items-center gap-1.5">
              {idx > 0 && (
                <ChevronRight
                  className="h-3.5 w-3.5 text-muted-foreground/60"
                  aria-hidden="true"
                />
              )}
              {item.to && !isLast ? (
                <Link
                  to={item.to}
                  className="truncate text-muted-foreground transition-colors hover:text-foreground"
                >
                  {item.label}
                </Link>
              ) : (
                <span
                  className="truncate font-medium text-foreground"
                  aria-current={isLast ? 'page' : undefined}
                >
                  {item.label}
                </span>
              )}
            </li>
          )
        })}
      </ol>

      {position && position.total > 0 && (
        <div className="flex flex-shrink-0 items-center gap-2">
          <button
            type="button"
            className="kelta-crumb-step"
            onClick={position.onPrev}
            disabled={!position.onPrev || position.index <= 0}
            aria-label="Previous record"
          >
            <ChevronLeft className="h-3.5 w-3.5" aria-hidden="true" />
          </button>
          <span className="kelta-crumb-pos">
            {position.index + 1} of {position.total}
          </span>
          <button
            type="button"
            className="kelta-crumb-step"
            onClick={position.onNext}
            disabled={!position.onNext || position.index >= position.total - 1}
            aria-label="Next record"
          >
            <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
          </button>
        </div>
      )}
    </nav>
  )
}
