/**
 * StatStrip
 *
 * Horizontal strip of equal-width stat tiles separated by 1px borders (no
 * gaps), wrapped in a 12px-radius card. Each tile shows a label + optional
 * icon, a 24/600 value with tabular numerals, and an optional trend chip +
 * subtitle. Matches the design handoff at
 * `design_handoff_kelta_detail_layout/`.
 */

import React from 'react'
import { ArrowDownRight, ArrowUpRight } from 'lucide-react'
import { cn } from '@/lib/utils'

export type StatTileKind = 'currency' | 'number' | 'text'

export interface StatTileTrend {
  /** `up` renders green, `down` renders red */
  dir: 'up' | 'down'
  /** e.g. `+12.4% YoY` */
  label: string
}

export interface StatTileConfig {
  label: string
  /** Pre-formatted value to display (already locale-formatted) */
  value: string
  kind?: StatTileKind
  icon?: React.ReactNode
  trend?: StatTileTrend
  /** Small muted line under the value/trend */
  sub?: string
}

export interface StatStripProps {
  tiles: StatTileConfig[]
  className?: string
}

export function StatStrip({ tiles, className }: StatStripProps): React.ReactElement | null {
  if (tiles.length === 0) return null

  return (
    <div
      data-component="StatStrip"
      className={cn(
        'overflow-hidden rounded-xl border border-border bg-card',
        'grid grid-cols-2 divide-x divide-border md:grid-cols-3 lg:grid-cols-5',
        className
      )}
    >
      {tiles.map((tile, idx) => (
        <StatTile key={`${tile.label}-${idx}`} tile={tile} />
      ))}
    </div>
  )
}

function StatTile({ tile }: { tile: StatTileConfig }): React.ReactElement {
  return (
    <div className="flex flex-col gap-2 px-5 py-4">
      <div className="flex items-center justify-between gap-2">
        <span className="kelta-field-label">{tile.label}</span>
        {tile.icon && (
          <span className="text-muted-foreground" aria-hidden="true">
            {tile.icon}
          </span>
        )}
      </div>
      <div className="text-[24px] font-semibold tracking-[-0.02em] tabular-nums text-foreground">
        {tile.value}
      </div>
      {(tile.trend || tile.sub) && (
        <div className="flex flex-wrap items-center gap-2">
          {tile.trend && (
            <span
              className={cn(
                'inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 text-[11px] font-medium',
                tile.trend.dir === 'up'
                  ? 'bg-emerald-500/10 text-emerald-400'
                  : 'bg-red-500/10 text-red-400'
              )}
            >
              {tile.trend.dir === 'up' ? (
                <ArrowUpRight className="h-3 w-3" aria-hidden="true" />
              ) : (
                <ArrowDownRight className="h-3 w-3" aria-hidden="true" />
              )}
              {tile.trend.label}
            </span>
          )}
          {tile.sub && <span className="text-[12px] text-muted-foreground">{tile.sub}</span>}
        </div>
      )}
    </div>
  )
}
