/**
 * CIDR validation helpers for the tenant IP allowlist editor.
 *
 * Validates IPv4 and IPv6 CIDR literals (e.g. `10.0.0.0/8`, `2001:db8::/32`)
 * client-side before saving. The gateway/worker validate again server-side; this
 * is purely for immediate form feedback.
 */

function isValidIpv4(addr: string): boolean {
  const parts = addr.split('.')
  if (parts.length !== 4) return false
  return parts.every((p) => {
    if (!/^\d{1,3}$/.test(p)) return false
    const n = Number(p)
    return n >= 0 && n <= 255 && String(n) === String(Number(p))
  })
}

// Covers full, compressed (`::`), and IPv4-mapped IPv6 forms.
const IPV6_PATTERN =
  /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9]))$/

function isValidIpv6(addr: string): boolean {
  return IPV6_PATTERN.test(addr)
}

/**
 * Returns true when `value` is a syntactically valid IPv4 or IPv6 CIDR range.
 */
export function isValidCidr(value: string): boolean {
  if (!value) return false
  const s = value.trim()
  const slash = s.indexOf('/')
  if (slash <= 0 || slash === s.length - 1) return false

  const addr = s.slice(0, slash)
  const prefixStr = s.slice(slash + 1)
  if (!/^\d+$/.test(prefixStr)) return false
  const prefix = Number(prefixStr)

  if (addr.includes(':')) {
    return prefix >= 0 && prefix <= 128 && isValidIpv6(addr)
  }
  return prefix >= 0 && prefix <= 32 && isValidIpv4(addr)
}
