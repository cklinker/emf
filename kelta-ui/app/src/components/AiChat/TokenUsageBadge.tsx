import { cn } from '@/lib/utils'
import { Zap } from 'lucide-react'

interface TokenUsageBadgeProps {
  used: number
  limit: number
  className?: string
}

function formatTokenCount(count: number): string {
  if (count >= 1_000_000) return `${(count / 1_000_000).toFixed(1)}M`
  if (count >= 1_000) return `${(count / 1_000).toFixed(0)}K`
  return String(count)
}

export function TokenUsageBadge({ used, limit, className }: TokenUsageBadgeProps) {
  const percentage = limit > 0 ? (used / limit) * 100 : 0
  const isWarning = percentage >= 80
  const isExceeded = percentage >= 100

  return (
    <div
      className={cn(
        'flex items-center gap-1 rounded-full px-2 py-0.5 text-xs',
        isExceeded
          ? 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400'
          : isWarning
            ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400'
            : 'bg-muted text-muted-foreground',
        className
      )}
    >
      <Zap className="h-3 w-3" />
      <span>
        {formatTokenCount(used)} / {formatTokenCount(limit)}
      </span>
    </div>
  )
}
