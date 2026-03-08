/**
 * Normalise an unknown value coming from the record into a string array.
 *
 * The value may arrive as:
 * - A JavaScript array:   ["s", "m", "l"]
 * - A JSON string:        '["s","m","l"]'
 * - A PostgreSQL literal:  "{s,m,l}"
 * - A comma-separated string: "s,m,l"
 * - null / undefined / empty string
 */
export function normalizeMultiPicklistValue(raw: unknown): string[] {
  if (raw == null) return []

  if (Array.isArray(raw)) {
    return raw.filter((v): v is string => typeof v === 'string' && v !== '')
  }

  if (typeof raw === 'string') {
    const trimmed = raw.trim()
    if (trimmed === '' || trimmed === '[]' || trimmed === '{}') return []

    // JSON array string: '["s","m","l"]'
    if (trimmed.startsWith('[')) {
      try {
        const parsed = JSON.parse(trimmed) as unknown
        if (Array.isArray(parsed)) {
          return parsed.filter((v): v is string => typeof v === 'string' && v !== '')
        }
      } catch {
        // fall through to comma-separated
      }
    }

    // PostgreSQL array literal: "{s,m,l}"
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
      return trimmed
        .slice(1, -1)
        .split(',')
        .map((s) => s.trim().replace(/^"|"$/g, ''))
        .filter((s) => s !== '')
    }

    // Comma-separated string: "s,m,l"
    if (trimmed.includes(',')) {
      return trimmed
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s !== '')
    }

    // Single value
    return [trimmed]
  }

  return []
}
