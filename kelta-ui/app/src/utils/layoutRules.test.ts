import { describe, it, expect } from 'vitest'
import { dtoToLayoutRule, dtosToLayoutRules } from './layoutRules'
import type { LayoutRuleDto } from '../hooks/usePageLayout'

const baseDto: LayoutRuleDto = {
  id: 'r1',
  layoutId: 'l1',
  name: 'Rule',
  description: null,
  kind: 'COMPUTE',
  active: true,
  whenEvents: ['onChange'],
  targetField: null,
  dependsOn: null,
  body: {},
  sortOrder: 10,
}

describe('dtoToLayoutRule', () => {
  it('maps a COMPUTE dto to a compute rule', () => {
    const rule = dtoToLayoutRule(
      { ...baseDto, kind: 'COMPUTE', targetField: 'total', body: { formula: 'a + b' } },
      't1'
    )
    expect(rule).toMatchObject({ kind: 'compute', target: 'total', formula: 'a + b', tenantId: 't1' })
  })

  it('maps a SCRIPT dto to a script rule with expression + optional message', () => {
    const rule = dtoToLayoutRule(
      {
        ...baseDto,
        kind: 'SCRIPT',
        targetField: 'discount',
        whenEvents: ['onChange', 'onBeforeSave'],
        body: { expression: 'IF(discount > 0.5, "Too high", "")', message: 'Invalid' },
      },
      't1'
    )
    expect(rule).toEqual(
      expect.objectContaining({
        kind: 'script',
        target: 'discount',
        expression: 'IF(discount > 0.5, "Too high", "")',
        message: 'Invalid',
        when: ['onChange', 'onBeforeSave'],
        tenantId: 't1',
      })
    )
  })

  it('maps a SCRIPT dto with no target/message to a form-level script rule', () => {
    const rule = dtoToLayoutRule(
      { ...baseDto, kind: 'SCRIPT', targetField: null, body: { expression: 'x > y' } },
      't1'
    )
    expect(rule).toMatchObject({ kind: 'script', expression: 'x > y' })
    expect((rule as { target?: string }).target).toBeUndefined()
    expect((rule as { message?: string }).message).toBeUndefined()
  })

  it('parses a JSON-string body (serializer variance)', () => {
    const rule = dtoToLayoutRule(
      { ...baseDto, kind: 'SCRIPT', body: JSON.stringify({ expression: 'a' }) },
      't1'
    )
    expect(rule).toMatchObject({ kind: 'script', expression: 'a' })
  })

  it('dtosToLayoutRules filters unconvertible rules', () => {
    const rules = dtosToLayoutRules(
      [
        { ...baseDto, kind: 'SCRIPT', body: { expression: 'a' } },
        { ...baseDto, id: 'r2', kind: 'TRANSFORM', body: { transform: { type: 'nope' } } },
      ],
      't1'
    )
    expect(rules).toHaveLength(1)
    expect(rules[0]).toMatchObject({ kind: 'script' })
  })
})
