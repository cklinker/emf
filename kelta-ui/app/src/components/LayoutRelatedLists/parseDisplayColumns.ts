/**
 * Parse the `displayColumns` value persisted with a related-list layout entry.
 *
 * The layout builder stores this as `JSON.stringify(string[])` (JSON-encoded
 * array of field names). Older or hand-edited rows may use a comma-separated
 * string. Anything unrecognized is treated as "no override" (empty array),
 * which causes the renderer to fall back to auto-discovered columns.
 */
export function parseDisplayColumns(raw: string | null | undefined): string[] {
  if (!raw) return []
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
