/**
 * Parse the `displayColumns` value persisted with a related-list layout entry.
 *
 * The backend column is JSONB. Depending on serialization, the value can arrive
 * as a real `string[]`, as a JSON-stringified array, or as a comma-separated
 * legacy string. Anything unrecognized becomes `[]`, which causes the renderer
 * to fall back to auto-discovered columns.
 */
export function parseDisplayColumns(raw: unknown): string[] {
  if (raw == null) return []

  if (Array.isArray(raw)) {
    return raw.filter((s): s is string => typeof s === 'string')
  }

  if (typeof raw !== 'string') return []

  const trimmed = raw.trim()
  if (!trimmed) return []
  if (trimmed.startsWith('[')) {
    try {
      const parsed = JSON.parse(trimmed)
      return Array.isArray(parsed) ? parsed.filter((s): s is string => typeof s === 'string') : []
    } catch {
      return []
    }
  }
  return trimmed
    .split(',')
    .map((c) => c.trim())
    .filter(Boolean)
}
