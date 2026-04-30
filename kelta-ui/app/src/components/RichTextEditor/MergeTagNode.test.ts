import { describe, it, expect } from 'vitest'
import { tokenizeMergeTags } from './MergeTagNode'

describe('tokenizeMergeTags', () => {
  it('wraps a single merge tag in a marker span', () => {
    const html = tokenizeMergeTags('Hello {{firstName}}')
    expect(html).toContain('data-merge-tag')
    expect(html).toContain('data-expression="firstName"')
    expect(html).toContain('{{firstName}}')
  })

  it('handles multiple merge tags in the same paragraph', () => {
    const html = tokenizeMergeTags('<p>Hi {{firstName}}, your order {{order_id.id}} is ready.</p>')
    const matches = html.match(/data-merge-tag/g) ?? []
    expect(matches.length).toBe(2)
    expect(html).toContain('data-expression="firstName"')
    expect(html).toContain('data-expression="order_id.id"')
  })

  it('preserves text outside the tags untouched', () => {
    const html = tokenizeMergeTags('<p>Status: {{status}}</p>')
    expect(html).toContain('Status: ')
    expect(html).toContain('</p>')
  })

  it('skips text already inside a merge-tag span', () => {
    const input = '<p><span data-merge-tag data-expression="firstName">{{firstName}}</span></p>'
    const html = tokenizeMergeTags(input)
    // Only one wrapping span should exist — the function must not double-wrap.
    const matches = html.match(/data-merge-tag/g) ?? []
    expect(matches.length).toBe(1)
  })

  it('trims whitespace inside the merge tag braces', () => {
    const html = tokenizeMergeTags('Hello {{  firstName  }}!')
    expect(html).toContain('data-expression="firstName"')
    expect(html).toContain('{{firstName}}')
  })

  it('returns plain HTML when there are no merge tags', () => {
    const html = tokenizeMergeTags('<p>Plain content</p>')
    expect(html).toBe('<p>Plain content</p>')
  })
})
