/**
 * TagsCard
 *
 * Card holding a chip row of pills with optional tone variants. Matches the
 * design handoff at `design_handoff_kelta_detail_layout/`.
 */

import React from 'react'
import { Plus } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { cn } from '@/lib/utils'

export type TagTone = 'default' | 'brand' | 'success' | 'warning' | 'danger'

export interface TagItem {
  label: string
  tone?: TagTone
}

export interface TagsCardConfig {
  title: string
  tags: TagItem[]
  /** When provided, header renders a small `+` button */
  onAdd?: () => void
}

const TONE_CLASS: Record<TagTone, string> = {
  default: 'bg-muted text-foreground',
  brand: 'bg-blue-400/15 text-blue-300',
  success: 'bg-emerald-500/10 text-emerald-400',
  warning: 'bg-amber-500/10 text-amber-400',
  danger: 'bg-red-500/10 text-red-400',
}

export function TagsCard({
  config,
  className,
}: {
  config: TagsCardConfig
  className?: string
}): React.ReactElement | null {
  const { title, tags, onAdd } = config
  if (tags.length === 0 && !onAdd) return null

  return (
    <Card
      data-component="TagsCard"
      className={cn('overflow-hidden rounded-xl border border-border bg-card', className)}
    >
      <CardHeader className="flex flex-row items-center justify-between gap-2 px-5 py-4">
        <span className="text-sm font-semibold text-foreground">{title}</span>
        {onAdd && (
          <Button
            size="icon"
            variant="ghost"
            onClick={onAdd}
            aria-label={`Add ${title.toLowerCase()}`}
            className="h-6 w-6"
          >
            <Plus className="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
        )}
      </CardHeader>
      <CardContent className="border-t border-border px-5 py-4">
        {tags.length === 0 ? (
          <span className="text-[13px] text-muted-foreground">—</span>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {tags.map((tag, idx) => (
              <Badge
                key={`${tag.label}-${idx}`}
                className={cn('font-medium', TONE_CLASS[tag.tone || 'default'])}
                variant="secondary"
              >
                {tag.label}
              </Badge>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
