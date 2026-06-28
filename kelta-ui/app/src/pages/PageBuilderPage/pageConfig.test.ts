import { describe, it, expect } from 'vitest'
import { readComponents, readConfig, mergeConfig } from './pageConfig'
import type { PageComponent } from './PageBuilderPage'

const comp = (id: string): PageComponent => ({
  id,
  type: 'heading',
  props: {},
  position: { row: 0, column: 0, width: 12, height: 1 },
})

describe('pageConfig', () => {
  describe('readComponents', () => {
    it('prefers config.components', () => {
      const page = { config: { components: [comp('a')] }, components: [comp('legacy')] }
      expect(readComponents(page).map((c) => c.id)).toEqual(['a'])
    })

    it('falls back to legacy top-level components', () => {
      expect(readComponents({ components: [comp('legacy')] }).map((c) => c.id)).toEqual(['legacy'])
    })

    it('returns [] for null/empty', () => {
      expect(readComponents(null)).toEqual([])
      expect(readComponents({})).toEqual([])
    })
  })

  describe('readConfig', () => {
    it('returns the config object or {}', () => {
      expect(
        readConfig({ config: { layout: { type: 'single' } } } as unknown as Parameters<
          typeof readConfig
        >[0])
      ).toEqual({
        layout: { type: 'single' },
      })
      expect(readConfig({})).toEqual({})
      expect(readConfig(null)).toEqual({})
    })
  })

  describe('mergeConfig', () => {
    it('overlays components while preserving layout', () => {
      const result = mergeConfig(
        { layout: { type: 'grid' }, components: [comp('old')] },
        {
          components: [comp('new')],
        }
      )
      expect(result.layout).toEqual({ type: 'grid' })
      expect(result.components?.map((c) => c.id)).toEqual(['new'])
    })

    it('overlays layout while preserving components', () => {
      const result = mergeConfig(
        { layout: { type: 'single' }, components: [comp('keep')] },
        {
          layout: { type: 'sidebar' },
        }
      )
      expect(result.layout).toEqual({ type: 'sidebar' })
      expect(result.components?.map((c) => c.id)).toEqual(['keep'])
    })

    it('overlays schemaVersion:2 while preserving the 2a-covered keys (components/layout)', () => {
      const result = mergeConfig(
        { layout: { type: 'grid' }, components: [comp('keep')] },
        { schemaVersion: 2 }
      )
      expect(result.schemaVersion).toBe(2)
      expect(result.layout).toEqual({ type: 'grid' })
      expect(result.components?.map((c) => c.id)).toEqual(['keep'])
    })

    it('overlays variables/dataSources/access when passed', () => {
      const result = mergeConfig(
        {},
        {
          variables: [{ name: 'count', type: 'number', default: 0 }],
          dataSources: [{ name: 'orders', collection: 'orders', mode: 'list' }],
          access: { requiredPermission: 'orders:view' },
        }
      )
      expect(result.variables).toEqual([{ name: 'count', type: 'number', default: 0 }])
      expect(result.dataSources).toEqual([{ name: 'orders', collection: 'orders', mode: 'list' }])
      expect(result.access).toEqual({ requiredPermission: 'orders:view' })
    })

    it('leaves an omitted key untouched (never wipes the existing value)', () => {
      const result = mergeConfig(
        { variables: [{ name: 'keep', type: 'string' }], schemaVersion: 2 },
        { components: [comp('x')] }
      )
      // variables/schemaVersion omitted from changes ⇒ preserved.
      expect(result.variables).toEqual([{ name: 'keep', type: 'string' }])
      expect(result.schemaVersion).toBe(2)
    })

    it('does NOT invent schemaVersion when only components are passed (additive helper)', () => {
      const result = mergeConfig({ components: [comp('a')] }, { components: [comp('b')] })
      expect(result.schemaVersion).toBeUndefined()
    })
  })
})
