/**
 * Format a date as a relative time string.
 *
 * @param date - ISO string or Date object
 * @returns Human-readable relative time (e.g., "Just now", "5m ago", "2h ago", "Yesterday")
 */
export function formatRelativeTime(date: string | Date): string {
  const now = Date.now()
  const then = typeof date === 'string' ? new Date(date).getTime() : date.getTime()
  const diffMs = now - then

  if (diffMs < 0) return 'Just now'

  const diffMinutes = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMinutes < 1) return 'Just now'
  if (diffMinutes < 60) return `${diffMinutes}m ago`
  if (diffHours < 24) return `${diffHours}h ago`
  if (diffDays === 1) return 'Yesterday'
  if (diffDays < 7) return `${diffDays}d ago`

  const d = typeof date === 'string' ? new Date(date) : date
  return d.toLocaleDateString()
}
