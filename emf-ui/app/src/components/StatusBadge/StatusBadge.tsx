import { cn } from '@/lib/utils'

interface ColorConfig {
  color: string
  bg: string
}

interface StatusBadgeProps {
  value: string
  colorMap?: Record<string, ColorConfig>
  size?: 'sm' | 'md'
  className?: string
}

const DEFAULT_COLOR_MAP: Record<string, ColorConfig> = {
  active: { color: '#15803d', bg: '#dcfce7' },
  approved: { color: '#15803d', bg: '#dcfce7' },
  success: { color: '#15803d', bg: '#dcfce7' },
  completed: { color: '#15803d', bg: '#dcfce7' },
  enabled: { color: '#15803d', bg: '#dcfce7' },
  true: { color: '#15803d', bg: '#dcfce7' },
  yes: { color: '#15803d', bg: '#dcfce7' },
  healthy: { color: '#15803d', bg: '#dcfce7' },
  loaded: { color: '#15803d', bg: '#dcfce7' },
  pending: { color: '#854d0e', bg: '#fef9c3' },
  in_progress: { color: '#854d0e', bg: '#fef9c3' },
  running: { color: '#854d0e', bg: '#fef9c3' },
  warning: { color: '#854d0e', bg: '#fef9c3' },
  draft: { color: '#854d0e', bg: '#fef9c3' },
  submitted: { color: '#854d0e', bg: '#fef9c3' },
  rejected: { color: '#991b1b', bg: '#fee2e2' },
  failed: { color: '#991b1b', bg: '#fee2e2' },
  error: { color: '#991b1b', bg: '#fee2e2' },
  inactive: { color: '#991b1b', bg: '#fee2e2' },
  disabled: { color: '#991b1b', bg: '#fee2e2' },
  false: { color: '#991b1b', bg: '#fee2e2' },
  no: { color: '#991b1b', bg: '#fee2e2' },
  unhealthy: { color: '#991b1b', bg: '#fee2e2' },
  new: { color: '#1e40af', bg: '#dbeafe' },
  open: { color: '#1e40af', bg: '#dbeafe' },
  info: { color: '#1e40af', bg: '#dbeafe' },
  published: { color: '#1e40af', bg: '#dbeafe' },
}

const DEFAULT_FALLBACK: ColorConfig = { color: '#374151', bg: '#f3f4f6' }

function formatLabel(value: string): string {
  const withSpaces = value.replace(/_/g, ' ')
  return withSpaces.charAt(0).toUpperCase() + withSpaces.slice(1)
}

export default function StatusBadge({ value, colorMap, size = 'md', className }: StatusBadgeProps) {
  const normalizedValue = value.toLowerCase()
  const mergedMap = colorMap ? { ...DEFAULT_COLOR_MAP, ...colorMap } : DEFAULT_COLOR_MAP
  const colors = mergedMap[normalizedValue] ?? DEFAULT_FALLBACK

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full font-medium whitespace-nowrap leading-none',
        size === 'sm' ? 'text-[11px] px-2 py-0.5' : 'text-xs px-2.5 py-[3px]',
        className
      )}
      style={{ color: colors.color, backgroundColor: colors.bg }}
      data-testid="status-badge"
    >
      <span
        className={cn('rounded-full shrink-0', size === 'sm' ? 'size-1.5' : 'size-[7px]')}
        style={{ backgroundColor: colors.color }}
        aria-hidden="true"
      />
      {formatLabel(value)}
    </span>
  )
}
